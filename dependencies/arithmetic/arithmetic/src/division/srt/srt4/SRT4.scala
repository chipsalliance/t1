package division.srt.srt4

import division.srt._
import addition.csa.CarrySaveAdder
import addition.csa.common.CSACompressor3_2
import chisel3._
import chisel3.util._
import spire.math
import utils.leftShift

/** SRT4
  * 1/2 <= d < 1, 1/2 < rho <=1, 0 < q  < 2
  * radix = 4
  * a = 2, {-2, -1, 0, 1, -2},
  * dTruncateWidth = 4, rTruncateWidth = 8
  * y^（xxx.xxxx）, d^（0.1xxx）
  * -44/16 < y^ < 42/16
  * floor((-r*rho - 2^-t)_t) <= y^ <= floor((r*rho - ulp)_t)
  */

/** SRT4
  *
  * @param n the maximum result width
  * @param a digit set
  * @param dTruncateWidth TruncateWidth for divisor
  * @param rTruncateWidth TruncateWidth for residual fractional part
  */
class SRT4(
            dividendWidth:  Int,
            dividerWidth:   Int,
            n:              Int, // the longest width
            radixLog2:      Int = 2,
            a:              Int = 2,
            dTruncateWidth: Int = 4,
            rTruncateWidth: Int = 4)
  extends Module {
  val guardBitWidth = 1

  /** width for csa */
  val xLen: Int = dividendWidth + radixLog2 + 1 + guardBitWidth
  // IO
  val input = IO(Flipped(DecoupledIO(new SRTInput(dividendWidth, dividerWidth, n, 2))))
  val output = IO(ValidIO(new SRTOutput(dividerWidth, dividendWidth)))

  //rW[j]
  val partialReminderCarryNext, partialReminderSumNext = Wire(UInt(xLen.W))
  val quotientNext, quotientMinusOneNext = Wire(UInt(n.W))
  val dividerNext = Wire(UInt(dividerWidth.W))
  val counterNext = Wire(UInt(log2Ceil(n).W))

  // Control
  // sign of Cycle, true -> (counter === 0.U)
  val isLastCycle, enable: Bool = Wire(Bool())

  // State
  // because we need a CSA to minimize the critical path
  val partialReminderCarry = RegEnable(partialReminderCarryNext, 0.U(xLen.W), enable)
  val partialReminderSum = RegEnable(partialReminderSumNext, 0.U(xLen.W), enable)
  val divider = RegEnable(dividerNext, 0.U(dividerWidth.W), enable)
  val quotient = RegEnable(quotientNext, 0.U(n.W), enable)
  val quotientMinusOne = RegEnable(quotientMinusOneNext, 0.U(n.W), enable)
  val counter = RegEnable(counterNext, 0.U(log2Ceil(n).W), enable)

  val occupiedNext = Wire(Bool())
  val occupied = RegNext(occupiedNext, false.B)
  occupiedNext := input.fire || (!isLastCycle && occupied)

  //  Datapath
  //  according two adders
  isLastCycle := !counter.orR
  output.valid := occupied && isLastCycle
  input.ready := !occupied
  enable := input.fire || !isLastCycle

  /** divisor needs to be extended to be aligned with partialReminder */
  val divisorExtended = Cat(divider, 0.U(guardBitWidth.W))
  val remainderNoCorrect: UInt = partialReminderSum + partialReminderCarry

  /** partialReminderSum is r*W[j], so remainderCorrect = remainderNoCorrect + r*divisor */
  val remainderCorrect: UInt =
    partialReminderSum + partialReminderCarry + (divisorExtended << radixLog2)
  val needCorrect: Bool = remainderNoCorrect(xLen - 1).asBool

  output.bits.reminder := Mux(needCorrect, remainderCorrect, remainderNoCorrect)(xLen - 2, radixLog2 + guardBitWidth)
  output.bits.quotient := Mux(needCorrect, quotientMinusOne, quotient)

  /** width for truncated y */
  val rWidth: Int = 1 + radixLog2 + rTruncateWidth
  val tables: Seq[Seq[Int]] = SRTTable(1 << radixLog2, a, dTruncateWidth, rTruncateWidth).tablesToQDS
  // selected quotient encoding width
  val ohWidth: Int = a match {
    case 2 => 5
    case 3 => 6
  }

  /** QDS module whose output needs to be decoded */
  val selectedQuotientOH: UInt =
    QDS(rWidth, ohWidth, dTruncateWidth - 1, tables, a)(
      partialReminderSum.head(rWidth),
      partialReminderCarry.head(rWidth),
      dividerNext.head(dTruncateWidth)(dTruncateWidth - 2, 0) //.1********* -> 1*** -> ***
    )
  // On-The-Fly conversion
  val otf = OTF(radixLog2, n, ohWidth, a)(quotient, quotientMinusOne, selectedQuotientOH)

  val csa: Vec[UInt] =
    if (a == 2) { // a == 2
      // decode quotient oneHot and calculate -qd
      val dividerMap = VecInit((-2 to 2).map {
        case -2 => divisorExtended << 1
        case -1 => divisorExtended
        case 0  => 0.U
        case 1  => Fill(1 + radixLog2, 1.U(1.W)) ## ~divisorExtended
        case 2  => Fill(radixLog2, 1.U(1.W)) ## ~(divisorExtended << 1)
      })
      // qds Sing: if qdsOut = "b11000" or "10000" or "01000"
      // if q is positive, add one to partialReminderCarry in the least bit
      val qdsSign = selectedQuotientOH(ohWidth - 1, ohWidth / 2 + 1).orR

      addition.csa.c32(
        VecInit(
          partialReminderSum.head(xLen),
          partialReminderCarry.head(xLen - 1) ## qdsSign,
          Mux1H(selectedQuotientOH, dividerMap)
        )
      )
    } else { // a==3
      val qHigh = selectedQuotientOH(5, 3)
      val qLow = selectedQuotientOH(2, 0)
      val qds0Sign = qHigh.head(1)
      val qds1Sign = qLow.head(1)

      // csa
      val dividerHMap = VecInit((-1 to 1).map {
        case -1 => divisorExtended << 1 // -2
        case 0  => 0.U //  0
        case 1  => Fill(radixLog2, 1.U(1.W)) ## ~(divisorExtended << 1) // 2
      })
      val dividerLMap = VecInit((-1 to 1).map {
        case -1 => divisorExtended // -1
        case 0  => 0.U //  0
        case 1  => Fill(1 + radixLog2, 1.U(1.W)) ## ~divisorExtended // 1
      })
      val csa0 = addition.csa.c32(
        VecInit(
          partialReminderSum.head(xLen),
          partialReminderCarry.head(xLen - 1) ## qds0Sign,
          Mux1H(qHigh, dividerHMap)
        )
      )
      addition.csa.c32(
        VecInit(
          csa0(1).head(xLen),
          leftShift(csa0(0), 1).head(xLen - 1) ## qds1Sign,
          Mux1H(qLow, dividerLMap)
        )
      )
    }

  dividerNext := Mux(input.fire, input.bits.divider, divider)
  counterNext := Mux(input.fire, input.bits.counter, counter - 1.U)
  quotientNext := Mux(input.fire, 0.U, otf(0))
  quotientMinusOneNext := Mux(input.fire, 0.U, otf(1))
  partialReminderSumNext := Mux(input.fire, input.bits.dividend, csa(1) << radixLog2)
  partialReminderCarryNext := Mux(input.fire, 0.U, csa(0) << 1 + radixLog2)
}
