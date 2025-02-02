// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import java.util.UUID

import scala.concurrent.duration._
import scala.math._

import cats.data.NonEmptyList
import cats.effect.Async
import cats.effect.Sync
import cats.syntax.all._
import fs2.Pipe
import fs2.Stream
import fs2.concurrent.Topic
import giapi.client.GiapiStatusDb
import giapi.client.StatusValue
import org.typelevel.log4cats.Logger
import lucuma.core.enums.GiapiStatus
import lucuma.core.enums.Site
import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.`User-Agent`
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.server.middleware.GZip
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Binary
import org.http4s.websocket.WebSocketFrame.Close
import org.http4s.websocket.WebSocketFrame.Ping
import org.http4s.websocket.WebSocketFrame.Pong
import scodec.bits.ByteVector
import observe.model.ClientId
import observe.model._
import observe.model.boopickle._
import observe.model.config._
import observe.model.events._
import observe.server.ObserveEngine
import observe.server.tcs.GuideConfigDb
import observe.web.server.OcsBuildInfo
import observe.web.server.http4s.encoder._
import observe.web.server.security.AuthenticationService
import observe.web.server.security.AuthenticationService.AuthResult
import observe.web.server.security.Http4sAuthentication
import observe.web.server.security.TokenRefresher

/**
 * Rest Endpoints under the /api route
 */
