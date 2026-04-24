// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.rtl.zvma

import me.jiuyang.zaozi.*
import me.jiuyang.zaozi.default.{*, given}
import me.jiuyang.zaozi.reftpe.*
import me.jiuyang.zaozi.valuetpe.*
import me.jiuyang.stdlib.*
import me.jiuyang.stdlib.default.{*, given}
import org.llvm.mlir.scalalib.capi.ir.{*, given}

import java.lang.foreign.Arena

case class LaneLogicParameter(datapathWidth: Int) extends Parameter

given upickle.default.ReadWriter[LaneLogicParameter] = upickle.default.macroRW

class LaneLogicRequest(parameter: LaneLogicParameter) extends HWBundle(parameter):
  val src:    BundleField[Vec[UInt]] = Aligned(Vec(2, UInt(parameter.datapathWidth)))
  val opcode: BundleField[UInt]      = Aligned(UInt(4))

class LaneLogicInterface(parameter: LaneLogicParameter) extends HWBundle(parameter):
  val req:  BundleField[LaneLogicRequest] = Flipped(new LaneLogicRequest(parameter))
  val resp: BundleField[UInt]             = Aligned(UInt(parameter.datapathWidth))

class LaneLogicLayers(parameter: LaneLogicParameter) extends LayerInterface(parameter):
  def layers = Seq.empty

class LaneLogicProbe(parameter: LaneLogicParameter)
    extends DVBundle[LaneLogicParameter, LaneLogicLayers](parameter)

@generator
object LaneLogic extends Generator[LaneLogicParameter, LaneLogicLayers, LaneLogicInterface, LaneLogicProbe]:
  override def moduleName(parameter: LaneLogicParameter): String = "LaneLogic"

  def architecture(parameter: LaneLogicParameter) =
    val io = summon[Interface[LaneLogicInterface]]

    // Replicate the Chisel QMC decoder logic faithfully:
    // For each bit position, compute:
    //   input = opcode(1,0) ## src0_bit ## (opcode(2) ^ src1_bit)
    //   qmc_result = decode(input) using LogicTable truth table
    //   result_bit = qmc_result ^ opcode(3)
    //
    // The LogicTable truth table maps {op[1:0], src0, src1} to a 1-bit result:
    //   and(0): 00_11 -> 1, 00_10 -> 0, 00_01 -> 0, 00_00 -> 0
    //   or(1):  01_11 -> 1, 01_10 -> 1, 01_01 -> 1, 01_00 -> 0
    //   xor(2): 10_11 -> 0, 10_10 -> 1, 10_01 -> 1, 10_00 -> 0
    // QMC minimization of these 12 entries produces:
    //   f = (!op1 & a & b) | (op0 & (a | b)) | (op1 & (a ^ b))
    // where a = src0_bit, b = opcode(2) ^ src1_bit

    val opcode = io.req.opcode
    val src0   = io.req.src(0)
    val src1   = io.req.src(1)

    val resultBits = Seq.tabulate(parameter.datapathWidth) { i =>
      val a = src0.asBits.bit(i)
      val b = opcode.asBits.bit(2) ^ src1.asBits.bit(i)

      val op0 = opcode.asBits.bit(0)
      val op1 = opcode.asBits.bit(1)

      // QMC minimized: f = (!op1 & a & b) | (op0 & (a | b)) | (op1 & (a ^ b))
      val qmcResult = (!op1 & a & b) | (op0 & (a | b)) | (op1 & (a ^ b))
      (qmcResult ^ opcode.asBits.bit(3)).asBits
    }

    io.resp := resultBits.reverse.reduce(_ ## _).asUInt
