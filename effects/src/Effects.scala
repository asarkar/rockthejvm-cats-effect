package effects

import scala.concurrent.Future
import scala.io.StdIn.readLine

object Effects:

  // pure functional programming
  // substitution
  def combine(a: Int, b: Int): Int = a + b

  val five: Int    = combine(2, 3)
  val five_v2: Int = 2 + 3
  val five_v3      = 5

  // referential transparency = can replace an expression with its value
  // as many times as we want without changing behavior

  // example: print to the console
  val printSomething: Unit    = println("Cats Effect")
  val printSomething_v2: Unit = () // not the same

  // example: change a variable
  var anInt                = 0
  val changingVar: Unit    = (anInt += 1)
  val changingVar_v2: Unit = () // not the same

  // side effects are inevitable for useful programs

  /*
    Effect types
    Properties:
    - type signature describes the kind of calculation that will be performed
    - type signature describes the VALUE that will be calculated
    - when side effects are needed, effect construction is separate from effect execution
   */

  /*
    example: Option is an effect type
    - describes a possibly absent value
    - computes a value of type A, if it exists
    - side effects are not needed
   */
  val anOption: Option[Int] = Option(42)

  /*
    example: Future is NOT an effect type
    - describes an asynchronous computation
    - computes a value of type A, if it's successful
    - side effect is required (allocating/scheduling a thread), execution is NOT separate from construction
   */

  import scala.concurrent.ExecutionContext.Implicits.global

  val aFuture: Future[Int] = Future(42)

  /*
  example: MyIO data type from the Monads lesson - it IS an effect type
  - describes any computation that might produce side effects
  - calculates a value of type A, if it's successful
  - side effects are required for the evaluation of () => A
    - YES, the creation of MyIO does NOT produce the side effects on construction
   */

  case class MyIO[A](unsafeRun: () => A):
    def map[B](f: A => B): MyIO[B] =
      MyIO(() => f(unsafeRun()))

    def flatMap[B](f: A => MyIO[B]): MyIO[B] =
      MyIO(() => f(unsafeRun())).unsafeRun()

  /** Exercises
    *   1. An IO which returns the current time of the system
    *   2. An IO which measures the duration of a computation (hint: use ex 1)
    *   3. An IO which prints something to the console
    *   4. An IO which reads a line (a string) from the std input
    */
  // 1
  private val now: MyIO[Long] = MyIO(() => System.currentTimeMillis())

  // 2
  def measures[A](computation: MyIO[A]): MyIO[Long] =
    now.flatMap(start => computation.flatMap(_ => now).map(_ - start))

  // 3
  def putStrLn(line: String): MyIO[Unit] = MyIO(() => println(line))

  // 4
  val read: MyIO[String] = MyIO(() => readLine())
