package addition.prefixadder

import addition.prefixadder.common.{BrentKungAdder, KoggeStoneAdder, RippleCarry3Adder, RippleCarryAdder}
import chiseltest.formal.BoundedCheck
import formal.FormalSuite
import utest._

object AdderSpecTester extends FormalSuite {
  val width = 8
  // TODO: utest only support static test(cannot use map function), maybe we can try to use macro to implement this.
  val tests: Tests = Tests {
    test("ripple carry should pass") {
      verify(new RippleCarryAdder(width), Seq(BoundedCheck(1)))
    }
    test("ripple carry 3 fan-in should pass") {
      verify(new RippleCarry3Adder(width), Seq(BoundedCheck(1)))
    }
    test("kogge stone should pass") {
      verify(new KoggeStoneAdder(width), Seq(BoundedCheck(1)))
    }
    test("brent kung should pass") {
      verify(new BrentKungAdder(width), Seq(BoundedCheck(1)))
   }
  }
}
