import Threads.showThread
import cats.Parallel
import cats.effect.IO
import cats.effect.IOApp

object IOParallelism extends IOApp.Simple:
  // IOs are usually sequential
  val aniIO: IO[String]    = IO(s"[${Thread.currentThread().getName}] Ani")
  val kamranIO: IO[String] = IO(s"[${Thread.currentThread().getName}] Kamran")

  val composedIO: IO[String] = for
    ani    <- aniIO
    kamran <- kamranIO
  yield s"$ani and $kamran love Rock the JVM"

  // mapN extension method
  import cats.syntax.apply.catsSyntaxTuple2Semigroupal

  val meaningOfLife: IO[Int] = IO.delay(42)
  val favLang: IO[String]    = IO.delay("Scala")
  val goalInLife: IO[String] = (meaningOfLife.showThread(), favLang.showThread()).mapN(
    (num, string) => s"my goal in life is $num and $string"
  )

  // parallelism on IOs
  // convert a sequential IO to parallel IO
  val parIO1: IO.Par[Int]    = Parallel[IO].parallel(meaningOfLife)
  val parIO2: IO.Par[String] = Parallel[IO].parallel(favLang)

  val goalInLifeParallel: IO.Par[String] =
    (parIO1, parIO2).mapN((num, string) => s"my goal in life is $num and $string")
  // turn back to sequential
  val goalInLife_v2: IO[String] = Parallel[IO].sequential(goalInLifeParallel)
  val goalInLife_v3: IO[String] = (meaningOfLife.showThread(), favLang.showThread()).parMapN(
    (num, string) => s"my goal in life is $num and $string"
  )

  // regarding failure:
  val aFailure: IO[String] = IO.raiseError(new RuntimeException("I can't do this!"))
  // compose success + failure
  val parallelWithFailure: IO[String] =
    (meaningOfLife.showThread(), aFailure.showThread()).parMapN((num, string) => s"$num $string")
  // compose failure + failure
  val anotherFailure: IO[String] = IO.raiseError(new RuntimeException("Second failure"))
  val twoFailures: IO[String] = (aFailure.showThread(), anotherFailure.showThread()).parMapN(_ + _)
  // the first effect to fail gives the failure of the result
  val twoFailuresDelayed: IO[String] =
    (IO(Thread.sleep(1000)) >> aFailure.showThread(), anotherFailure.showThread()).parMapN(_ + _)

  override def run: IO[Unit] =
    twoFailuresDelayed.showThread().void
