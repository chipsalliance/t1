// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.properties.{Path, Property}
import chisel3.util._
import org.chipsalliance.stdlib.GeneralOM
import org.chipsalliance.t1.rtl.decoder.{BoolField, Decoder}
object LaneShifterParameter          {
  implicit def rw: upickle.default.ReadWriter[LaneShifterParameter] = upickle.default.macroRW
}
case class LaneShifterParameter(datapathWidth: Int, latency: Int)
    extends VFUParameter
    with SerializableModuleParameter {
  val shifterSizeBit: Int       = log2Ceil(datapathWidth)
  val decodeField:    BoolField = Decoder.shift
  val inputBundle  = new LaneShifterReq(this)
  val outputBundle = new LaneShifterResponse(datapathWidth)
  override val NeedSplit: Boolean = true
}

class LaneShifterReq(param: LaneShifterParameter) extends VFUPipeBundle {
  // vec(2, n) 是用来与别的vfu module的输入对齐
  val src:         Vec[UInt] = Vec(2, UInt(param.datapathWidth.W))
  val shifterSize: UInt      = UInt(param.shifterSizeBit.W)
  val opcode:      UInt      = UInt(3.W)
  val vxrm:        UInt      = UInt(2.W)
}

class LaneShifterResponse(datapathWidth: Int) extends VFUPipeBundle {
  val data = UInt(datapathWidth.W)
}

class LaneShifterOM(parameter: LaneShifterParameter) extends GeneralOM[LaneShifterParameter, LaneShifter](parameter) {
  override def hasRetime: Boolean = true
}

@instantiable
class LaneShifter(val parameter: LaneShifterParameter) extends VFUModule with SerializableModule[LaneShifterParameter] {
  val omInstance: Instance[LaneShifterOM] = Instantiate(new LaneShifterOM(parameter))
  omInstance.retimeIn.foreach(_ := Property(Path(clock)))

  val response: LaneShifterResponse = Wire(new LaneShifterResponse(parameter.datapathWidth))
  val request:  LaneShifterReq      = connectIO(response).asTypeOf(parameter.inputBundle)

  val shifterSource: UInt = request.src(1)
  // arithmetic
  val extend:        UInt = Fill(parameter.datapathWidth, request.opcode(1) && shifterSource(parameter.datapathWidth - 1))
  val extendData:    UInt = extend ## shifterSource

  val roundTail: UInt = (1.U << request.shifterSize).asUInt
  val lostMSB:   UInt = (roundTail >> 1).asUInt
  val roundMask: UInt = roundTail - 1.U

  // v[d - 1]
  val vds1:     Bool = (lostMSB & shifterSource).orR
  // v[d -2 : 0]
  val vLostLSB: Bool = ((roundMask >> 1).asUInt & shifterSource).orR
  // v[d]
  val vd:       Bool = (roundTail & shifterSource).orR
  // r
  val roundR:   Bool =
    Mux1H(UIntToOH(request.vxrm), Seq(vds1, vds1 & (vLostLSB | vd), false.B, !vd & (vds1 | vLostLSB))) && request
      .opcode(2)

  response.data := Mux(
    request.opcode(0),
    extendData << request.shifterSize,
    extendData >> request.shifterSize
  ).asUInt + roundR
}
