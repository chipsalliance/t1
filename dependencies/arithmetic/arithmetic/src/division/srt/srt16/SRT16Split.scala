package division.srt.srt16

import division.srt._
import chisel3._
import chisel3.util._
import utils.leftShift

class SRT16Iter(
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
  val input = IO(Flipped(DecoupledIO(new DivIterIn(dividerWidth, xLen, n))))
  val resultOutput = IO(ValidIO(new SRTOutput(dividerWidth, dividendWidth)))
  val output = IO(Output(new DivIterOut(xLen)))
  val reqOTF = IO(Output(new OTFInput(n, ohWidth)))
  val respOTF = IO(Input(new OTFOutput(n)))

  val dividerNext = Wire(UInt(dividerWidth.W))
  val counterNext = Wire(UInt(log2Ceil(n).W))
  val quotientNext, quotientMinusOneNext = Wire(UInt(n.W))

  // Control
  val isLastCycle, enable: Bool = Wire(Bool())
  // State
  // because we need a CSA to minimize the critical path
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
  resultOutput.valid := occupied && isLastCycle
  input.ready := !occupied
  enable := input.fire || !isLastCycle

  val divisorExtended = Cat(divider, 0.U(guardBitWidth.W))
  val remainderNoCorrect: UInt = input.bits.partialSum + input.bits.partialCarry
  val remainderCorrect: UInt =
    input.bits.partialSum + input.bits.partialCarry + (divisorExtended << radixLog2)
  val needCorrect: Bool = remainderNoCorrect(xLen - 1).asBool

  resultOutput.bits.reminder := Mux(needCorrect, remainderCorrect, remainderNoCorrect)(xLen - 2, radixLog2 + guardBitWidth)
  resultOutput.bits.quotient := Mux(needCorrect, quotientMinusOne, quotient)

  // 5*CSA32  SRT16 <- SRT4 + SRT4*5 /SRT16 -> CSA53+CSA32
  val dividerMap = VecInit((-2 to 2).map {
    case -2 => divisorExtended << 1
    case -1 => divisorExtended
    case 0  => 0.U
    case 1  => Fill(1 + radixLog2, 1.U(1.W)) ## ~divisorExtended
    case 2  => Fill(radixLog2, 1.U(1.W)) ## ~(divisorExtended << 1)
  })
  val csa0InWidth = rWidth + radixLog2 + 1
  val csaIn1 = input.bits.partialSum.head(csa0InWidth)
  val csaIn2 = input.bits.partialCarry.head(csa0InWidth)

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
      input.bits.partialSum.head(rWidth),
      input.bits.partialCarry.head(rWidth),
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
      input.bits.partialSum.head(xLen),
      input.bits.partialCarry.head(xLen - 1) ## qds0sign,
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
  val otf1 = OTF(radixLog2, n, ohWidth)(respOTF.quotient, respOTF.quotientMinusOne, qdsOH1)

  reqOTF.quotient := quotient
  reqOTF.quotientMinusOne := quotientMinusOne
  reqOTF.selectedQuotientOH := qdsOH0

  dividerNext := Mux(input.fire, input.bits.divider, divider)
  counterNext := Mux(input.fire, input.bits.counter, counter - 1.U)
  quotientNext := Mux(input.fire, 0.U, otf1(0))
  quotientMinusOneNext := Mux(input.fire, 0.U, otf1(1))

  output.partialSum := csa1Out(1) << radixLog2
  output.partialCarry := csa1Out(0) << radixLog2 + 1
  output.isLastCycle := isLastCycle
}
class DivIterIn(width: Int, xLen: Int, n:Int) extends Bundle{
  val partialSum = UInt(xLen.W)
  val partialCarry = UInt(xLen.W)
  val divider = UInt(width.W)
  val counter = UInt(log2Ceil(n).W)
}

class DivIterOut(width: Int) extends Bundle{
  val partialSum = UInt(width.W)
  val partialCarry = UInt(width.W)
  val isLastCycle = Bool()
}

class SRT16Split(
                   dividendWidth:  Int,
                   dividerWidth:   Int,
                   n:              Int, // the longest width
                   radixLog2:      Int = 2,
                   a:              Int = 2,
                   dTruncateWidth: Int = 4,
                   rTruncateWidth: Int = 4) extends Module{
  val input = IO(Flipped(DecoupledIO(new SRTInput(dividendWidth, dividerWidth, n, 4))))
  val output = IO(ValidIO(new SRTOutput(dividerWidth, dividendWidth)))

  val guardBitWidth = 3
  val xLen: Int = dividendWidth + radixLog2 + 1 + guardBitWidth
  val ohWidth: Int = 2 * a + 1

  val iter = Module(new SRT16Iter(dividendWidth, dividerWidth, n, radixLog2, a, dTruncateWidth, rTruncateWidth))
  val otf = OTF(radixLog2, n, ohWidth)(iter.reqOTF.quotient, iter.reqOTF.quotientMinusOne, iter.reqOTF.selectedQuotientOH)

  val partialCarryNext, partialSumNext = Wire(UInt(xLen.W))
  val enable       = input.fire || !iter.output.isLastCycle

  partialSumNext   := Mux(input.fire, input.bits.dividend, iter.output.partialSum)
  partialCarryNext := Mux(input.fire, 0.U, iter.output.partialCarry)

  val partialCarry = RegEnable(partialCarryNext, 0.U(xLen.W), enable)
  val partialSum   = RegEnable(partialSumNext  , 0.U(xLen.W), enable)

  input.ready := iter.input.ready

  iter.input.valid := input.valid
  iter.input.bits.partialCarry := partialCarry
  iter.input.bits.partialSum   := partialSum
  iter.input.bits.divider := input.bits.divider
  iter.input.bits.counter := input.bits.counter

  iter.respOTF.quotient := otf(0)
  iter.respOTF.quotientMinusOne := otf(1)

  output := iter.resultOutput

}