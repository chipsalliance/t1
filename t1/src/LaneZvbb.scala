// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3.experimental.hierarchy.{instantiable, Instance, Instantiate}
import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.properties.{Path, Property}
import chisel3.util._
import org.chipsalliance.stdlib.GeneralOM
import org.chipsalliance.t1.rtl.decoder.{BoolField, Decoder}

object LaneZvbbParam {
  implicit def rw: upickle.default.ReadWriter[LaneZvbbParam] = upickle.default.macroRW
}

case class LaneZvbbParam(datapathWidth: Int, latency: Int) extends VFUParameter with SerializableModuleParameter {
  val inputBundle = new LaneZvbbRequest(datapathWidth)
  val decodeField: BoolField = Decoder.zvbb
  val outputBundle = new LaneZvbbResponse(datapathWidth)
  override val NeedSplit: Boolean = false
}

class LaneZvbbRequest(datapathWidth: Int) extends VFUPipeBundle {
  val src         = Vec(3, UInt(datapathWidth.W))
  val opcode      = UInt(4.W)
  val vSew        = UInt(2.W)
  val shifterSize = UInt(log2Ceil(datapathWidth).W)
}

class LaneZvbbResponse(datapathWidth: Int) extends VFUPipeBundle {
  val data = UInt(datapathWidth.W)
}

class LaneZvbbOM(parameter: LaneZvbbParam) extends GeneralOM[LaneZvbbParam, LaneZvbbVFU](parameter) {
  override def hasRetime: Boolean = true
}

// Public FixedIOExtModule stub (per plan task14)
class LaneZvbbInterface(parameter: LaneZvbbParam) extends Bundle {
  val clock:      Clock                         = Input(Clock())
  val reset:      Bool                          = Input(Bool())
  val requestIO:  DecoupledIO[LaneZvbbRequest]  = Flipped(Decoupled(new LaneZvbbRequest(parameter.datapathWidth)))
  val responseIO: DecoupledIO[LaneZvbbResponse] = Decoupled(new LaneZvbbResponse(parameter.datapathWidth))
}

class LaneZvbb(val parameter: LaneZvbbParam)
    extends FixedIOExtModule(new LaneZvbbInterface(parameter))
    with SerializableModule[LaneZvbbParam] {
  val paramDir = java.nio.file.Paths.get("zaozi-params")
  java.nio.file.Files.createDirectories(paramDir)
  java.nio.file.Files.write(
    paramDir.resolve("LaneZvbb.json"),
    upickle.default.write(parameter).getBytes(java.nio.charset.StandardCharsets.UTF_8)
  )
}

// Internal VFU wrapper for Lane consumption
@instantiable
class LaneZvbbVFU(val parameter: LaneZvbbParam) extends VFUModule with SerializableModule[LaneZvbbParam] {
  val omInstance: Instance[LaneZvbbOM] = Instantiate(new LaneZvbbOM(parameter))
  omInstance.retimeIn.foreach(_ := Property(Path(clock)))

  val impl = Module(new LaneZvbb(parameter))
  impl.io.clock := clock
  impl.io.reset := reset.asBool

  impl.io.requestIO.valid := requestIO.valid
  requestIO.ready         := impl.io.requestIO.ready
  impl.io.requestIO.bits  := requestIO.bits.asTypeOf(impl.io.requestIO.bits)

  responseIO.valid         := impl.io.responseIO.valid
  impl.io.responseIO.ready := responseIO.ready
  responseIO.bits          := impl.io.responseIO.bits.asTypeOf(responseIO.bits)
}
