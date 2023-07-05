package v

import chisel3._
import chisel3.util._

/**
  */
case class LaneShifterParameter(dataWidth: Int) extends VFUParameter {
  val shifterSizeBit: Int = log2Ceil(dataWidth)
  val decodeField: BoolField = Decoder.shift
  val inputBundle = new LaneShifterReq(this)
  val outputBundle: UInt = UInt(dataWidth.W)
}

class LaneShifterReq(param: LaneShifterParameter) extends Bundle {
  val src:         UInt = UInt(param.dataWidth.W)
  val shifterSize: UInt = UInt(param.shifterSizeBit.W)
  val opcode:      UInt = UInt(3.W)
  val vxrm:        UInt = UInt(2.W)
}

class LaneShifter(val parameter: LaneShifterParameter) extends VFUModule(parameter) {
  val response: UInt = Wire(UInt(parameter.dataWidth.W))
  val request: LaneShifterReq = connectIO(response).asTypeOf(parameter.inputBundle)

  // arithmetic
  val extend:     UInt = Fill(parameter.dataWidth, request.opcode(1) && request.src(parameter.dataWidth - 1))
  val extendData: UInt = extend ## request.src

  val roundTail: UInt = (1.U << request.shifterSize).asUInt
  val lostMSB:   UInt = (roundTail >> 1).asUInt
  val roundMask: UInt = roundTail - 1.U

  // v[d - 1]
  val vds1: Bool = (lostMSB & request.src).orR
  // v[d -2 : 0]
  val vLostLSB: Bool = (roundMask & request.src(1)).orR
  // v[d]
  val vd: Bool = (roundTail & request.src(1)).orR
  // r
  val roundR: Bool =
    Mux1H(UIntToOH(request.vxrm), Seq(vds1, vds1 & (vLostLSB | vd), false.B, !vd & (vds1 | vLostLSB))) && request.opcode(2)

  response := Mux(request.opcode(0), extendData << request.shifterSize, extendData >> request.shifterSize).asUInt + roundR
}
