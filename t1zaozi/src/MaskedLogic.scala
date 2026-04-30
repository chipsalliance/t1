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

case class LogicParam(eLen: Int, latency: Int, laneScale: Int) extends Parameter:
  val datapathWidth: Int = eLen * laneScale

given upickle.default.ReadWriter[LogicParam] = upickle.default.macroRW

class MaskedLogicRequest(parameter: LogicParam) extends HWBundle(parameter):
  val tag:    BundleField[UInt]      = Aligned(UInt(4))
  val src:    BundleField[Vec[UInt]] = Aligned(Vec(4, UInt(parameter.datapathWidth)))
  val opcode: BundleField[UInt]      = Aligned(UInt(4))

class MaskedLogicResponse(parameter: LogicParam) extends HWBundle(parameter):
  val tag:  BundleField[UInt] = Aligned(UInt(4))
  val data: BundleField[UInt] = Aligned(UInt(parameter.datapathWidth))

class MaskedLogicInterface(parameter: LogicParam) extends HWBundle(parameter):
  val clock:      BundleField[Clock]                            = Flipped(Clock())
  val reset:      BundleField[Reset]                            = Flipped(Reset())
  val requestIO:  BundleField[DecoupledIO[MaskedLogicRequest]]  =
    Flipped(Decoupled(new MaskedLogicRequest(parameter)))
  val responseIO: BundleField[DecoupledIO[MaskedLogicResponse]] =
    Aligned(Decoupled(new MaskedLogicResponse(parameter)))

class MaskedLogicLayers(parameter: LogicParam) extends LayerInterface(parameter):
  def layers = Seq.empty

class MaskedLogicProbe(parameter: LogicParam) extends DVBundle[LogicParam, MaskedLogicLayers](parameter)

@generator
object MaskedLogic extends Generator[LogicParam, MaskedLogicLayers, MaskedLogicInterface, MaskedLogicProbe]:
  override def moduleName(parameter: LogicParam): String = "MaskedLogic"

  def architecture(parameter: LogicParam) =
    val p  = parameter
    val io = summon[Interface[MaskedLogicInterface]]

    given Ref[Clock] = io.clock
    given Ref[Reset] = io.reset

    // SingleCycleVFU pipeline
    io.requestIO.ready := true.B

    val requestRegValid = RegInit(false.B)
    requestRegValid := io.requestIO.valid & io.requestIO.ready

    val reqType    = new MaskedLogicRequest(p)
    val requestReg = RegInit(0.B(reqType.width).asType(reqType))
    when(io.requestIO.valid & io.requestIO.ready):
      requestReg := io.requestIO.bits

    val response = Wire(new MaskedLogicResponse(p))
    response.tag := requestReg.tag

    val opcode = requestReg.opcode

    // Same QMC logic as LaneLogic but with masking:
    // For each bit: Mux(mask_bit, qmc_result, original_data_bit)
    // src(0) and src(1) are operands, src(2) is original data (vd), src(3) is mask
    val resultBits = Seq.tabulate(p.datapathWidth) { i =>
      val sr0 = requestReg.src(0).asBits.bit(i)
      val sr1 = requestReg.src(1).asBits.bit(i)
      val sr2 = requestReg.src(2).asBits.bit(i)
      val sr3 = requestReg.src(3).asBits.bit(i)

      // Note: in Chisel, the QMC input is opcode(1,0) ## (opcode(2) ^ sr0) ## sr1
      val a = opcode.asBits.bit(2) ^ sr0
      val b = sr1

      val op0 = opcode.asBits.bit(0)
      val op1 = opcode.asBits.bit(1)

      // QMC minimized matching Chisel PLA don't-care resolution (opcode 11 -> 0):
      // f = (!op1 & a & b) | (!op1 & op0 & (a | b)) | (op1 & !op0 & (a ^ b))
      val qmcResult   = (!op1 & a & b) | (!op1 & op0 & (a | b)) | (op1 & !op0 & (a ^ b))
      val logicResult = qmcResult ^ opcode.asBits.bit(3)

      (sr3 ? (logicResult, sr2)).asBits
    }

    response.data := resultBits.reverse.reduce(_ ## _).asUInt

    // Pipe the response (valid gates data writes, matching Chisel Pipe)
    val (pipedValid, pipedData) = pipeValidData(requestRegValid, response.asBits.asUInt, p.latency)

    io.responseIO.valid := pipedValid
    io.responseIO.bits  := pipedData.asBits.asType(new MaskedLogicResponse(p))
