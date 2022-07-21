package v

import chisel3._

case class LaneMulParam(inputWidth: Int) {
  val respWidth: Int = 2 * inputWidth
}

class LaneMul(param: LaneMulParam) extends Module {
  val src:  Vec[UInt] = IO(Input(Vec(3, UInt(param.inputWidth.W))))
  val resp: Vec[UInt] = IO(Output(Vec(2, UInt(param.respWidth.W))))

  // TODO: mul
  // todo: resp c&s
  resp.head := src.head * src(1) + src.last
  resp.last := 0.U
}
