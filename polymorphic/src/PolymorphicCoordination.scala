package polymorphic

import cats.effect.Concurrent
import cats.effect.Deferred
import cats.effect.Fiber
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Outcome
import cats.effect.Ref
import cats.effect.Spawn
import cats.effect.kernel.Async

import scala.concurrent.duration.DurationInt

object PolymorphicCoordination extends IOApp.Simple:

  // Concurrent - Ref + Deferred for ANY effect type
  trait MyConcurrent[F[_]] extends Spawn[F]:
    def ref[A](a: A): F[Ref[F, A]]
    def deferred[A]: F[Deferred[F, A]]

  val concurrentIO: Async[IO]          = Concurrent[IO]    // given instance of Concurrent[IO]
  val aDeferred: IO[Deferred[IO, Int]] = Deferred[IO, Int] // given/implicit Concurrent[IO] in scope
  val aDeferred_v2: IO[Deferred[IO, Int]] = concurrentIO.deferred[Int]
  val aRef: IO[Ref[IO, Int]]              = concurrentIO.ref(42)

  // capabilities: pure, map/flatMap, raiseError, uncancelable, start (fibers), + ref/deferred

  def eggBoiler(): IO[Unit] =
    def eggReadyNotification(signal: Deferred[IO, Unit]) =
      for
        _ <- IO("Egg boiling on some other fiber, waiting...").debug()
        _ <- signal.get
        _ <- IO("EGG READY!").debug()
      yield ()

    def tickingClock(counter: Ref[IO, Int], signal: Deferred[IO, Unit]): IO[Unit] =
      for
        _     <- IO.sleep(1.second)
        count <- counter.updateAndGet(_ + 1)
        _     <- IO(count).debug()
        _     <- if count >= 10 then signal.complete(()) else tickingClock(counter, signal)
      yield ()

    for
      counter         <- Ref[IO].of(0)
      signal          <- Deferred[IO, Unit]
      notificationFib <- eggReadyNotification(signal).start
      clock           <- tickingClock(counter, signal).start
      _               <- notificationFib.join
      _               <- clock.join
    yield ()

  import cats.syntax.flatMap.toFlatMapOps // flatMap
  import cats.syntax.functor.toFunctorOps // map
  import cats.effect.syntax.spawn.*       // start extension method
  import Utils.*

  def polymorphicEggBoiler[F[_]](using concurrent: Concurrent[F]): F[Unit] =
    def eggReadyNotification(signal: Deferred[F, Unit]) =
      for
        _ <- concurrent.pure("Egg boiling on some other fiber, waiting...").debug
        _ <- signal.get
        _ <- concurrent.pure("EGG READY!").debug
      yield ()

    def tickingClock(counter: Ref[F, Int], signal: Deferred[F, Unit]): F[Unit] =
      for
        _     <- unsafeSleep[F, Throwable](1.second)
        count <- counter.updateAndGet(_ + 1)
        _     <- concurrent.pure(count).debug
        _     <- if count >= 10 then signal.complete(()).void else tickingClock(counter, signal)
      yield ()

    for
      counter         <- concurrent.ref(0)
      signal          <- concurrent.deferred[Unit]
      notificationFib <- eggReadyNotification(signal).start
      clock           <- tickingClock(counter, signal).start
      _               <- notificationFib.join
      _               <- clock.join
    yield ()

  /** Exercises:
    *   1. Generalize racePair
    *   2. Generalize the Mutex concurrency primitive for any F
    */
  type RaceResult[F[_], A, B] = Either[
    (Outcome[F, Throwable, A], Fiber[F, Throwable, B]), // (winner result, loser fiber)
    (Fiber[F, Throwable, A], Outcome[F, Throwable, B])  // (loser fiber, winner result)
  ]

  type EitherOutcome[F[_], A, B] = Either[Outcome[F, Throwable, A], Outcome[F, Throwable, B]]

  import cats.effect.syntax.monadCancel.* // guaranteeCase extension method

  def ourRacePair[F[_], A, B](fa: F[A], fb: F[B])(using
      concurrent: Concurrent[F]
  ): F[RaceResult[F, A, B]] =
    concurrent.uncancelable { poll =>
      for
        signal <- concurrent.deferred[EitherOutcome[F, A, B]]
        fiba   <- fa.guaranteeCase(outcomeA => signal.complete(Left(outcomeA)).void).start
        fibb   <- fb.guaranteeCase(outcomeB => signal.complete(Right(outcomeB)).void).start
        result <- poll(signal.get).onCancel: // blocking call - should be cancelable
          for
            cancelFibA <- fiba.cancel.start
            cancelFibB <- fibb.cancel.start
            _          <- cancelFibA.join
            _          <- cancelFibB.join
          yield ()
      yield result match
        case Left(outcomeA)  => Left((outcomeA, fibb))
        case Right(outcomeB) => Right((fiba, outcomeB))
    }

  override def run: IO[Unit] = polymorphicEggBoiler[IO]
