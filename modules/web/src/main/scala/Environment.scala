// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem
package web

import cats.implicits._
import cats.effect._
import doobie.Transactor
import gem.{ Service => GemService }
import gem.dao.DatabaseConfiguration
import io.circe._
import io.circe.parser.decode
import io.circe.syntax._
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import pdi.jwt.Jwt
import pdi.jwt.algorithms.JwtHmacAlgorithm

/**
 * The runtime environment for the web server, providing access to required services. You can think
 * of this as a "realized" configuration. The environment doesn't know anything about http4s, and it
 * should only appear in the outermost layer of the application. In particular the main application
 * endpoints don't know about the environment. This has the potential to turn into a God object so
 * we need to keep an eye on it.
 */
trait Environment[F[_]] {

  def config: WebConfiguration

  /** A logger that goes to the database, cc:'d to JDK logging. */
  def log: Log[F]

  /** A source of database connections. */
  def transactor: Transactor[F]

  /**
   * Encode a JWT claim, which is an arbitrary JSON object with some well-known fields. The result
   * here is the three-part encoding described at https://jwt.io/
   */
  def encodeJwt[A: Encoder](claim: A): String

  /**
   * Decode and validate a JWT claim, if possible. This must be in F because validation depends
   * on clock time.
   */
  def decodeJwt[A: Decoder](value: String): F[Either[String, A]]

  /** Attempt to log in, yielding a Service value tied to this Environment's log and transactor. */
  def tryLogin(userId: String, password: String): F[Option[GemService[F]]]

  /** Like tryLogin, but for prevoiusly authenticated users. */
  def service(userId: String): F[Option[GemService[F]]]

  /** Shut down this environment, releasing any held resources. */
  def shutdown: F[Unit]

}

object Environment {

  // Right now we'll just create a key on startup, which means sessions can't survive server
  // re-start. We could also write it to a file and try to read it on startup, or use a shared
  // keychain to support single sign-on. But for now keep it simple.
  private def randomSecretKey[F[_]: Sync](algorithm: JwtHmacAlgorithm): F[SecretKey] =
    Sync[F].delay {
      val bytes = new Array[Byte](128)
      scala.util.Random.nextBytes(bytes)
      new SecretKeySpec(bytes, algorithm.fullName)
    }

  /** Realize a "living" server environment from a static configuration. */
  def quicken[F[_]: Async: ContextShift](cfg: WebConfiguration, db: DatabaseConfiguration)(
    implicit ev: ContextShift[IO]
  ): F[Environment[F]] = {

    val xa  = db.transactor[F ] // TODO: hikari
    val lxa = db.transactor[IO] // TODO: hikari

    (randomSecretKey(cfg.jwt.algorithm), Log.newLog[F](cfg.log.name, lxa)).mapN { (sk, lg) =>
      new Environment[F] {

        override val config     = cfg
        override def log        = lg
        override def transactor = xa

        override def encodeJwt[A: Encoder](claim: A) =
          Jwt.encode(claim.asJson.noSpaces, sk, cfg.jwt.algorithm)

        override def decodeJwt[A: Decoder](value: String) =
          Sync[F].delay(Jwt.decode(value, sk).toEither.leftMap(_.getMessage).flatMap(decode[A](_).leftMap(_.getMessage)))

        override def tryLogin(userId: String, password: String): F[Option[GemService[F]]] =
          GemService.tryLogin(userId, password, transactor, log)

        override def service(userId: String): F[Option[GemService[F]]] =
          GemService.service(userId, transactor, log)

        override def shutdown: F[Unit] =
          log.shutdown(config.log.shutdownTimeout)

      }
    }
  }

  /** A single-element stream yielding an Environment, which will be cleaned up. */
  def resource[F[_]: Async: ContextShift](cfg: WebConfiguration, db: DatabaseConfiguration)(
    implicit ev: ContextShift[IO]
  ): Resource[F, Environment[F]] =
    Resource.make(quicken(cfg, db))(_.shutdown)

}
