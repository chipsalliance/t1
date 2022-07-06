package v

import chisel3._

class LaneMul(param: VectorParameters) extends Module {
  val src:  Vec[UInt] = IO(Input(Vec(2, UInt(param.ELEN.W))))
  val resp: Vec[UInt] = IO(Output(Vec(2, UInt(param.mulRespWidth.W))))

  // todo: resp c&s
  resp.head := src.head * src.last
  resp.last := 0.U
}
