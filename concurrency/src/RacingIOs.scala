package concurrency

import cats.effect.FiberIO
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Outcome
import cats.effect.OutcomeIO

import scala.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

object RacingIOs extends IOApp.Simple:

  def runWithSleep[A](value: A, duration: FiniteDuration): IO[A] =
    (
      IO(s"starting computation: $value").debug() >>
        IO.sleep(duration) >>
        IO(s"computation for $value: done") >>
        IO(value)
    ).onCancel(IO(s"computation CANCELED for $value").debug().void)

  def testRace(): IO[String] =
    val meaningOfLife                  = runWithSleep(42, 1.second)
    val favLang                        = runWithSleep("Scala", 2.seconds)
    val first: IO[Either[Int, String]] = IO.race(meaningOfLife, favLang)
    /*
      - both IOs run on separate fibers
      - the first one to finish will complete the result
      - the loser will be canceled
     */

    first.flatMap:
      case Left(mol)   => IO(s"Meaning of life won: $mol")
      case Right(lang) => IO(s"Fav language won: $lang")

  /*
  We need the upper bound, instead of simply `Int | String` because `Outcome` is invariant.
  Using the upper bound means any type that fits inside the union `Int | String`, including
  `Int`, `String`, or the union itself.
  https://users.scala-lang.org/t/union-type-counterintuitive-behavior/10790
   */
  def testRacePair(): IO[Outcome[IO, Throwable, ? <: Int | String]] =
    val meaningOfLife = runWithSleep(42, 1.second)
    val favLang       = runWithSleep("Scala", 2.seconds)
    val raceResult: IO[Either[
      (OutcomeIO[Int], FiberIO[String]), // (winner result, loser fiber)
      (FiberIO[Int], OutcomeIO[String])  // (loser fiber, winner result)
    ]] = IO.racePair(meaningOfLife, favLang)

    raceResult.flatMap:
      case Left((outMol, fibLang)) => fibLang.cancel >> IO("MOL won").debug() >> IO(outMol).debug()
      case Right((fibMol, outLang)) =>
        fibMol.cancel >> IO("Language won").debug() >> IO(outLang).debug()

  /** Exercises: 1 - implement a timeout pattern with race 2 - a method to return a LOSING effect
    * from a race (hint: use racePair) 3 - implement race in terms of racePair
    */
  // 1
  def timeout[A](io: IO[A], duration: FiniteDuration): IO[A] =
    IO.race(io, IO.sleep(duration))
      .flatMap:
        case Left(a)  => IO(a)
        case Right(_) => IO.raiseError(new TimeoutException())

  // 2
  def unrace[A, B](ioa: IO[A], iob: IO[B]): IO[Either[A, B]] =
    IO.racePair(ioa, iob)
      .flatMap:
        case Left((_, fibB))  => fibB.join.flatMap(outcome2IO).map(Right(_))
        case Right((fibA, _)) => fibA.join.flatMap(outcome2IO).map(Left(_))

  def outcome2IO[A](out: OutcomeIO[A]): IO[A] =
    out.fold(
      IO.raiseError(new RuntimeException("Cancelled")), // Canceled
      IO.raiseError,                                    // Errored
      identity                                          // Succeeded
    )

  // 3
  def simpleRace[A, B](ioa: IO[A], iob: IO[B]): IO[Either[A, B]] =
    IO.racePair(ioa, iob)
      .flatMap:
        case Left((outA, fibB))  => fibB.cancel *> outcome2IO(outA).map(Left(_))
        case Right((fibA, outB)) => fibA.cancel *> outcome2IO(outB).map(Right(_))

  override def run: IO[Unit] = testRace().debug().void
