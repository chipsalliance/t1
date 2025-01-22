package division.srt.srt4

import chisel3._
import chiseltest._
import utest._
import scala.util.{Random}

object SRT4Test extends TestSuite with ChiselUtestTester {
  def tests: Tests = Tests {
    test("SRT4 should pass") {
      def testcase(width: Int, x: Int, d: Int): Unit = {
        // parameters
        val radixLog2: Int = 2
        val n:         Int = width
        val m:         Int = n - 1
        val p:         Int = Random.nextInt(m)
        val q:         Int = Random.nextInt(m)
//        val dividend: BigInt = BigInt("fffffff0", 16) + x
        val dividend: BigInt = x
        val divisor: BigInt = d
        def zeroCheck(x: BigInt): Int = {
          var flag = false
          var a: Int = m
          while (!flag && (a >= -1)) {
            flag = ((BigInt(1) << a) & x) != 0
            a = a - 1
          }
          a + 1
        }
        val zeroHeadDividend:  Int = m - zeroCheck(dividend)
        val zeroHeaddivisor:   Int = m - zeroCheck(divisor)
        val needComputerWidth: Int = zeroHeaddivisor - zeroHeadDividend + 1 + radixLog2 - 1
        val noguard:           Boolean = needComputerWidth % radixLog2 == 0
        val guardWidth:        Int = if (noguard) 0 else 2 - needComputerWidth % 2
        val counter:           Int = (needComputerWidth + 1) / 2
        if ((divisor == 0) || (divisor > dividend) || (needComputerWidth <= 0))
          return
        val quotient:               BigInt = dividend / divisor
        val remainder:              BigInt = dividend % divisor
        val leftShiftWidthDividend: Int = zeroHeadDividend - guardWidth + 1
        val leftShiftWidthdivisor:  Int = zeroHeaddivisor

        // test
        testCircuit(
          new SRT4(n, n, n),
          Seq(chiseltest.internal.NoThreadingAnnotation, chiseltest.simulator.WriteVcdAnnotation)
        ) { dut: SRT4 =>
          dut.clock.setTimeout(0)
          dut.input.valid.poke(true.B)
          dut.input.bits.dividend.poke((dividend << leftShiftWidthDividend).U)
          dut.input.bits.divider.poke((divisor << leftShiftWidthdivisor).U)
          dut.input.bits.counter.poke(counter.U)
          dut.clock.step()
          dut.input.valid.poke(false.B)
          var flag = false
          for (a <- 1 to 1000 if !flag) {
            if (dut.output.valid.peek().litValue == 1) {
              flag = true

              def printvalue(): Unit = {
                println("SRT4 error!")
                println("zeroHeadDividend_ex   = %d".format(zeroHeadDividend))
                println("zeroHeaddivisor_ex   = %d".format(zeroHeaddivisor))
                println("guardWidth = " + guardWidth)
                println("leftShiftWidthDividend  = %d ".format(leftShiftWidthDividend))
                println("counter_ex   = %d, needComputerWidth_ex = %d".format(counter, needComputerWidth))
                println("%d / %d = %d --- %d".format(dividend, divisor, quotient, remainder))
                println(
                  "%d / %d = %d --- %d".format(
                    dividend,
                    divisor,
                    dut.output.bits.quotient.peek().litValue,
                    dut.output.bits.reminder.peek().litValue >> zeroHeaddivisor
                  )
                )
              }

              def check = {
                if (
                  (dut.output.bits.quotient
                    .peek()
                    .litValue == quotient) || (dut.output.bits.reminder.peek().litValue >> zeroHeaddivisor == remainder)
                ) {} else {
                  printvalue
                }
              }
              check
              utest.assert(dut.output.bits.quotient.peek().litValue == quotient)
              utest.assert(dut.output.bits.reminder.peek().litValue >> zeroHeaddivisor == remainder)
            }
            dut.clock.step()
          }
          utest.assert(flag)
          dut.clock.step(scala.util.Random.nextInt(10))
        }
      }

//      for (i <- 0 to 15) {
//        for (j <- 1 to 16) {
//          testcase(32, i, j)
//        }
//      }

            for (i <- 2 to 15) {
              for (j <- 1 to i-1) {
                testcase(4, i, j)
              }
            }

    }
  }
}
