package concurrency

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import utils.Threads.showThread

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object BlockingIOs extends IOApp.Simple:

  val someSleeps: IO[Unit] =
    for
      _ <- IO
        .sleep(1.second)
        .showThread() // SEMANTIC BLOCKING - no threads are actually blocked, CE assigns this thread to some other fiber
      _ <- IO.sleep(1.second).showThread()
    yield ()

  // really blocking IOs
  val aBlockingIO: IO[Int] = IO.blocking:
    Thread.sleep(1000)
    println(s"[${Thread.currentThread().getName}] computed a blocking code")
    42
  // will evaluate on a thread from ANOTHER thread pool specific for blocking calls

  // yielding
  val iosOnManyThreads: IO[Unit] =
    for
      _ <- IO("first").showThread()
      _ <- IO.cede // a signal to yield control over the thread - equivalent to IO.shift from CE2
      _ <- IO("second")
        .showThread() // the rest of this effect may run on another thread (not necessarily)
      _ <- IO.cede
      _ <- IO("third").showThread()
    yield ()

  def testThousandEffectsSwitch(): IO[Int] =
    Resource
      .make(IO(ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))))(ec =>
        IO(ec.close())
      )
      .use(ec =>
        (1 to 1000).map(IO.pure).reduce(_.showThread() >> IO.cede >> _.showThread()).evalOn(ec)
      )

  /*
    Blocking calls & IO.sleep and yield control over the calling thread automatically.
   */
  override def run: IO[Unit] = testThousandEffectsSwitch().void
