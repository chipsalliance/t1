// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.rtl

import me.jiuyang.zaozi.*
import me.jiuyang.zaozi.default.{*, given}
import me.jiuyang.zaozi.reftpe.*
import me.jiuyang.zaozi.valuetpe.*
import me.jiuyang.stdlib.*
import me.jiuyang.stdlib.default.{*, given}
import org.llvm.mlir.scalalib.capi.ir.{*, given}

import java.lang.foreign.Arena

case class LaneShifterParameter(datapathWidth: Int, latency: Int) extends Parameter:
  val shifterSizeBit: Int = log2Ceil(datapathWidth)

given upickle.default.ReadWriter[LaneShifterParameter] = upickle.default.macroRW

class LaneShifterReq(parameter: LaneShifterParameter) extends HWBundle(parameter):
  val tag:         BundleField[UInt]      = Aligned(UInt(4))
  val src:         BundleField[Vec[UInt]] = Aligned(Vec(2, UInt(parameter.datapathWidth)))
  val shifterSize: BundleField[UInt]      = Aligned(UInt(parameter.shifterSizeBit))
  val opcode:      BundleField[UInt]      = Aligned(UInt(3))
  val vxrm:        BundleField[UInt]      = Aligned(UInt(2))

class LaneShifterResponse(parameter: LaneShifterParameter) extends HWBundle(parameter):
  val tag:  BundleField[UInt] = Aligned(UInt(4))
  val data: BundleField[UInt] = Aligned(UInt(parameter.datapathWidth))

class LaneShifterInterface(parameter: LaneShifterParameter) extends HWBundle(parameter):
  val clock:      BundleField[Clock]                            = Flipped(Clock())
  val reset:      BundleField[Reset]                            = Flipped(Reset())
  val requestIO:  BundleField[DecoupledIO[LaneShifterReq]]      = Flipped(Decoupled(new LaneShifterReq(parameter)))
  val responseIO: BundleField[DecoupledIO[LaneShifterResponse]] =
    Aligned(Decoupled(new LaneShifterResponse(parameter)))

class LaneShifterLayers(parameter: LaneShifterParameter) extends LayerInterface(parameter):
  def layers = Seq.empty

class LaneShifterProbe(parameter: LaneShifterParameter)
    extends DVBundle[LaneShifterParameter, LaneShifterLayers](parameter)

@generator
object LaneShifter extends Generator[LaneShifterParameter, LaneShifterLayers, LaneShifterInterface, LaneShifterProbe]:
  override def moduleName(parameter: LaneShifterParameter): String = "LaneShifter"

  def architecture(parameter: LaneShifterParameter) =
    val p  = parameter
    val io = summon[Interface[LaneShifterInterface]]

    given Ref[Clock] = io.clock
    given Ref[Reset] = io.reset

    // SingleCycleVFU pipeline: requestIO.ready := true, requestRegValid := requestIO.fire
    io.requestIO.ready := true.B

    val requestRegValid = RegInit(false.B)
    requestRegValid := io.requestIO.valid & io.requestIO.ready

    val reqType    = new LaneShifterReq(p)
    val requestReg = RegInit(0.B(reqType.width).asType(reqType))
    when(io.requestIO.valid & io.requestIO.ready):
      requestReg := io.requestIO.bits

    // Compute response
    val response = Wire(new LaneShifterResponse(p))
    response.tag := requestReg.tag

    val shifterSource = requestReg.src(1)
    // arithmetic sign extension
    val signBit       = requestReg.opcode.asBits.bit(1) & shifterSource.asBits.bit(p.datapathWidth - 1)
    val extend        = fill(p.datapathWidth, signBit).asUInt
    val extendData    = (extend.asBits ## shifterSource.asBits).asUInt

    val roundTail = (1.U << requestReg.shifterSize).asBits.bits(p.datapathWidth, 0).asUInt
    val lostMSB   = (roundTail >> 1).asBits.bits(p.datapathWidth - 1, 0).asUInt
    val roundMask = (roundTail - 1.U).asBits.bits(p.datapathWidth - 1, 0).asUInt

    // v[d - 1]
    val vds1     = (lostMSB.asBits & shifterSource.asBits).asUInt.asBits.orR
    // v[d - 2 : 0]
    val vLostLSB =
      ((roundMask >> 1).asBits.bits(p.datapathWidth - 1, 0).asUInt.asBits & shifterSource.asBits).asUInt.asBits.orR
    // v[d]
    val vd       = (roundTail.asBits.bits(p.datapathWidth - 1, 0) & shifterSource.asBits).asUInt.asBits.orR
    // r
    val roundR   = mux1H(
      UIntToOH(requestReg.vxrm),
      Seq(
        vds1.asBits.asUInt,
        (vds1 & (vLostLSB | vd)).asBits.asUInt,
        false.B.asBits.asUInt,
        (!vd & (vds1 | vLostLSB)).asBits.asUInt
      )
    ).asBits.bit(0) & requestReg.opcode.asBits.bit(2)

    val shiftResult = requestReg.opcode.asBits.bit(0) ?
      (
        (extendData << requestReg.shifterSize).asBits.bits(p.datapathWidth - 1, 0).asUInt,
        (extendData >> requestReg.shifterSize).asBits.bits(p.datapathWidth - 1, 0).asUInt
      )
    response.data := (shiftResult + roundR.asBits.asUInt).asBits.bits(p.datapathWidth - 1, 0).asUInt

    // Pipe the response by latency (valid gates data writes, matching Chisel Pipe)
    val (pipedValid, pipedData) = pipeValidData(requestRegValid, response.asBits.asUInt, p.latency)

    io.responseIO.valid := pipedValid
    io.responseIO.bits  := pipedData.asBits.asType(new LaneShifterResponse(p))
