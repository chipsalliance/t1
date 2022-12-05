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

  val vs2: UInt = req.src.head
  val vs1: UInt = req.src(1)
  val op1: UInt = Mux(req.reverse, vs1, vs2)
  val op2: UInt = Mux(req.reverse, vs2, vs1)
  val op3: UInt = Mux1H(UIntToOH(req.opcode(3) ## req.opcode(0)), Seq(
    req.src.last,              // add
    1.U,                       // sub
    req.src.last(0).asUInt,    // adc
    (~req.src.last(0)).asUInt, // sbc
  ))

  val subOrSbc: Bool = req.opcode(0)
  val (s, c) = csa32(Mux(subOrSbc, (~op1).asUInt, op1), op2, op3)
  val result: UInt = s +& (c ## false.B)
  val average: UInt = (result >> 1).asUInt + Mux1H(
    UIntToOH(csr.vxrm),
    Seq(result(0), result(0) && result(1), false.B, result(0) && !result(1))
  )
  resp := Mux(req.average, average, result)
}
