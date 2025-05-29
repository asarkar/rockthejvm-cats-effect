package concurrency

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import concurrency.Fibers.processResultsFromFiber
import concurrency.Fibers.timeout
import concurrency.Fibers.tupleIOs
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe

import scala.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt

class FibersSpec extends AnyFunSpec:
  describe("runs an IO"):
    it("success"):
      val success = IO.pure(42)
      processResultsFromFiber(success).unsafeRunSync() shouldBe 42

    it("failure"):
      val failure = IO.raiseError(new RuntimeException())
      assertThrows[RuntimeException](processResultsFromFiber(failure).unsafeRunSync())

    it("cancellation"):
      val task = startAndThenCancel(IO(42))
      processResultsFromFiber(task).unsafeRunSync() shouldBe true

  describe("runs two IOs"):
    it("success"):
      val success1 = IO.pure(41)
      val success2 = IO.pure(42)
      tupleIOs(success1, success2).unsafeRunSync() shouldBe (41, 42)

    it("failure"):
      val success = IO.pure(42)
      val failure = IO.raiseError(new RuntimeException())
      assertThrows[RuntimeException](tupleIOs(success, failure).unsafeRunSync())

    it("cancellation"):
      val success = IO.pure(42)
      val task    = startAndThenCancel(success)
      tupleIOs(success, startAndThenCancel(task)).unsafeRunSync() shouldBe (42, true)

  describe("times out"):
    it("timeout"):
      val longComputation = IO.sleep(500.millis)
      assertThrows[TimeoutException](timeout(longComputation, 100.millis).unsafeRunSync())

  def startAndThenCancel[A](io: IO[A]): IO[Boolean] =
    for
      fib    <- io.flatTap(_ => IO.sleep(500.millis)).start
      _      <- fib.cancel
      result <- fib.join
    yield result.isCanceled
