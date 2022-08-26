package v

import chisel3._
import chisel3.util.Valid


class OtherUnitReq(param: DataPathParam) extends Bundle {
  val src:  Vec[UInt] = Vec(2, UInt(param.dataWidth.W))
  val opcode: UInt = UInt(4.W)
  val extendType: Valid[ExtendInstructionType] = Valid(new ExtendInstructionType)
  val imm: UInt = UInt(3.W)
}

class OtherUnitResp(param: DataPathParam) extends Bundle {
  val data: UInt = UInt(param.dataWidth.W)
}

class OtherUnit(param: DataPathParam) extends Module {
  val req: OtherUnitReq = IO(Input(new OtherUnitReq(param)))
  val resp: OtherUnitResp = IO(Output(new OtherUnitResp(param)))
  resp.data := req.src.head
}