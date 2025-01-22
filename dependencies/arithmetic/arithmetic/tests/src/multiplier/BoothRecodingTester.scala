package multiplier

import chisel3._
import chiseltest._
import chisel3.util.BitPat
import utest._

object BoothRecodingTester extends TestSuite with ChiselUtestTester {
  val tests: Tests = Tests {
    test("encoding len") {
      testCircuit(new Booth(16)(8)) { dut =>
        dut.input.poke(7.U)
        dut.output(0).expect(7.S)
        dut.output(1).expect(0.S)
        dut.output(2).expect(0.S)
      }
    }
  }
}
