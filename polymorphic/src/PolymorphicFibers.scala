package polymorphic

import cats.effect.Fiber
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.MonadCancel
import cats.effect.Outcome
import cats.effect.Outcome.*
import cats.effect.Spawn
import cats.effect.kernel.Async

object PolymorphicFibers extends IOApp.Simple:

  // Spawn = create fibers for any effect
  trait MyGenSpawn[F[_], E] extends MonadCancel[F, E]:
    def start[A](fa: F[A]): F[Fiber[F, Throwable, A]] // creates a fiber
    def never[A]: F[A]                                // a forever-suspending effect
    def cede: F[Unit]                                 // a "yield" effect

    def racePair[A, B](fa: F[A], fb: F[B]): F[Either[ // fundamental racing
      (Outcome[F, E, A], Fiber[F, E, B]),
      (Fiber[F, E, A], Outcome[F, E, B])
    ]]

  trait MySpawn[F[_]] extends MyGenSpawn[F, Throwable]

  val mol: IO[Int]                         = IO(42)
  val fiber: IO[Fiber[IO, Throwable, Int]] = mol.start

  // capabilities: pure, map/flatMap, raiseError, uncancelable, start

  val spawnIO: Async[IO] = Spawn[IO] // fetch the given/implicit Spawn[IO]

  def ioOnSomeThread[A](io: IO[A]): IO[Outcome[IO, Throwable, A]] =
    for
      fib    <- spawnIO.start(io) // io.start assumes the presence of a Spawn[IO]
      result <- fib.join
    yield result

  import cats.syntax.functor.toFunctorOps // map
  import cats.syntax.flatMap.toFlatMapOps // flatMap

  // generalize
  import cats.effect.syntax.spawn.* // start extension method
  def effectOnSomeThread[F[_], A](fa: F[A])(using spawn: Spawn[F]): F[Outcome[F, Throwable, A]] =
    for
      fib    <- fa.start
      result <- fib.join
    yield result

  val molOnFiber: IO[Outcome[IO, Throwable, Int]]    = ioOnSomeThread(mol)
  val molOnFiber_v2: IO[Outcome[IO, Throwable, Int]] = effectOnSomeThread(mol)

  /** Exercise - generalize the following code (race implementation from the Racing lesson)
    */

  def ioRace[A, B](ioa: IO[A], iob: IO[B]): IO[Either[A, B]] =
    IO.racePair(ioa, iob)
      .flatMap:
        case Left((outA, fibB)) =>
          outA match
            case Succeeded(effectA) => fibB.cancel >> effectA.map(a => Left(a))
            case Errored(e)         => fibB.cancel >> IO.raiseError(e)
            case Canceled() =>
              fibB.join.flatMap:
                case Succeeded(effectB) => effectB.map(b => Right(b))
                case Errored(e)         => IO.raiseError(e)
                case Canceled() =>
                  IO.raiseError(new RuntimeException("Both computations canceled."))
        case Right((fibA, outB)) =>
          outB match
            case Succeeded(effectB) => fibA.cancel >> effectB.map(b => Right(b))
            case Errored(e)         => fibA.cancel >> IO.raiseError(e)
            case Canceled() =>
              fibA.join.flatMap:
                case Succeeded(effectA) => effectA.map(a => Left(a))
                case Errored(e)         => IO.raiseError(e)
                case Canceled() =>
                  IO.raiseError(new RuntimeException("Both computations canceled."))

  def generalRace[F[_], A, B](fa: F[A], fb: F[B])(using spawn: Spawn[F]): F[Either[A, B]] =
    spawn
      .racePair(fa, fb)
      .flatMap:
        case Left((outA, fibB)) =>
          outA match
            case Succeeded(effectA) => fibB.cancel.flatMap(_ => effectA.map(a => Left(a)))
            case Errored(e)         => fibB.cancel.flatMap(_ => spawn.raiseError(e))
            case Canceled() =>
              fibB.join.flatMap:
                case Succeeded(effectB) => effectB.map(b => Right(b))
                case Errored(e)         => spawn.raiseError(e)
                case Canceled() =>
                  spawn.raiseError(new RuntimeException("Both computations canceled."))
        case Right((fibA, outB)) =>
          outB match
            case Succeeded(effectB) => fibA.cancel.flatMap(_ => effectB.map(b => Right(b)))
            case Errored(e)         => fibA.cancel.flatMap(_ => spawn.raiseError(e))
            case Canceled() =>
              fibA.join.flatMap:
                case Succeeded(effectA) => effectA.map(a => Left(a))
                case Errored(e)         => spawn.raiseError(e)
                case Canceled() =>
                  spawn.raiseError(new RuntimeException("Both computations canceled."))
  // beware this is a simple implementation - certain cases are not taken into account
  // (which would make the code more complicated)

  import scala.concurrent.duration.DurationInt

  val fast: IO[Int]                    = IO.sleep(1.second) >> IO(42).debug()
  val slow: IO[String]                 = IO.sleep(2.seconds) >> IO("Scala").debug()
  val race: IO[Either[Int, String]]    = ioRace(fast, slow)
  val race_v2: IO[Either[Int, String]] = generalRace(fast, slow)

  override def run = race.void
