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

object LogicParam {
  implicit def rw: upickle.default.ReadWriter[LogicParam] = upickle.default.macroRW
}
case class LogicParam(eLen: Int, latency: Int, laneScale: Int) extends VFUParameter with SerializableModuleParameter {
  val datapathWidth: Int       = eLen * laneScale
  val decodeField:   BoolField = Decoder.logic
  val inputBundle  = new MaskedLogicRequest(datapathWidth)
  val outputBundle = new MaskedLogicResponse(datapathWidth)
}

class MaskedLogicRequest(datapathWidth: Int) extends VFUPipeBundle {
  val src:    Vec[UInt] = Vec(4, UInt(datapathWidth.W))
  val opcode: UInt      = UInt(4.W)
}

class MaskedLogicResponse(datapathWidth: Int) extends VFUPipeBundle {
  val data: UInt = UInt(datapathWidth.W)
}

class MaskedLogicOM(parameter: LogicParam) extends GeneralOM[LogicParam, MaskedLogicVFU](parameter) {
  override def hasRetime: Boolean = true
}

// Public FixedIOExtModule stub (per plan task12)
class MaskedLogicInterface(parameter: LogicParam) extends Bundle {
  val clock:      Clock                            = Input(Clock())
  val reset:      Bool                             = Input(Bool())
  val requestIO:  DecoupledIO[MaskedLogicRequest]  = Flipped(Decoupled(new MaskedLogicRequest(parameter.datapathWidth)))
  val responseIO: DecoupledIO[MaskedLogicResponse] = Decoupled(new MaskedLogicResponse(parameter.datapathWidth))
}

class MaskedLogic(val parameter: LogicParam)
    extends FixedIOExtModule(new MaskedLogicInterface(parameter))
    with SerializableModule[LogicParam] {
  val paramDir = java.nio.file.Paths.get("zaozi-params")
  java.nio.file.Files.createDirectories(paramDir)
  java.nio.file.Files.write(
    paramDir.resolve("MaskedLogic.json"),
    upickle.default.write(parameter).getBytes(java.nio.charset.StandardCharsets.UTF_8)
  )
}

// Internal VFU wrapper for Lane consumption
@instantiable
class MaskedLogicVFU(val parameter: LogicParam) extends VFUModule with SerializableModule[LogicParam] {
  val omInstance: Instance[MaskedLogicOM] = Instantiate(new MaskedLogicOM(parameter))
  omInstance.retimeIn.foreach(_ := Property(Path(clock)))

  val impl = Module(new MaskedLogic(parameter))
  impl.io.clock := clock
  impl.io.reset := reset.asBool

  impl.io.requestIO.valid := requestIO.valid
  requestIO.ready         := impl.io.requestIO.ready
  impl.io.requestIO.bits  := requestIO.bits.asTypeOf(impl.io.requestIO.bits)

  responseIO.valid         := impl.io.responseIO.valid
  impl.io.responseIO.ready := responseIO.ready
  responseIO.bits          := impl.io.responseIO.bits.asTypeOf(responseIO.bits)
}
