package effects

import cats.Traverse
import cats.effect.IO
import cats.effect.IOApp

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Random

object IOTraversal extends IOApp.Simple:

  import scala.concurrent.ExecutionContext.Implicits.global

  def heavyComputation(string: String): Future[Int] = Future:
    Thread.sleep(Random.nextInt(1000))
    string.split(" ").length

  val workLoad: List[String] =
    List("I quite like CE", "Scala is great", "looking forward to some awesome stuff")

  def clunkyFutures(): Unit =
    val futures: List[Future[Int]] = workLoad.map(heavyComputation)
    // Future[List[Int]] would be hard to obtain
    futures.foreach(_.foreach(println))

  val listTraverse: Traverse[List] = Traverse[List]

  def traverseFutures(): Unit =
    // traverse
    val singleFuture: Future[List[Int]] = listTraverse.traverse(workLoad)(heavyComputation)
    // ^^ this stores ALL the results
    singleFuture.foreach(println)

  import utils.Threads.showThread

  // traverse for IO
  def computeAsIO(string: String): IO[Int] =
    IO.sleep(Random.nextInt(1000).millis)
      .map(_ => string.split(" ").length)
      .showThread()

  val ios: List[IO[Int]]      = workLoad.map(computeAsIO)
  val singleIO: IO[List[Int]] = listTraverse.traverse(workLoad)(computeAsIO)

  // parallel traversal
  import cats.syntax.parallel.catsSyntaxParallelTraverse1
  val parallelSingleIO: IO[List[Int]] = workLoad.parTraverse(computeAsIO)

  // Exercises
  // hint: use Traverse API
  def sequence[A](ios: List[IO[A]]): IO[List[A]] =
    // def traverse[G[_]: Applicative, A, B](fa: F[A])(f: A => G[B]): G[F[B]]
    // G is IO, F is List
    summon[Traverse[List]].traverse(ios)(x => x)

  // hard version
  def sequence2[F[_]: Traverse, A](ios: F[IO[A]]): IO[F[A]] =
    Traverse[F].traverse(ios)(x => x)

  import cats.effect.IO.catsSyntaxParallelSequence1
  // parallel version
  def parSequence3[F[_]: Traverse, A](ios: F[IO[A]]): IO[F[A]] =
    ios.parSequence

  override def run: IO[Unit] =
    parSequence3(ios).map(_.sum).showThread().void
