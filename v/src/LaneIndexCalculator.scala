package v

import chisel3._

case class LaneIndexCalculatorParameter(inputWidth: Int, outputWidth: Int, laneSizeBit: Int)

class LaneIndexCalculator(param: LaneIndexCalculatorParameter, lanIndex: Int) extends Module {
  val groupIndex: UInt = IO(Input(UInt(param.inputWidth.W)))
  val resp: UInt = IO(Output(UInt(param.outputWidth.W)))
  resp := groupIndex ## lanIndex.U(param.laneSizeBit.W)
}
