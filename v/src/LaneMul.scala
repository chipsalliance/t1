package v

import chisel3._

case class LaneMulParam(inputWidth: Int) {
  val respWidth: Int = 2 * inputWidth
}

class LaneMulReq(param: LaneMulParam) extends Bundle {
  val src:  Vec[UInt] = Vec(3, UInt(param.inputWidth.W))
  val opcode: UInt = UInt(4.W)
}

class LaneMul(param: LaneMulParam) extends Module {
  val req: LaneMulReq = IO(Input(new LaneMulReq(param)))
  val resp: Vec[UInt] = IO(Output(Vec(2, UInt(param.respWidth.W))))

  // TODO: mul
  // todo: resp c&s
  resp.head := req.src.head * req.src(1) + req.src.last
  resp.last := 0.U
}
