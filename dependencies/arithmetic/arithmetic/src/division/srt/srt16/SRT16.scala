package division.srt.srt16

import division.srt._
import chisel3._
import chisel3.util._
import utils.leftShift

/** RSRT16 with Two SRT4 Overlapped Stages
  * n>=7
  * Reuse parameters, OTF and QDS of srt4
  */
class SRT16(
  dividendWidth:  Int,
  dividerWidth:   Int,
  n:              Int, // the longest width
  radixLog2:      Int = 2,
  a:              Int = 2,
  dTruncateWidth: Int = 4,
  rTruncateWidth: Int = 4)
    extends Module {
  val guardBitWidth = 3
  val xLen:    Int = dividendWidth + radixLog2 + 1 + guardBitWidth
  val ohWidth: Int = 2 * a + 1
  val rWidth:  Int = 1 + radixLog2 + rTruncateWidth

  // IO
  val input = IO(Flipped(DecoupledIO(new SRTInput(dividendWidth, dividerWidth, n, 4))))
  val output = IO(ValidIO(new SRTOutput(dividerWidth, dividendWidth)))

  val partialReminderCarryNext, partialReminderSumNext = Wire(UInt(xLen.W))
  val dividerNext = Wire(UInt(dividerWidth.W))
  val counterNext = Wire(UInt(log2Ceil(n).W))
  val quotientNext, quotientMinusOneNext = Wire(UInt(n.W))

  // Control
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

  // 5*CSA32  SRT16 <- SRT4 + SRT4*5 /SRT16 -> CSA53+CSA32
  val dividerMap = VecInit((-2 to 2).map {
    case -2 => divisorExtended << 1
    case -1 => divisorExtended
    case 0  => 0.U
    case 1  => Fill(1 + radixLog2, 1.U(1.W)) ## ~divisorExtended
    case 2  => Fill(radixLog2, 1.U(1.W)) ## ~(divisorExtended << 1)
  })
  val csa0InWidth = rWidth + radixLog2 + 1
  val csaIn1 = partialReminderSum.head(csa0InWidth)
  val csaIn2 = partialReminderCarry.head(csa0InWidth)

  val csa1 = addition.csa.c32(VecInit(csaIn1, csaIn2, dividerMap(0).head(csa0InWidth))) // -2  csain 10bit
  val csa2 = addition.csa.c32(VecInit(csaIn1, csaIn2, dividerMap(1).head(csa0InWidth))) // -1
  val csa3 = addition.csa.c32(VecInit(csaIn1, csaIn2, dividerMap(2).head(csa0InWidth))) // 0
  val csa4 = addition.csa.c32(VecInit(csaIn1, csaIn2, dividerMap(3).head(csa0InWidth))) // 1
  val csa5 = addition.csa.c32(VecInit(csaIn1, csaIn2, dividerMap(4).head(csa0InWidth))) // 2

  // qds
  val tables:         Seq[Seq[Int]] = SRTTable(1 << radixLog2, a, dTruncateWidth, rTruncateWidth).tablesToQDS
  val partialDivider: UInt = dividerNext.head(dTruncateWidth)(dTruncateWidth - 2, 0)
  val qdsOH0: UInt =
    QDS(rWidth, ohWidth, dTruncateWidth - 1, tables)(
      partialReminderSum.head(rWidth),
      partialReminderCarry.head(rWidth),
      partialDivider
    ) // q_j+1 oneHot

  def qds(a: Vec[UInt]): UInt = {
    QDS(rWidth, ohWidth, dTruncateWidth - 1, tables)(
      leftShift(a(1), radixLog2).head(rWidth),
      leftShift(a(0), radixLog2 + 1).head(rWidth),
      partialDivider
    )
  }
  //  q_j+2 oneHot precompute
  val qds1SelectedQuotientOH: UInt = qds(csa1) // -2
  val qds2SelectedQuotientOH: UInt = qds(csa2) // -1
  val qds3SelectedQuotientOH: UInt = qds(csa3) // 0
  val qds4SelectedQuotientOH: UInt = qds(csa4) // 1
  val qds5SelectedQuotientOH: UInt = qds(csa5) // 2

  val qds1SelectedQuotientOHMap = VecInit((-2 to 2).map {
    case -2 => qds1SelectedQuotientOH
    case -1 => qds2SelectedQuotientOH
    case 0  => qds3SelectedQuotientOH
    case 1  => qds4SelectedQuotientOH
    case 2  => qds5SelectedQuotientOH
  })

  val qdsOH1 = Mux1H(qdsOH0, qds1SelectedQuotientOHMap) // q_j+2 oneHot
  val qds0sign = qdsOH0(ohWidth - 1, ohWidth / 2 + 1).orR
  val qds1sign = qdsOH1(ohWidth - 1, ohWidth / 2 + 1).orR

  val csa0Out = addition.csa.c32(
    VecInit(
      partialReminderSum.head(xLen),
      partialReminderCarry.head(xLen - 1) ## qds0sign,
      Mux1H(qdsOH0, dividerMap)
    )
  )
  val csa1Out = addition.csa.c32(
    VecInit(
      leftShift(csa0Out(1), radixLog2).head(xLen),
      leftShift(csa0Out(0), radixLog2 + 1).head(xLen - 1) ## qds1sign,
      Mux1H(qdsOH1, dividerMap)
    )
  )

  // On-The-Fly conversion
  //  todo?: OTF input: Q, QM1, (q1 << 2 + q2) output: Q,QM1
  val otf0 = OTF(radixLog2, n, ohWidth)(quotient, quotientMinusOne, qdsOH0)
  val otf1 = OTF(radixLog2, n, ohWidth)(otf0(0), otf0(1), qdsOH1)

  dividerNext := Mux(input.fire, input.bits.divider, divider)
  counterNext := Mux(input.fire, input.bits.counter, counter - 1.U)
  quotientNext := Mux(input.fire, 0.U, otf1(0))
  quotientMinusOneNext := Mux(input.fire, 0.U, otf1(1))
  partialReminderSumNext := Mux(input.fire, input.bits.dividend, csa1Out(1) << radixLog2)
  partialReminderCarryNext := Mux(input.fire, 0.U, csa1Out(0) << radixLog2 + 1)
}
