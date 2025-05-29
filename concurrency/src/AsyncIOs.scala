package concurrency

import cats.effect.IO
import cats.effect.IOApp

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

object AsyncIOs extends IOApp.Simple:

  // IOs can run asynchronously on fibers, without having to manually manage the fiber lifecycle
  val threadPool: ExecutorService = Executors.newFixedThreadPool(8)
  given ec: ExecutionContext      = ExecutionContext.fromExecutorService(threadPool)
  type Callback[A] = Either[Throwable, A] => Unit

  def computeMeaningOfLife(): Int =
    Thread.sleep(1000)
    println(
      s"[${Thread.currentThread().getName}] computing the meaning of life on some other thread..."
    )
    42

  def computeMeaningOfLifeEither(): Either[Throwable, Int] = Try(computeMeaningOfLife()).toEither

  def computeMolOnThreadPool(): Unit =
    threadPool.execute(() => computeMeaningOfLife())

  // lift computation to an IO
  // async is a FFI (Foreign Function Interface)
  val asyncMolIO: IO[Int] =
    // CE thread blocks (semantically) until this cb is invoked (by some other thread)
    IO.async_ { (cb: Callback[Int]) =>
      threadPool.execute { () => // computation not managed by CE
        val result = computeMeaningOfLifeEither()
        cb(result) // CE thread is notified with the result
      }
    }

  // Exercise: lift an async computation on ec to an IO.
  def asyncToIO[A](computation: () => A)(ec: ExecutionContext): IO[A] =
    IO.async_ { (cb: Callback[A]) =>
      ec.execute { () =>
        val result = Try(computation()).toEither
        cb(result)
      }
    }

  val asyncMolIO_v2: IO[Int] = asyncToIO(computeMeaningOfLife)(ec)

  // Exercise: lift an async computation as a Future, to an IO.
  lazy val molFuture: Future[Int] = Future(computeMeaningOfLife())

  def convertFutureToIO[A](future: => Future[A]): IO[A] =
    IO.async_ { (cb: Callback[A]) =>
      future.onComplete(result => cb(result.toEither))
    }

  val asyncMolIO_v3: IO[Int] = convertFutureToIO(molFuture)
  val asyncMolIO_v4: IO[Int] = IO.fromFuture(IO(molFuture))

  // Exercise: a never-ending IO?
  val neverEndingIO: IO[Int]    = IO.async_[Int](_ => ()) // no callback, no finish
  val neverEndingIO_v2: IO[Int] = IO.never

  import scala.concurrent.duration.*

  /*
    FULL ASYNC Call
   */
  def demoAsyncCancellation(): IO[Unit] =
    val asyncMeaningOfLifeIO_v2: IO[Int] = IO.async { (cb: Callback[Int]) =>
      /*
        finalizer in case computation gets cancelled.
        finalizers are of type IO[Unit]
        not specifying finalizer => Option[IO[Unit]]
        creating option is an effect => IO[Option[IO[Unit]]]
       */
      // return IO[Option[IO[Unit]]]
      IO {
        threadPool.execute { () =>
          val result = computeMeaningOfLifeEither()
          cb(result)
        }
      }.as(Some(IO("Cancelled!").debug().void))
    }

    for
      fib <- asyncMeaningOfLifeIO_v2.start
      _   <- IO.sleep(500.millis) >> IO("cancelling...").debug() >> fib.cancel
      _   <- fib.join
    yield ()

  override def run: IO[Unit] = demoAsyncCancellation().debug() >> IO(threadPool.shutdown())
