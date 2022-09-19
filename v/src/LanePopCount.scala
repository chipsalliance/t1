package v

import chisel3._
import chisel3.util.PopCount

class LanePopCount(param: DataPathParam) extends Module {
  val src:  UInt = IO(Input(UInt(param.dataWidth.W)))
  val resp: UInt = IO(Output(UInt(param.dataBits.W)))
  resp := PopCount(src)
}
