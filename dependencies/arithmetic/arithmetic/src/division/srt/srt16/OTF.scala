package division.srt.srt16

import division.srt._
import chisel3._
import chisel3.util.Mux1H

class OTF(radixLog2: Int, qWidth: Int, ohWidth: Int) extends Module {
  val input = IO(Input(new OTFInput(qWidth, ohWidth)))
  val output = IO(Output(new OTFOutput(qWidth)))

  val radix: Int = 1 << radixLog2
  // datapath
  // q_j+1 in this circle, only for srt4
  val qNext: UInt = Mux1H(
    Seq(
      input.selectedQuotientOH(0) -> "b110".U,
      input.selectedQuotientOH(1) -> "b111".U,
      input.selectedQuotientOH(2) -> "b000".U,
      input.selectedQuotientOH(3) -> "b001".U,
      input.selectedQuotientOH(4) -> "b010".U
    )
  )

  // val cShiftQ:  Bool = qNext >= 0.U
  // val cShiftQM: Bool = qNext <=  0.U
  val cShiftQ:  Bool = input.selectedQuotientOH(ohWidth - 1, ohWidth / 2).orR
  val cShiftQM: Bool = input.selectedQuotientOH(ohWidth / 2, 0).orR
  val qIn:      UInt = Mux(cShiftQ, qNext, radix.U + qNext)(radixLog2 - 1, 0)
  val qmIn:     UInt = Mux(!cShiftQM, qNext - 1.U, (radix - 1).U + qNext)(radixLog2 - 1, 0)

  output.quotient := Mux(cShiftQ, input.quotient, input.quotientMinusOne)(qWidth - radixLog2, 0) ## qIn
  output.quotientMinusOne := Mux(!cShiftQM, input.quotient, input.quotientMinusOne)(qWidth - radixLog2, 0) ## qmIn
}

object OTF {
  def apply(
    radixLog2:          Int,
    qWidth:             Int,
    ohWidth:            Int
  )(quotient:           UInt,
    quotientMinusOne:   UInt,
    selectedQuotientOH: UInt
  ): Vec[UInt] = {
    val m = Module(new OTF(radixLog2, qWidth, ohWidth))
    m.input.quotient := quotient
    m.input.quotientMinusOne := quotientMinusOne
    m.input.selectedQuotientOH := selectedQuotientOH
    VecInit(m.output.quotient, m.output.quotientMinusOne)
  }
}
