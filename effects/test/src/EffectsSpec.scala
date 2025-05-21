import Effects.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.be
import org.scalatest.matchers.should.Matchers.should

import java.time.Duration

class EffectsSpec extends AnyFunSpec:

  it("measures the duration of a computation"):
    val actual: MyIO[Long] = measures(MyIO(() => Thread.sleep(Duration.ofSeconds(1))))
    actual.unsafeRun() should be >= 1L
