package v

import chisel3._
import chisel3.util._

class LaneAdderReq(param: DataPathParam) extends Bundle {
  val src:     Vec[UInt] = Vec(3, UInt(param.dataWidth.W))
  val opcode:  UInt = UInt(4.W)
  val sign:    Bool = Bool()
  val reverse: Bool = Bool()
  val average: Bool = Bool()
}

class LaneAdderCsr extends Bundle {
  val vxrm: UInt = UInt(2.W)
  val vSew: UInt = UInt(2.W)
}

class LaneAdder(param: DataPathParam) extends Module {
  val req:  LaneAdderReq = IO(Input(new LaneAdderReq(param)))
  val resp: UInt = IO(Output(UInt((param.dataWidth + 1).W)))
  val csr:  LaneAdderCsr = IO(Input(new LaneAdderCsr()))
  // TODO: adder
  val (s, c) = csa32(req.src.head, req.src(1), req.src.last)
  val addResult: UInt = s +& (c ## false.B)
  /** 因为 average 会右移, 所以需要比[[param.dataWidth]]多一位的结果
    * 对于有符号的而言,多出来的是符号位扩展
    * 对于无符号的而言,多出来的是上面加的进位
    * For vaaddu and vaadd there can be no overflow in the result. For
    * vasub and vasubu, overflow is ignored and the result wraps around.
    * todo：vasub and vasubu
    */
  val averageResult: UInt = (Mux(req.sign, addResult(param.dataWidth - 1), addResult(param.dataWidth)) ## (addResult >> 1).asUInt(30, 0)) + Mux1H(
    UIntToOH(csr.vxrm),
    Seq(addResult(0), addResult(0) && addResult(1), false.B, addResult(0) && !addResult(1))
  )
  resp := Mux(req.average, averageResult, addResult)
}
