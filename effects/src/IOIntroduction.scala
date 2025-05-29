package effects

import cats.effect.IO

import scala.io.StdIn

object IOIntroduction:
  // IO
  val ourFirstIO: IO[Int] = IO.pure(42) // arg that should not have side effects
  val aDelayedIO: IO[Int] = IO.delay:
    println("I'm producing an integer")
    54

  val aDelayedIO_v2: IO[Int] = IO: // apply == delay
    println("I'm producing an integer")
    54

  // map, flatMap
  val improvedMeaningOfLife: IO[Int] = ourFirstIO.map(_ * 2)
  val printedMeaningOfLife: IO[Unit] = ourFirstIO.flatMap(mol => IO.delay(println(mol)))

  def smallProgram(): IO[Unit] = for {
    line1 <- IO(StdIn.readLine())
    line2 <- IO(StdIn.readLine())
    _     <- IO.delay(println(line1 + line2))
  } yield ()

  // mapN - combine IO effects as tuples
  import cats.syntax.apply.catsSyntaxTuple2Semigroupal

  val combinedMeaningOfLife: IO[Int] = (ourFirstIO, improvedMeaningOfLife).mapN(_ + _)

  def smallProgram_v2(): IO[Unit] =
    (IO(StdIn.readLine()), IO(StdIn.readLine())).mapN(_ + _).map(println)

  // 1 - sequence two IOs and take the result of the LAST one
  // hint: use flatMap
  def sequenceTakeLast[A, B](ioa: IO[A], iob: IO[B]): IO[B] =
    ioa.flatMap(_ => iob)

  def sequenceTakeLast2[A, B](ioa: IO[A], iob: IO[B]): IO[B] =
    ioa *> iob // andThen

  def sequenceTakeLast3[A, B](ioa: IO[A], iob: IO[B]): IO[B] =
    ioa >> iob // "andThen" with by-name call

    // 2 - sequence two IOs and take the result of the FIRST one
    // hint: use flatMap
  def sequenceTakeFirst[A, B](ioa: IO[A], iob: IO[B]): IO[A] =
    sequenceTakeLast(iob, ioa)

  def sequenceTakeFirst2[A, B](ioa: IO[A], iob: IO[B]): IO[A] =
    ioa <* iob

  // 3 - repeat an IO effect forever
  // hint: use flatMap + recursion
  def forever[A](io: IO[A]): IO[A] =
    io.flatMap(_ => forever(io))

  def forever2[A](io: IO[A]): IO[A] =
    io >> forever2(io)

  def forever3[A](io: IO[A]): IO[A] =
    io *> forever3(io) // stack overflow!

  def forever4[A](io: IO[A]): IO[A] =
    io.foreverM // with tail recursion

  // 4 - convert an IO to a different type
  // hint: use map
  def convert[A, B](ioa: IO[A], value: B): IO[B] =
    ioa.map(_ => value)

  def convert2[A, B](ioa: IO[A], value: B): IO[B] =
    ioa.as(value)

  // 5 - discard value inside an IO, just return Unit
  def asUnit[A](ioa: IO[A]): IO[Unit] =
    convert(ioa, ())

  def asUnit2[A](ioa: IO[A]): IO[Unit] =
    ioa.void

  // 6 - fix stack recursion
  def sum(n: Int): Int =
    if (n <= 0) then 0
    else n + sum(n - 1)

  def sumIO(n: Int): IO[Int] =
    if n <= 0 then IO(0)
    else IO(n).flatMap(x => sumIO(n - 1).map(_ + x))

  // 7 (hard) - write a fibonacci IO that does NOT crash on recursion
  // hints: use recursion, ignore exponential complexity. use flatMap heavily
  def fibonacci(n: Int): IO[BigInt] =
    if (n < 2) then IO(1)
    else
      for
        last <- IO.defer(fibonacci(n - 1)) // same as .delay(...).flatten
        prev <- IO.defer(fibonacci(n - 2))
      yield last + prev
