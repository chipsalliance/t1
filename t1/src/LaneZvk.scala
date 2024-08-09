// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3.experimental.hierarchy.instantiable
import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import org.chipsalliance.t1.rtl.decoder.{BoolField, Decoder}

object LaneZvkParam {
  implicit def rw: upickle.default.ReadWriter[LaneZvkParam] = upickle.default.macroRW
}

case class LaneZvkParam(datapathWidth: Int, latency: Int) extends VFUParameter with SerializableModuleParameter {
  val inputBundle = new LaneZvkRequest(datapathWidth)
  val decodeField: BoolField = Decoder.zvbb
  val outputBundle = new LaneZvkResponse(datapathWidth)
  override val NeedSplit: Boolean = false
}

class LaneZvkRequest(datapathWidth: Int) extends VFUPipeBundle {
  val src    = Vec(3, UInt(datapathWidth.W))
  val opcode = UInt(4.W)
  val vSew   = UInt(2.W)
  val shifterSize = UInt(log2Ceil(datapathWidth).W)
}

class LaneZvkResponse(datapathWidth: Int)  extends VFUPipeBundle {
  val data = UInt(datapathWidth.W)
}

@instantiable
class LaneZvk(val parameter: LaneZvkParam) 
  extends VFUModule(parameter) with SerializableModule[LaneZvkParam]{
  val response: LaneZvkResponse = Wire(new LaneZvkResponse(parameter.datapathWidth))
  val request : LaneZvkRequest  = connectIO(response).asTypeOf(parameter.inputBundle)

  val zvbbSrc: UInt = request.src(1) // vs2
  val zvbbRs:  UInt = request.src(0) // vs1 or rs1
  val vSew:    UInt = UIntToOH(request.vSew) // sew = 0, 1, 2

 
  response.data := Mux1H(UIntToOH(request.opcode), Seq(
  ))
}

