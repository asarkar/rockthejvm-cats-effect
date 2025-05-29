package effects

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp

import scala.io.StdIn

object IOApps:
  val program: IO[Unit] = for {
    line <- IO(StdIn.readLine())
    _    <- IO(println(s"You've just written: $line"))
  } yield ()

object TestApp:
  def main(args: Array[String]): Unit =
    import cats.effect.unsafe.implicits.global
    IOApps.program.unsafeRunSync()

object FirstCEApp extends IOApp:
  override def run(args: List[String]): IO[ExitCode] =
    IOApps.program.as(ExitCode.Success)

object MySimpleApp extends IOApp.Simple:
  override def run = IOApps.program
