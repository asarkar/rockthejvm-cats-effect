package concurrency

import cats.effect.Fiber
import cats.effect.FiberIO
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Outcome
import cats.effect.OutcomeIO
import utils.Threads.showThread

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

object Fibers extends IOApp.Simple:

  val meaningOfLife: IO[Int] = IO.pure(42)
  val favLang: IO[String]    = IO.pure("Scala")

  def sameThreadIOs(): IO[Unit] =
    for
      _ <- meaningOfLife.showThread()
      _ <- favLang.showThread()
    yield ()

  // introducing Fiber: a data structure describing an effect running on some thread
  def createFiber: FiberIO[String] = ??? // almost impossible to create fibers manually

  // the fiber is not actually started, but the fiber allocation is wrapped in another effect
  val aFiber: IO[Fiber[IO, Throwable, Int]] = meaningOfLife.showThread().start

  def differentThreadIOs(): IO[Unit] =
    for
      _ <- aFiber
      _ <- favLang.showThread()
    yield ()

  // joining a fiber
  def runOnSomeOtherThread[A](io: IO[A]): IO[OutcomeIO[A]] =
    for
      fib    <- io.start
      result <- fib.join // an effect which waits for the fiber to terminate
    yield result
  /*
    possible outcomes:
    - success with an IO
    - failure with an exception
    - cancelled
   */

  val someIOOnAnotherThread: IO[OutcomeIO[Int]] = runOnSomeOtherThread(meaningOfLife)
  val someResultFromAnotherThread: IO[Int] = someIOOnAnotherThread.flatMap:
    case Outcome.Succeeded(effect) => effect
    case Outcome.Errored(e)        => IO(0)
    case Outcome.Canceled()        => IO(0)

  def throwOnAnotherThread(): IO[OutcomeIO[Int]] =
    for
      fib    <- IO.raiseError[Int](new RuntimeException("no number for you")).start
      result <- fib.join
    yield result

  def testCancel(): IO[Outcome[IO, Throwable, String]] =
    val task = IO("starting").showThread() >> IO.sleep(1.second) >> IO("done").showThread()
    // onCancel is a "finalizer", allowing you to free up resources in case you get canceled
    val taskWithCancellationHandler = task.onCancel(IO("I'm being cancelled!").showThread().void)

    for
      fib <- taskWithCancellationHandler.start                     // on a separate thread
      _   <- IO.sleep(500.millis) >> IO("cancelling").showThread() // running on the calling thread
      _   <- fib.cancel
      result <- fib.join
    yield result

  override def run: IO[Unit] = testCancel().showThread().void

  /*
  1. Write a function that runs an IO on another thread, and, depending on the result of the fiber
     - return the result in an IO
     - if errored or cancelled, return a failed IO
   */
  def processResultsFromFiber[A](io: IO[A]): IO[A] =
    runOnSomeOtherThread(io).flatMap(processOutcome)

  private def processOutcome[A](outcome: Outcome[IO, Throwable, A]): IO[A] =
    outcome match
      case Outcome.Succeeded(effect) => effect
      case Outcome.Errored(e)        => IO.raiseError(e)
      case Outcome.Canceled()        => IO.raiseError(new RuntimeException("Got canceled!"))

  /*
  2. Write a function that takes two IOs, runs them on different fibers and returns an IO with a tuple containing both results.
     - if both IOs complete successfully, tuple their results
     - if the first IO returns an error, raise that error (ignoring the second IO's result/error)
     - if the first IO doesn't error but second IO returns an error, raise that error
     - if one (or both) canceled, raise a RuntimeException
   */
  import cats.syntax.apply.catsSyntaxTuple2Semigroupal
  def tupleIOs[A, B](ioa: IO[A], iob: IO[B]): IO[(A, B)] =
    (processResultsFromFiber(ioa), processResultsFromFiber(iob)).mapN((a, b) => (a, b))

  /*
  3. Write a function that adds a timeout to an IO:
     - IO runs on a fiber
     - if the timeout duration passes, then the fiber is canceled
     - the method returns an IO[A] which contains
       - the original value if the computation is successful before the timeout signal
       - the exception if the computation is failed before the timeout signal
       - a RuntimeException if it times out (i.e. cancelled by the timeout)
   */
  def timeout[A](ioa: IO[A], duration: FiniteDuration): IO[A] =
    (for
      fib    <- ioa.timeout(duration).start
      result <- fib.join
    yield result).flatMap(processOutcome)
