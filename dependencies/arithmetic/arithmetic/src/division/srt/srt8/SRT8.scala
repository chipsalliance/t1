package division.srt.srt8

import division.srt._
import division.srt.SRTTable
import chisel3._
import chisel3.util._
import utils.leftShift

/** SRT8
  * 1/2 <= d < 1, 1/2 < rho <=1, 0 < q  < 2
  * radix = 8
  * a = 7, {-7, ... ,-2, -1, 0, 1, 2, ... 7},
  * dTruncateWidth = 4, rTruncateWidth = 4
  * y^（xxxx.xxxx）, d^（0.1xxx）
  * table from SRTTable
  * -129/16 < y^ < 127/16
  * floor((-r*rho - 2^-t)_t) <= y^ <= floor((r*rho - ulp)_t)
  */

class SRT8(
  dividendWidth:  Int,
  dividerWidth:   Int,
  n:              Int, // the longest width
  radixLog2:      Int = 3,
  a:              Int = 7,
  dTruncateWidth: Int = 4,
  rTruncateWidth: Int = 4)
    extends Module {

  val guardBitWidth = 2
  val xLen: Int = dividendWidth + radixLog2 + 1 + guardBitWidth

  // IO
  val input = IO(Flipped(DecoupledIO(new SRTInput(dividendWidth, dividerWidth, n, 3))))
  val output = IO(ValidIO(new SRTOutput(dividerWidth, dividendWidth)))

  val partialReminderCarryNext, partialReminderSumNext = Wire(UInt(xLen.W))
  val quotientNext, quotientMinusOneNext = Wire(UInt(n.W))
  val dividerNext = Wire(UInt(dividerWidth.W))
  val counterNext = Wire(UInt(log2Ceil(n).W))

  // Control
  // sign of select quotient, true -> negative, false -> positive
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

  val divisorExtended = Cat(divider, 0.U(guardBitWidth.W))
  val remainderNoCorrect: UInt = partialReminderSum + partialReminderCarry
  val remainderCorrect: UInt =
    partialReminderSum + partialReminderCarry + (divisorExtended << radixLog2)
  val needCorrect: Bool = remainderNoCorrect(xLen - 1).asBool
  output.bits.reminder := Mux(needCorrect, remainderCorrect, remainderNoCorrect)(xLen - 2, radixLog2 + guardBitWidth)
  output.bits.quotient := Mux(needCorrect, quotientMinusOne, quotient)

  val rWidth: Int = 1 + radixLog2 + rTruncateWidth
  val tables: Seq[Seq[Int]] = SRTTable(1 << radixLog2, a, dTruncateWidth, rTruncateWidth).tablesToQDS

  val ohWidth: Int = a match {
    case 7 => 10
    case 6 => 8
    case 5 => 8
    case 4 => 8
  }
  // qds
  val selectedQuotientOH: UInt =
    QDS(rWidth, ohWidth, dTruncateWidth - 1, tables, a)(
      partialReminderSum.head(rWidth),
      partialReminderCarry.head(rWidth),
      dividerNext.head(dTruncateWidth)(dTruncateWidth - 2, 0) //.1********* -> 1*** -> ***
    )
  // On-The-Fly conversion
  val otf = OTF(radixLog2, n, ohWidth, a)(quotient, quotientMinusOne, selectedQuotientOH)

  val dividerLMap = VecInit((-2 to 2).map {
    case -2 => divisorExtended << 1 // -2
    case -1 => divisorExtended // -1
    case 0  => 0.U //  0
    case 1  => Fill(1 + radixLog2, 1.U(1.W)) ## ~divisorExtended // 1
    case 2  => Fill(radixLog2, 1.U(1.W)) ## ~(divisorExtended << 1) // 2
  })

  if (a == 7) {
    val qHigh:    UInt = selectedQuotientOH(9, 5)
    val qLow:     UInt = selectedQuotientOH(4, 0)
    val qdsSign0: Bool = qHigh.head(2).orR
    val qdsSign1: Bool = qLow.head(2).orR
    // csa for SRT8 -> CSA32+CSA32
    val dividerHMap = VecInit((-2 to 2).map {
      case -2 => divisorExtended << 3 // -8
      case -1 => divisorExtended << 2 // -4
      case 0  => 0.U //  0
      case 1  => Fill(2, 1.U(1.W)) ## ~(divisorExtended << 2) // 4
      case 2  => Fill(1, 1.U(1.W)) ## ~(divisorExtended << 3) // 8
    })
    val csa0 = addition.csa.c32(
      VecInit(
        partialReminderSum.head(xLen),
        partialReminderCarry.head(xLen - 1) ## qdsSign0,
        Mux1H(qHigh, dividerHMap)
      )
    )
    val csa1 = addition.csa.c32(
      VecInit(
        csa0(1).head(xLen),
        leftShift(csa0(0), 1).head(xLen - 1) ## qdsSign1,
        Mux1H(qLow, dividerLMap)
      )
    )
    partialReminderSumNext := Mux(input.fire, input.bits.dividend, csa1(1) << radixLog2)
    partialReminderCarryNext := Mux(input.fire, 0.U, csa1(0) << 1 + radixLog2)
  } else if (a == 6) {
    val qHigh:    UInt = selectedQuotientOH(7, 5)
    val qLow:     UInt = selectedQuotientOH(4, 0)
    val qdsSign0: Bool = qHigh.head(1).asBool
    val qdsSign1: Bool = qLow.head(2).orR

    // csa for SRT8 -> CSA32+CSA32
    val dividerHMap = VecInit((-1 to 1).map {
      case -1 => divisorExtended << 2 // -4
      case 0  => 0.U //  0
      case 1  => Fill(2, 1.U(1.W)) ## ~(divisorExtended << 2) // 4
    })
    val csa0 = addition.csa.c32(
      VecInit(
        partialReminderSum.head(xLen),
        partialReminderCarry.head(xLen - 1) ## qdsSign0,
        Mux1H(qHigh, dividerHMap)
      )
    )
    val csa1 = addition.csa.c32(
      VecInit(
        csa0(1).head(xLen),
        leftShift(csa0(0), 1).head(xLen - 1) ## qdsSign1,
        Mux1H(qLow, dividerLMap)
      )
    )
    partialReminderSumNext := Mux(input.fire, input.bits.dividend, csa1(1) << radixLog2)
    partialReminderCarryNext := Mux(input.fire, 0.U, csa1(0) << 1 + radixLog2)
  } else if (a == 5) {
    val qHigh:    UInt = selectedQuotientOH(7, 5)
    val qLow:     UInt = selectedQuotientOH(4, 0)
    val qdsSign0: Bool = qHigh.head(1).asBool
    val qdsSign1: Bool = qLow.head(2).orR

    // csa for SRT8 -> CSA32+CSA32
    val dividerHMap = VecInit((-1 to 1).map {
      case -1 => divisorExtended << 2 // -4
      case 0  => 0.U //  0
      case 1  => Fill(2, 1.U(1.W)) ## ~(divisorExtended << 2) // 4
    })
    val csa0 = addition.csa.c32(
      VecInit(
        partialReminderSum.head(xLen),
        partialReminderCarry.head(xLen - 1) ## qdsSign0,
        Mux1H(qHigh, dividerHMap)
      )
    )
    val csa1 = addition.csa.c32(
      VecInit(
        csa0(1).head(xLen),
        leftShift(csa0(0), 1).head(xLen - 1) ## qdsSign1,
        Mux1H(qLow, dividerLMap)
      )
    )
    partialReminderSumNext := Mux(input.fire, input.bits.dividend, csa1(1) << radixLog2)
    partialReminderCarryNext := Mux(input.fire, 0.U, csa1(0) << 1 + radixLog2)
  } else if (a == 4) {
    val qHigh:    UInt = selectedQuotientOH(7, 5)
    val qLow:     UInt = selectedQuotientOH(4, 0)
    val qdsSign0: Bool = qHigh.head(1).asBool
    val qdsSign1: Bool = qLow.head(2).orR

    // csa for SRT8 -> CSA32+CSA32
    val dividerHMap = VecInit((-1 to 1).map {
      case -1 => divisorExtended << 1 // -2
      case 0  => 0.U //  0
      case 1  => Fill(radixLog2, 1.U(1.W)) ## ~(divisorExtended << 1) // 2
    })
    val csa0 = addition.csa.c32(
      VecInit(
        partialReminderSum.head(xLen),
        partialReminderCarry.head(xLen - 1) ## qdsSign0,
        Mux1H(qHigh, dividerHMap)
      )
    )
    val csa1 = addition.csa.c32(
      VecInit(
        csa0(1).head(xLen),
        leftShift(csa0(0), 1).head(xLen - 1) ## qdsSign1,
        Mux1H(qLow, dividerLMap)
      )
    )
    partialReminderSumNext := Mux(input.fire, input.bits.dividend, csa1(1) << radixLog2)
    partialReminderCarryNext := Mux(input.fire, 0.U, csa1(0) << 1 + radixLog2)
  }

  dividerNext := Mux(input.fire, input.bits.divider, divider)
  counterNext := Mux(input.fire, input.bits.counter, counter - 1.U)
  quotientNext := Mux(input.fire, 0.U, otf(0))
  quotientMinusOneNext := Mux(input.fire, 0.U, otf(1))
}
