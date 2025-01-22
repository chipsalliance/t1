package addition.csa

import addition.csa.common.{CSACompressor2_2, CSACompressor3_2, CSACompressor5_3}
import chiseltest.formal.BoundedCheck
import formal.FormalSuite
import utest._

object CSASpecTester extends FormalSuite {
  def tests: Tests = Tests {
    test("CSA53") {
      verify(new CarrySaveAdder(CSACompressor5_3, 1, true), Seq(BoundedCheck(1)))
    }
    test("CSA32") {
      verify(new CarrySaveAdder(CSACompressor3_2, 1, true), Seq(BoundedCheck(1)))
    }
    test("CSA22") {
      verify(new CarrySaveAdder(CSACompressor2_2, 1, true), Seq(BoundedCheck(1)))
    }
  }
}
