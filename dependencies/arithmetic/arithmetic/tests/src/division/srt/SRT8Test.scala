package division.srt.srt8

import chisel3._
import chiseltest._
import utest._

import scala.util.Random

object SRT8Test extends TestSuite with ChiselUtestTester {
  def tests: Tests = Tests {
    test("SRT8 should pass") {
      def testcase(width: Int, x: Int, d: Int): Unit = {
        // parameters
        val radixLog2: Int = 3
        val n:         Int = width
        val m:         Int = n - 1
        val p:         Int = Random.nextInt(m - radixLog2 + 1) //order to offer guardwidth
        val q:         Int = Random.nextInt(m - radixLog2 + 1)

//        val dividend: BigInt = BigInt("fffffff0", 16) + x
        val dividend: BigInt = x
        val divisor:  BigInt = d
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
        val zeroHeadDivisor:   Int = m - zeroCheck(divisor)
        val needComputerWidth: Int = zeroHeadDivisor - zeroHeadDividend + 1 + radixLog2 - 1
        val noguard:           Boolean = needComputerWidth % radixLog2 == 0
        val guardWidth:        Int = if (noguard) 0 else 3 - needComputerWidth % 3
        val counter:           Int = (needComputerWidth + guardWidth) / radixLog2
        if ((divisor == 0) || (divisor > dividend) || (needComputerWidth <= 0))
          return
        val quotient:               BigInt = dividend / divisor
        val remainder:              BigInt = dividend % divisor
        val leftShiftWidthDividend: Int = zeroHeadDividend - guardWidth + 2
        val leftShiftWidthDivider:  Int = zeroHeadDivisor

        val dividendAppend = dividend % 4
        val appendWidth = if (leftShiftWidthDividend < 0) {
          -leftShiftWidthDividend
        } else 0

        testCircuit(
          new SRT8(n, n, n),
          Seq(chiseltest.internal.NoThreadingAnnotation, chiseltest.simulator.WriteVcdAnnotation)
        ) { dut: SRT8 =>
          dut.clock.setTimeout(0)
          dut.input.valid.poke(true.B)
          dut.input.bits.dividend.poke((dividend << leftShiftWidthDividend).U)
          dut.input.bits.divider.poke((divisor << leftShiftWidthDivider).U)
          dut.input.bits.counter.poke(counter.U)
          dut.clock.step()
          dut.input.valid.poke(false.B)
          var flag = false
          for (a <- 1 to 1000 if !flag) {
            if (dut.output.valid.peek().litValue == 1) {
              flag = true

              def printvalue(): Unit = {
                println("SRT8 error!")
                println("leftShiftWidthDividend  = %d ".format(leftShiftWidthDividend))
                println("%d / %d = %d --- %d".format(dividend, divisor, quotient, remainder))
                println(
                  "%d / %d = %d --- %d".format(
                    dividend,
                    divisor,
                    dut.output.bits.quotient.peek().litValue,
                    dut.output.bits.reminder.peek().litValue >> zeroHeadDivisor
                  )
                )
              }

              def check = {
                if (
                  (dut.output.bits.quotient
                    .peek()
                    .litValue == quotient) || (dut.output.bits.reminder.peek().litValue >> zeroHeadDivisor == remainder)
                ) {} else {
                  printvalue
                }
              }
              check
              utest.assert(dut.output.bits.quotient.peek().litValue == quotient)
              utest.assert(dut.output.bits.reminder.peek().litValue >> zeroHeadDivisor == remainder)
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
        for (j <- 1 to i - 1) {
          testcase(4, i, j)
        }
      }

    }
  }
}
