package gem.ctl
package free

import scalaz._, Scalaz._
import scalaz.effect._

import gem.ctl.low.io._
import gem.ctl.free.ctl._

/** An interpreter for the control language. */
object interpreter {

  /** Configuration for the interpreter. */
  trait Config {
    def userAndHost: UserAndHost
    def verbose:     Boolean
  }

  /**
   * Construct an interpreter of `CtlIO` given a `Config` and an `IORef` for the initial indentation
   * of log messages.
   */
  def interpreter(c: Config, indent: IORef[Int]) = λ[CtlOp ~> EitherT[IO, Int, ?]] {
    case CtlOp.Shell(false, cmd) => doShell(cmd, c.verbose, indent)
    case CtlOp.Shell(true,  cmd) => doShell(cmd.bimap(s => s"ssh ${c.userAndHost.userAndHost} $s", "ssh" :: c.userAndHost.userAndHost :: _), c.verbose, indent)
    case CtlOp.Exit(exitCode)    => EitherT.left(exitCode.point[IO])
    case CtlOp.GetConfig         => c.point[EitherT[IO, Int, ?]]
    case CtlOp.Gosub(level, msg, fa) =>
      for {
        _ <- doLog(level, msg, indent)
        _ <- EitherT.right(indent.mod(_ + 1))
        a <- fa.foldMap(this)
        _ <- EitherT.right(indent.mod(_ - 1))
      } yield a
  }

  /**
   * Construct a program to log a message to the console at the given log level and indentation.
   * This is where all the colorizing happens.
   */
  private def doLog(level: Level, msg: String, indent: IORef[Int]): EitherT[IO, Int, Unit] = {
    val color = level match {
      case Level.Error => Console.RED
      case Level.Warn  => Console.YELLOW
      case Level.Info  => Console.GREEN
      case Level.Shell => "\u001B[0;37m" // gray
    }
    EitherT.right {
      val pre = s"[${level.toString.take(4).toLowerCase}]"
      val messageColor = color // if (level == Shell) color else Console.BLUE
      for {
        i <- indent.read.map("  " * _)
        _ <- IO.putStrLn(f"$color$pre%-7s $messageColor$i$msg${Console.RESET}")
      } yield ()
    }
  }

  /**
   * Construct a program to perform a shell operation, optionally logging the output (if verbose),
   * and gathering the result as an `Output`.
   */
  private def doShell(cmd: String \/ List[String], verbose: Boolean, indent: IORef[Int]): EitherT[IO, Int, Output] = {

    def handler(s: String): IO[Unit] =
      if (verbose) doLog(Level.Shell, s, indent).run.map(_.toOption.get) // shh
      else IO.putStr(".")

    for {
      _ <- verbose.whenM(doLog(Level.Shell, s"$$ ${cmd.fold(identity, _.mkString(" "))}", indent))
      o <- EitherT.right(exec(cmd, handler))
      _ <- verbose.whenM(doLog(Level.Shell, s"exit(${o.exitCode})", indent))
      - <- verbose.unlessM(EitherT.right[IO, Int, Unit](IO.putStr("\u001B[1G\u001B[K")))
    } yield o

  }

}
