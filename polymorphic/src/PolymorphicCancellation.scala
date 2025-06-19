package polymorphic

import cats.Applicative
import cats.Monad
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.MonadCancel
import cats.effect.Poll
import cats.effect.kernel.Outcome

object PolymorphicCancellation extends IOApp.Simple:

  trait MyApplicativeError[F[_], E] extends Applicative[F]:
    def raiseError[A](error: E): F[A]
    def handleErrorWith[A](fa: F[A])(f: E => F[A]): F[A]

  trait MyMonadError[F[_], E] extends MyApplicativeError[F, E] with Monad[F]

  // MonadCancel describes the capability to cancel & prevent cancellation

  trait MyPoll[F[_]]:
    def apply[A](fa: F[A]): F[A]

  trait MyMonadCancel[F[_], E] extends MyMonadError[F, E]:
    def canceled: F[Unit]
    def uncancelable[A](poll: Poll[F] => F[A]): F[A]

  // monadCancel for IO
  val monadCancelIO: MonadCancel[IO, Throwable] = MonadCancel[IO]

  // we can create values, because MonadCancel is a Monad
  val molIO: IO[Int]          = monadCancelIO.pure(42)
  val ambitiousMolIO: IO[Int] = monadCancelIO.map(molIO)(_ * 10)

  val mustCompute: IO[Int] = monadCancelIO.uncancelable { _ =>
    for
      _   <- monadCancelIO.pure("once started, I can't go back...")
      res <- monadCancelIO.pure(56)
    yield res
  }

  import cats.syntax.flatMap.toFlatMapOps // flatMap
  import cats.syntax.functor.toFunctorOps // map

  // goal: can generalize code
  def mustComputeGeneral[F[_], E](using mc: MonadCancel[F, E]): F[Int] = mc.uncancelable { _ =>
    for
      _   <- mc.pure("once started, I can't go back...")
      res <- mc.pure(56)
    yield res
  }

  val mustCompute_v2: IO[Int] = mustComputeGeneral[IO, Throwable]

  // allow cancellation listeners
  val mustComputeWithListener: IO[Int] = mustCompute.onCancel(IO("I'm being cancelled!").void)
  val mustComputeWithListener_v2: IO[Int] =
    monadCancelIO.onCancel(mustCompute, IO("I'm being cancelled!").void) // same
  // .onCancel as extension method
//  import cats.effect.syntax.monadCancel._ // .onCancel

  // allow finalizers: guarantee, guaranteeCase
  val aComputationWithFinalizers: IO[Int] = monadCancelIO.guaranteeCase(IO(42)):
    case Outcome.Succeeded(fa) => fa.flatMap(a => IO(s"successful: $a").void)
    case Outcome.Errored(e)    => IO(s"failed: $e").void
    case Outcome.Canceled()    => IO("canceled").void

  // bracket pattern is specific to MonadCancel
  val aComputationWithUsage: IO[String] = monadCancelIO.bracket(IO(42)) { value =>
    IO(s"Using the meaning of life: $value")
  } { _ =>
    IO("releasing the meaning of life...").void
  }
  // therefore Resources can only be built in the presence of a MonadCancel instance

  /** Exercise - generalize a piece of code (the auth-flow example from the Cancellation lesson)
    */
  import Utils.*
  import scala.concurrent.duration.DurationInt

  def inputPassword[F[_], E](using mc: MonadCancel[F, E]): F[String] =
    for
      _  <- mc.pure("Input password:").debug
      _  <- mc.pure("(typing password)").debug
      _  <- unsafeSleep[F, E](5.seconds)
      pw <- mc.pure("RockTheJVM1!")
    yield pw

  def verifyPassword[F[_], E](pw: String)(using mc: MonadCancel[F, E]): F[Boolean] =
    for
      _     <- mc.pure("verifying...").debug
      _     <- unsafeSleep[F, E](2.seconds)
      check <- mc.pure(pw == "RockTheJVM1!")
    yield check

  import cats.effect.syntax.monadCancel.monadCancelOps_

  def authFlow[F[_], E](using mc: MonadCancel[F, E]): F[Unit] = mc.uncancelable { poll =>
    for
      pw <- poll(inputPassword).onCancel(
        mc.pure("Authentication timed out. Try again later.").debug.void
      ) // this is cancelable
      verified <- verifyPassword(pw) // this is NOT cancelable
      _ <-
        if verified then mc.pure("Authentication successful.").debug // this is NOT cancelable
        else mc.pure("Authentication failed.").debug
    yield ()
  }

  val authProgram: IO[Unit] =
    for
      authFib <- authFlow[IO, Throwable].start
      _ <- IO.sleep(3.seconds) >> IO("Authentication timeout, attempting cancel...")
        .debug() >> authFib.cancel
      _ <- authFib.join
    yield ()

  override def run = authProgram
