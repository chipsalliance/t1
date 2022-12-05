package v

import chisel3._
import chisel3.util._

class LaneAdderReq(param: DataPathParam) extends Bundle {
  val src:     Vec[UInt] = Vec(3, UInt(param.dataWidth.W))
  val opcode:  UInt = UInt(4.W)
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
  val sub: Bool = req.opcode(0)

  val vs2: UInt = req.src.head
  val vs1: UInt = req.src(1)
  val op1: UInt = Mux(req.reverse, vs1, vs2)
  val op2: UInt = Mux(req.reverse, vs2, vs1)
  val (s, c) = csa32(
    Mux(sub, (~op1).asUInt, op1),
    op2,
    Mux(sub, 1.U, req.src.last),
  )
  val addResult: UInt = s +& (c ## false.B)
  val averageResult: UInt = (addResult >> 1).asUInt + Mux1H(
    UIntToOH(csr.vxrm),
    Seq(addResult(0), addResult(0) && addResult(1), false.B, addResult(0) && !addResult(1))
  )
  resp := Mux(req.average, averageResult, addResult)
}
