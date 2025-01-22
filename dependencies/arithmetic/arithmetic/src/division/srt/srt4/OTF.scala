package division.srt.srt4

import division.srt._
import chisel3._
import chisel3.util.Mux1H

class OTF(radixLog2: Int, qWidth: Int, ohWidth: Int, a: Int) extends Module {
  val input = IO(Input(new OTFInput(qWidth, ohWidth)))
  val output = IO(Output(new OTFOutput(qWidth)))

  val radix: Int = 1 << radixLog2
  // datapath
  // q_j+1 in this circle, only for srt4
  // val cShiftQ:  Bool = qNext >= 0.U
  // val cShiftQM: Bool = qNext <=  0.U
  val qNext: UInt = Wire(UInt(3.W))
  val cShiftQ, cShiftQM = Wire(Bool())

  if (a == 2) {
    qNext := Mux1H(
      Seq(
        input.selectedQuotientOH(0) -> "b110".U, //-2
        input.selectedQuotientOH(1) -> "b111".U, //-1
        input.selectedQuotientOH(2) -> "b000".U, // 0
        input.selectedQuotientOH(3) -> "b001".U, // 1
        input.selectedQuotientOH(4) -> "b010".U // 2
      )
    )
    cShiftQ := input.selectedQuotientOH(ohWidth - 1, ohWidth / 2).orR
    cShiftQM := input.selectedQuotientOH(ohWidth / 2, 0).orR
  } else if (a == 3) {
    qNext := Mux1H(
      Seq(
        input.selectedQuotientOH(0) -> "b111".U, //-1
        input.selectedQuotientOH(1) -> "b000".U, // 0
        input.selectedQuotientOH(2) -> "b001".U // 1
      )
    ) + Mux1H(
      Seq(
        input.selectedQuotientOH(3) -> "b110".U, // -2
        input.selectedQuotientOH(4) -> "b000".U, //  0
        input.selectedQuotientOH(5) -> "b010".U //  2
      )
    )
    cShiftQ := input.selectedQuotientOH(5) ||
    (input.selectedQuotientOH(4) && input.selectedQuotientOH(2, 1).orR)
    cShiftQM := input.selectedQuotientOH(3) ||
    (input.selectedQuotientOH(4) && input.selectedQuotientOH(1, 0).orR)
  }

  val qIn:  UInt = Mux(cShiftQ, qNext, radix.U + qNext)(radixLog2 - 1, 0)
  val qmIn: UInt = Mux(!cShiftQM, qNext - 1.U, (radix - 1).U + qNext)(radixLog2 - 1, 0)

  output.quotient := Mux(cShiftQ, input.quotient, input.quotientMinusOne)(qWidth - radixLog2, 0) ## qIn
  output.quotientMinusOne := Mux(!cShiftQM, input.quotient, input.quotientMinusOne)(qWidth - radixLog2, 0) ## qmIn
}

object OTF {
  def apply(
    radixLog2:          Int,
    qWidth:             Int,
    ohWidth:            Int,
    a:                  Int
  )(quotient:           UInt,
    quotientMinusOne:   UInt,
    selectedQuotientOH: UInt
  ): Vec[UInt] = {
    val m = Module(new OTF(radixLog2, qWidth, ohWidth, a))
    m.input.quotient := quotient
    m.input.quotientMinusOne := quotientMinusOne
    m.input.selectedQuotientOH := selectedQuotientOH
    VecInit(m.output.quotient, m.output.quotientMinusOne)
  }
}
