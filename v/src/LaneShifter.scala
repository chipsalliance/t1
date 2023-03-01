package v

import chisel3._
import chisel3.util._

case class LaneShifterParameter(dataWidth: Int, shifterSizeBit: Int)

class LaneShifterReq(param: LaneShifterParameter) extends Bundle {
  val src:         UInt = UInt(param.dataWidth.W)
  val shifterSize: UInt = UInt(param.shifterSizeBit.W)
  val opcode:      UInt = UInt(3.W)
}

class LaneShifter(param: LaneShifterParameter) extends Module {
  val req:  LaneShifterReq = IO(Input(new LaneShifterReq(param)))
  val resp: UInt = IO(Output(UInt(param.dataWidth.W)))
  val vxrm: UInt = IO(Input(UInt(2.W)))

  // arithmetic
  val extend:     UInt = Fill(param.dataWidth, req.opcode(1) && req.src(param.dataWidth - 1))
  val extendData: UInt = extend ## req.src

  val roundTail: UInt = (1.U << req.shifterSize).asUInt
  val lostMSB: UInt = (roundTail >> 1).asUInt
  val roundMask: UInt = roundTail - 1.U

  // v[d - 1]
  val vds1: Bool = (lostMSB & req.src).orR
  // v[d -2 : 0]
  val vLostLSB: Bool = (roundMask & req.src(1)).orR
  // v[d]
  val vd: Bool = (roundTail & req.src(1)).orR
  // r
  val roundR: Bool =
    Mux1H(UIntToOH(vxrm), Seq(vds1, vds1 & (vLostLSB | vd), false.B, !vd & (vds1 | vLostLSB))) && req.opcode(2)

  resp := Mux(req.opcode(0), extendData << req.shifterSize, extendData >> req.shifterSize).asUInt + roundR
}
