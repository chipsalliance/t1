package v

import chisel3._

case class LaneIndexCalculatorParameter(inputWidth: Int, laneSizeBit: Int) {
  val outputWidth: Int = inputWidth + laneSizeBit
}

class LaneIndexCalculator(param: LaneIndexCalculatorParameter) extends Module {
  val groupIndex: UInt = IO(Input(UInt(param.inputWidth.W)))
  val resp: UInt = IO(Output(UInt(param.outputWidth.W)))
  val laneIndex: UInt = IO(Input(UInt(param.laneSizeBit.W)))
  resp := groupIndex ## laneIndex
}
