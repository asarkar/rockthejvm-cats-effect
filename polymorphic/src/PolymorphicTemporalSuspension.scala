package polymorphic

import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Temporal
import cats.effect.kernel.Async

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

object PolymorphicTemporalSuspension extends IOApp.Simple:

  // Temporal - time-blocking effects
  trait MyTemporal[F[_]] extends Concurrent[F]:
    def sleep(time: FiniteDuration): F[Unit] // semantically blocks this fiber for a specified time

  // abilites: pure, map/flatMap, raiseError, uncancelable, start, ref/deferred, +sleep
  val temporalIO: Async[IO] = Temporal[IO] // given Temporal[IO] in scope
  val chainOfEffects: IO[String] =
    IO("Loading...").debug() *> IO.sleep(1.second) *> IO("Game ready!").debug()
  val chainOfEffects_v2: IO[String] = temporalIO.pure("Loading...").debug() *> temporalIO.sleep(
    1.second
  ) *> temporalIO.pure("Game ready!").debug() // same

  /** Exercise: generalize the following piece
    */
  import cats.syntax.flatMap.toFlatMapOps
  def timeout[F[_], A](fa: F[A], duration: FiniteDuration)(using temporal: Temporal[F]): F[A] =
    val timeoutEffect = temporal.sleep(duration)
    val result        = temporal.race(fa, timeoutEffect)

    result.flatMap:
      case Left(v)  => temporal.pure(v)
      case Right(_) => temporal.raiseError(new RuntimeException("Computation timed out."))

  override def run = ???
