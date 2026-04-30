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
  val src:         Vec[UInt] = Vec(2, UInt(param.datapathWidth.W))
  val shifterSize: UInt      = UInt(param.shifterSizeBit.W)
  val opcode:      UInt      = UInt(3.W)
  val vxrm:        UInt      = UInt(2.W)
}

class LaneShifterResponse(datapathWidth: Int) extends VFUPipeBundle {
  val data = UInt(datapathWidth.W)
}

class LaneShifterOM(parameter: LaneShifterParameter)
    extends GeneralOM[LaneShifterParameter, LaneShifterVFU](parameter) {
  override def hasRetime: Boolean = true
}

// Public FixedIOExtModule stub (per plan task10)
class LaneShifterInterface(parameter: LaneShifterParameter) extends Bundle {
  val clock:      Clock                            = Input(Clock())
  val reset:      Bool                             = Input(Bool())
  val requestIO:  DecoupledIO[LaneShifterReq]      = Flipped(Decoupled(new LaneShifterReq(parameter)))
  val responseIO: DecoupledIO[LaneShifterResponse] = Decoupled(new LaneShifterResponse(parameter.datapathWidth))
}

class LaneShifter(val parameter: LaneShifterParameter)
    extends FixedIOExtModule(new LaneShifterInterface(parameter))
    with SerializableModule[LaneShifterParameter] {
  val paramDir = java.nio.file.Paths.get("zaozi-params")
  java.nio.file.Files.createDirectories(paramDir)
  java.nio.file.Files.write(
    paramDir.resolve("LaneShifter.json"),
    upickle.default.write(parameter).getBytes(java.nio.charset.StandardCharsets.UTF_8)
  )
}

// Internal VFU wrapper for Lane consumption (owns OM, delegates hardware to LaneShifter ExtModule)
@instantiable
class LaneShifterVFU(val parameter: LaneShifterParameter)
    extends VFUModule
    with SerializableModule[LaneShifterParameter] {
  val omInstance: Instance[LaneShifterOM] = Instantiate(new LaneShifterOM(parameter))
  omInstance.retimeIn.foreach(_ := Property(Path(clock)))

  val impl = Module(new LaneShifter(parameter))
  impl.io.clock := clock
  impl.io.reset := reset.asBool

  impl.io.requestIO.valid := requestIO.valid
  requestIO.ready         := impl.io.requestIO.ready
  impl.io.requestIO.bits  := requestIO.bits.asTypeOf(impl.io.requestIO.bits)

  responseIO.valid         := impl.io.responseIO.valid
  impl.io.responseIO.ready := responseIO.ready
  responseIO.bits          := impl.io.responseIO.bits.asTypeOf(responseIO.bits)
}
