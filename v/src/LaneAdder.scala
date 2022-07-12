package v

import chisel3._

class LaneAdder(param: DataPathParam) extends Module {
  val src:  Vec[UInt] = IO(Input(Vec(3, UInt(param.dataWidth.W))))
  val resp: UInt = IO(Output(UInt((param.dataWidth + 1).W)))
  val (s, c) = csa32(src.head, src(1), src.last)
  resp := s + (c ## false.B)
}