class ObserveUIApiRoutes[F[_]: Async](
  site:             Site,
  mode:             Mode,
  auth:             AuthenticationService[F],
  guideConfigS:     GuideConfigDb[F],
  giapiDB:          GiapiStatusDb[F],
  clientsDb:        ClientsSetDb[F],
  engineOutput:     Topic[F, ObserveEvent],
  webSocketBuilder: WebSocketBuilder2[F]
)(implicit
  L:                Logger[F]
) extends BooEncoders
    with ModelLenses
    with Http4sDsl[F] {

  private val unauthorized =
    Unauthorized(`WWW-Authenticate`(NonEmptyList.of(Challenge("jwt", "observe"))))

  // Handles authentication
  private val httpAuthentication = new Http4sAuthentication(auth)

  // Stream of updates to the guide config db
  val guideConfigEvents: Stream[F, Binary] =
    guideConfigS.discrete
      .map(g => GuideConfigUpdate(g.tcsGuide))
      .map(toFrame)

  // Stream of updates to gpi align an calib process
  // This is fairly custom for one use case and we'd rather have a more
  // generalized mechanism.
  // Also we may want to send this through another websocket but it would
  // complicate the client
  val giapiDBEvents: Stream[F, Binary] =
    giapiDB.discrete
      .map(_.get(GiapiStatus.GpiAlignAndCalibState.statusItem).flatMap(StatusValue.intValue))
      .collect { case Some(x) =>
        AlignAndCalibEvent(x)
      }
      .map(toFrame)

  val pingInterval: FiniteDuration = 10.second

  /**
   * Creates a process that sends a ping every second to keep the connection alive
   */
  private def pingStream: Stream[F, Ping] =
    Stream.fixedRate[F](pingInterval).flatMap(_ => Stream.emit(Ping()))

  val publicService: HttpRoutes[F] = GZip {
    HttpRoutes.of {

      case req @ POST -> Root / "observe" / "login" =>
        req.decode[UserLoginRequest] { (u: UserLoginRequest) =>
          // Try to authenticate
          auth.authenticateUser(u.username, u.password).flatMap {
            case Right(user) =>
              // Log who logged in
              // Note that the call to read a remoteAddr may do a DNS lookup
              req.remoteHost.flatMap { x =>
                L.info(s"${user.displayName} logged in from ${x.getOrElse("Unknown")}")
              } *>
                // if successful set a cookie
                httpAuthentication.loginCookie(user) >>= { cookie =>
                Ok(user).map(_.addCookie(cookie))
              }
            case Left(_)     =>
              unauthorized
          }
        }

      case POST -> Root / "observe" / "logout" =>
        // Clean the auth cookie
        val cookie = ResponseCookie(auth.config.cookieName,
                                    "",
                                    path = "/".some,
                                    secure = auth.config.useSSL,
                                    maxAge = Some(-1),
                                    httpOnly = true
        )
        Ok("").map(_.removeCookie(cookie))

    }
  }

  val protectedServices: AuthedRoutes[AuthResult, F] =
    AuthedRoutes.of {
      // Route used for testing only
      case GET -> Root / "log" / IntVar(count) as _ if mode === Mode.Development =>
        (L.info("info") *>
          L.warn("warn") *>
          L.error("error")).replicateA(min(1000, max(0, count))) *> Ok("")

      case auth @ POST -> Root / "observe" / "site" as user =>
        val userName = user.fold(_ => "Anonymous", _.displayName)
        // Login start
        auth.req.remoteHost.flatMap { x =>
          L.info(s"$userName connected from ${x.getOrElse("Unknown")}")
        } *>
          Ok(s"$site")

      case ws @ GET -> Root / "observe" / "events" as user =>
        // If the user didn't login, anonymize
        val anonymizeF: ObserveEvent => ObserveEvent = user.fold(_ => anonymize, _ => identity)

        def initialEvent(clientId: ClientId): Stream[F, WebSocketFrame] =
          Stream.emit(toFrame(ConnectionOpenEvent(user.toOption, clientId, OcsBuildInfo.version)))

        def engineEvents(clientId: ClientId): Stream[F, WebSocketFrame] =
          engineOutput
            .subscribe(100)
            .map(anonymizeF)
            .filter(filterOutNull)
            .filter(filterOutOnClientId(clientId))
            .map(toFrame)
        val clientSocket                                                = (ws.req.remoteAddr, ws.req.remotePort).mapN((a, p) => s"$a:$p").orEmpty
        val userAgent                                                   = ws.req.headers.get[`User-Agent`]

        // We don't care about messages sent over ws by clients but we want to monitor
        // control frames and track that pings arrive from clients
        def clientEventsSink(clientId: ClientId): Pipe[F, WebSocketFrame, Unit] =
          _.flatTap {
            case Close(_) =>
              Stream.eval(
                clientsDb.removeClient(clientId) *> L.debug(s"Closed client $clientSocket")
              )
            case Pong(_)  => Stream.eval(L.trace(s"Pong from $clientSocket"))
            case _        => Stream.empty
          }.filter {
            case Pong(_) => true
            case _       => false
          }.void
            .through(
              ObserveEngine.failIfNoEmitsWithin(5 * pingInterval, s"Lost ping on $clientSocket")
            )

        // Create a client specific websocket
        for {
          clientId <- Sync[F].delay(ClientId(UUID.randomUUID()))
          _        <- clientsDb.newClient(clientId, clientSocket, userAgent)
          _        <- L.info(s"New client $clientSocket => ${clientId.self}")
          initial   = initialEvent(clientId)
          streams   = Stream(pingStream,
                             guideConfigEvents,
                             giapiDBEvents,
                             engineEvents(clientId)
                      ).parJoinUnbounded
                        .onFinalize[F](clientsDb.removeClient(clientId))
          ws       <- webSocketBuilder
                        .withFilterPingPongs(false)
                        .build(initial ++ streams, clientEventsSink(clientId))
        } yield ws

    }

  def service: HttpRoutes[F] =
    publicService <+> TokenRefresher(GZip(httpAuthentication.optAuth(protectedServices)),
                                     httpAuthentication
    )

  // Event to WebSocket frame
  private def toFrame(e: ObserveEvent) =
    Binary(ByteVector(trimmedArray(e)))

  // Stream observe events to clients and a ping
  private def anonymize(e: ObserveEvent) =
    // Hide the name and target name for anonymous users
    telescopeTargetNameT
      .replace("*****")
      .andThen(observeTargetNameT.replace("*****"))
      .andThen(sequenceNameT.replace(""))(e)

  // Filter out NullEvents from the engine
  private def filterOutNull =
    (e: ObserveEvent) =>
      e match {
        case NullEvent => false
        case _         => true
      }

  // Messages with a clientId are only sent to the matching cliend
  private def filterOutOnClientId(clientId: ClientId) =
    (e: ObserveEvent) =>
      e match {
        case e: ForClient if e.clientId =!= clientId => false
        case _                                       => true
      }

}
