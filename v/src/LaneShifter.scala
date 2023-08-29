// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
object LaneShifterParameter {
  implicit def rw: upickle.default.ReadWriter[LaneShifterParameter] = upickle.default.macroRW
}
case class LaneShifterParameter(dataWidth: Int) extends VFUParameter with SerializableModuleParameter {
  val shifterSizeBit: Int = log2Ceil(dataWidth)
  val decodeField: BoolField = Decoder.shift
  val inputBundle = new LaneShifterReq(this)
  val outputBundle = new LaneShifterResponse(dataWidth)
}

class LaneShifterReq(param: LaneShifterParameter) extends Bundle {
  // vec(2, n) 是用来与别的vfu module的输入对齐
  val src:         Vec[UInt] = Vec(2, UInt(param.dataWidth.W))
  val shifterSize: UInt = UInt(param.shifterSizeBit.W)
  val opcode:      UInt = UInt(3.W)
  val vxrm:        UInt = UInt(2.W)
}

class LaneShifterResponse(datapathWidth: Int) extends Bundle {
  val data = UInt(datapathWidth.W)
}

class LaneShifter(val parameter: LaneShifterParameter)
  extends VFUModule(parameter) with SerializableModule[LaneShifterParameter] {
  val response: UInt = Wire(UInt(parameter.dataWidth.W))
  val request: LaneShifterReq = connectIO(response).asTypeOf(parameter.inputBundle)

  val shifterSource: UInt = request.src(1)
  // arithmetic
  val extend:     UInt = Fill(parameter.dataWidth, request.opcode(1) && shifterSource(parameter.dataWidth - 1))
  val extendData: UInt = extend ## shifterSource

  val roundTail: UInt = (1.U << request.shifterSize).asUInt
  val lostMSB:   UInt = (roundTail >> 1).asUInt
  val roundMask: UInt = roundTail - 1.U

  // v[d - 1]
  val vds1: Bool = (lostMSB & shifterSource).orR
  // v[d -2 : 0]
  val vLostLSB: Bool = (roundMask & shifterSource(1)).orR
  // v[d]
  val vd: Bool = (roundTail & shifterSource(1)).orR
  // r
  val roundR: Bool =
    Mux1H(UIntToOH(request.vxrm), Seq(vds1, vds1 & (vLostLSB | vd), false.B, !vd & (vds1 | vLostLSB))) && request.opcode(2)

  response := Mux(request.opcode(0), extendData << request.shifterSize, extendData >> request.shifterSize).asUInt + roundR
}
