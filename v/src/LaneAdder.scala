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
  // todo: decode
  // ["add", "sub", "slt", "sle", "sgt", "sge", "max", "min", "seq", "sne", "adc", "sbc"]
  val isSub: Bool = !(req.opcode === 0.U || req.opcode === 11.U)
  // reverse
  val reverseOperation0: UInt = Mux(req.reverse, req.src(1), req.src.head)
  val reverseOperation1: UInt = Mux(req.reverse, req.src.head, req.src(1))
  // sub -> src(1) - src(0)
  val subOperation0Correction: UInt = Mux(isSub, (~reverseOperation0).asUInt, reverseOperation0)
  // sub + 1 || carry || borrow
  val operation2: UInt = Mux(
    isSub,
    Mux(req.src.last(0), -1.S.asTypeOf(req.src.last), 1.U),
    req.src.last(0)
  )
  // TODO: adder
  val (s, c) = csa32(subOperation0Correction, reverseOperation1, operation2)
  val addResult: UInt = s +& (c ## false.B)
  /** 因为 average 会右移, 所以需要比[[param.dataWidth]]多一位的结果
    * 对于有符号的而言,多出来的是符号位扩展
    * 对于无符号的而言:
    *   加法多出来的是[[addResult]]的进位
    *   减法恒定是0
    * For vaaddu and vaadd there can be no overflow in the result. For
    * vasub and vasubu, overflow is ignored and the result wraps around.
    */
  val averageResult: UInt = (Mux(req.sign, addResult(param.dataWidth - 1), !isSub && addResult(param.dataWidth)) ## (addResult >> 1).asUInt(30, 0)) + Mux1H(
    UIntToOH(csr.vxrm),
    Seq(addResult(0), addResult(0) && addResult(1), false.B, addResult(0) && !addResult(1))
  )
  resp := Mux(req.average, averageResult, addResult)
}
