package v

import chisel3._
import chisel3.util.PopCount

case class LanePopCountParameter(inputWidth: Int, outputWidth: Int)

class LanePopCount(param: LanePopCountParameter) extends Module {
  val src: UInt = IO(Input(UInt(param.inputWidth.W)))
  val resp: UInt = IO(Output(UInt(param.outputWidth.W)))
  resp := PopCount(src)
}
