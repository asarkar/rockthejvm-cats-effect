package concurrency

import cats.effect.IO
import cats.effect.IOApp

import scala.concurrent.duration.DurationInt

object CancellingIOs extends IOApp.Simple:

  /*
    Cancelling IOs
    - fib.cancel
    - IO.race & other APIs
    - manual cancellation
   */
  val chainOfIOs: IO[Int] = IO("waiting").debug() >> IO.canceled >> IO(42).debug()

  // uncancelable
  // example: online store, payment processor
  // payment process must NOT be canceled
  val specialPaymentSystem: IO[String] = (
    IO("Payment running, don't cancel me...").debug() >>
      IO.sleep(1.second) >>
      IO("Payment completed.").debug()
  ).onCancel(IO("MEGA CANCEL OF DOOM!").debug().void)

  val cancellationOfDoom: IO[Unit] =
    for
      fib <- specialPaymentSystem.start
      _   <- IO.sleep(500.millis) >> fib.cancel
      _   <- fib.join
    yield ()

  val atomicPayment: IO[String] = IO.uncancelable(_ => specialPaymentSystem) // "masking"
  val atomicPayment_v2          = specialPaymentSystem.uncancelable          // same

  val noCancellationOfDoom: IO[Unit] =
    for
      fib <- atomicPayment.start
      _   <- IO.sleep(500.millis) >> IO("attempting cancellation...").debug() >> fib.cancel
      _   <- fib.join
    yield ()

  /*
    The uncancelable API is more complex and more general.
    It takes a function from Poll[IO] to IO. In the example above, we aren't using that Poll instance.
    The Poll object can be used to mark sections within the returned effect which CAN BE CANCELED.
   */

  /*
    Example: authentication service. Has two parts:
    - input password, can be cancelled, because otherwise we might block indefinitely on user input
    - verify password, CANNOT be cancelled once it's started
   */
  val inputPassword: IO[String] =
    IO("Input password:").debug() >> IO("(typing password)").debug() >> IO.sleep(2.seconds) >> IO(
      "RockTheJVM1!"
    )
  val verifyPassword: String => IO[Boolean] = (pw: String) =>
    IO("verifying...").debug() >> IO.sleep(2.seconds) >> IO(pw == "RockTheJVM1!")

  val authFlow: IO[Unit] = IO.uncancelable { poll =>
    for
      pw <- poll(inputPassword).onCancel(
        IO("Authentication timed out. Try again later.").debug().void
      ) // this is cancelable
      verified <- verifyPassword(pw) // this is NOT cancelable
      _ <-
        if (verified) IO("Authentication successful.").debug() // this is NOT cancelable
        else IO("Authentication failed.").debug()
    yield ()
  }

  val authProgram: IO[Unit] =
    for
      authFib <- authFlow.start
      _ <- IO.sleep(3.seconds) >> IO("Authentication timeout, attempting cancel...")
        .debug() >> authFib.cancel
      _ <- authFib.join
    yield ()

  /*
  Uncancelable calls are MASKS which suppress cancellation.
  Poll calls are "gaps opened" in the uncancelable region.
   */

  override def run: IO[Unit] = invincibleAuthProgram.void

  /** Exercises: what do you think the following effects will do?
    *   1. Anticipate
    *   2. Run to see if you're correct
    *   3. Prove your theory
    */
  // 1
  val cancelBeforeMol: IO[Int] = IO.canceled >> IO(42).debug()
  val uncancelableMol: IO[Int] = IO.uncancelable(_ => cancelBeforeMol)
// uncancelable will eliminate ALL cancel points

  // 2
  val invincibleAuthProgram: IO[Unit] = for {
    authFib <- IO.uncancelable(_ => authFlow).start
    _ <- IO.sleep(1.seconds) >> IO("Authentication timeout, attempting cancel...")
      .debug() >> authFib.cancel
    _ <- authFib.join
  } yield ()

  /*
  Lesson: Uncancelable calls are masks which suppress all existing cancelable gaps (including from a previous uncancelable).
   */

  // 3
  def threeStepProgram(): IO[Unit] =
    val sequence = IO.uncancelable { poll =>
      poll(IO("cancelable").debug() >> IO.sleep(1.second) >> IO("cancelable end").debug()) >>
        IO("uncancelable").debug() >> IO.sleep(1.second) >> IO("uncancelable end").debug() >>
        poll(
          IO("second cancelable").debug() >> IO.sleep(1.second) >> IO("second cancelable end")
            .debug()
        )
    }

    for {
      fib <- sequence.start
      _   <- IO.sleep(1500.millis) >> IO("CANCELING").debug() >> fib.cancel
      _   <- fib.join
    } yield ()

/*
  Lesson: Uncancelable regions ignore cancellation signals, but that doesn't mean the next CANCELABLE region won't take them.
 */
