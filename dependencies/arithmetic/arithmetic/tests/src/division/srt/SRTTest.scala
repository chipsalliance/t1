package division.srt

import chisel3._
import chiseltest._
import utest._

import scala.util.Random

object SRTTest extends TestSuite with ChiselUtestTester {
  def tests: Tests = Tests {
    test("SRT should pass") {
      def testcase(n:              Int = 64,
                   radixLog2:      Int = 4,
                   a:              Int = 2,
                   dTruncateWidth: Int = 4,
                   rTruncateWidth: Int = 4): Unit ={
        //tips
        println("SRT%d(width = %d, a = %d, dTruncateWidth = %d, rTruncateWidth = %d) should pass ".format(
          1 << radixLog2 , n , a, dTruncateWidth, rTruncateWidth))
        // parameters
        val m: Int = n - 1
        val p: Int = Random.nextInt(m - radixLog2 +1) //order to offer guardwidth
        val q: Int = Random.nextInt(m - radixLog2 +1)
        val dividend: BigInt = BigInt(p, Random)
        val divider: BigInt = BigInt(q, Random)
        //        val dividend: BigInt = BigInt("65")
        //        val divider: BigInt = BigInt("1")
        def zeroCheck(x: BigInt): Int = {
          var flag = false
          var k: Int = m
          while (!flag && (k >= -1)) {
            flag = ((BigInt(1) << k) & x) != 0
            k = k - 1
          }
          k + 1
        }
        val zeroHeadDividend: Int = m - zeroCheck(dividend)
        val zeroHeadDivider: Int = m - zeroCheck(divider)
        val needComputerWidth: Int = zeroHeadDivider - zeroHeadDividend + 1 + (if(radixLog2 == 4) 2 else radixLog2) -1
        val noguard: Boolean =  needComputerWidth % radixLog2 == 0
        val guardWidth: Int =  if (noguard) 0 else radixLog2 - needComputerWidth % radixLog2
        val counter: Int = (needComputerWidth + guardWidth) / radixLog2
        if ((divider == 0) || (divider > dividend) || (needComputerWidth <= 0))
          return
        val quotient: BigInt = dividend / divider
        val remainder: BigInt = dividend % divider
        val leftShiftWidthDividend: Int = zeroHeadDividend - guardWidth
        val leftShiftWidthDivider: Int = zeroHeadDivider
//                println("dividend = %8x, dividend = %d ".format(dividend, dividend))
//                println("divider  = %8x, divider  = %d".format(divider, divider))
//                println("zeroHeadDividend  = %d,  dividend << zeroHeadDividend = %d".format(zeroHeadDividend, dividend << leftShiftWidthDividend))
//                println("zeroHeadDivider   = %d,  divider << zeroHeadDivider  = %d".format(zeroHeadDivider, divider << leftShiftWidthDivider))
//                println("quotient   = %d,  remainder  = %d".format(quotient, remainder))
//                println("counter   = %d, needComputerWidth = %d".format(counter, needComputerWidth))
        // test
        testCircuit(new SRT(n, n, n, radixLog2, a, dTruncateWidth, rTruncateWidth),
          Seq(chiseltest.internal.NoThreadingAnnotation,
            chiseltest.simulator.WriteVcdAnnotation)) {
          dut: SRT =>
            dut.clock.setTimeout(0)
            dut.input.valid.poke(true.B)
            dut.input.bits.dividend.poke((dividend << leftShiftWidthDividend).U)
            dut.input.bits.divider.poke((divider << leftShiftWidthDivider).U)
            dut.input.bits.counter.poke(counter.U)
            dut.clock.step()
            dut.input.valid.poke(false.B)
            var flag = false
            for (a <- 1 to 1000 if !flag) {
              if (dut.output.valid.peek().litValue == 1) {
                flag = true
                println(dut.output.bits.quotient.peek().litValue)
                println(dut.output.bits.reminder.peek().litValue >> zeroHeadDivider)
                utest.assert(dut.output.bits.quotient.peek().litValue == quotient)
                utest.assert(dut.output.bits.reminder.peek().litValue >> zeroHeadDivider == remainder)
              }
              dut.clock.step()
            }
            utest.assert(flag)
            dut.clock.step(scala.util.Random.nextInt(5))
        }
      }
//      testcase(64)
//      for( i <- 1 to 50){
//        testcase(n = 64, radixLog2 = 3, a = 7, dTruncateWidth = 4, rTruncateWidth = 4)
//      }
    }
  }
}