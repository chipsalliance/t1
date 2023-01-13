package v

import chisel3._
import chisel3.util._

class LaneAdderReq(param: DataPathParam) extends Bundle {
  val src:     Vec[UInt] = Vec(2, UInt((param.dataWidth + 1).W))
  // mask for carry or borrow
  val mask:    Bool = Bool()
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
  // sub -> src(1) - src(0)
  val subOperation0: UInt = Mux(isSub && !req.reverse, (~req.src.head).asUInt, req.src.head)
  val subOperation1: UInt = Mux(isSub && req.reverse, (~req.src.last).asUInt, req.src.last)
  // sub + 1 || carry || borrow
  val operation2: UInt = isSub ^ req.mask

  //todo: decode(req) -> roundingTail
  val roundingTail: UInt = (subOperation0 + subOperation1 + operation2)(1, 0)
  val vxrmCorrection: UInt = Mux(req.average, csr.vxrm, 2.U)
  val roundingBits: Bool = Mux1H(
    UIntToOH(vxrmCorrection),
    Seq(
      roundingTail(0),
      roundingTail(0) && roundingTail(1),
      false.B,
      roundingTail(0) && !roundingTail(1)
    )
  )
  // TODO: adder
  val (s, c) = csa32(subOperation0, subOperation1, roundingBits ## operation2)
  val addResult: UInt = s + (c ## false.B)
  resp := Mux(req.average, addResult(param.dataWidth, 1), addResult)
}
