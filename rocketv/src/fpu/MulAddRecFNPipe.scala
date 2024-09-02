// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.instantiable
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util.Pipe

object MulAddRecFNPipeParameter {
  implicit def rwP: upickle.default.ReadWriter[MulAddRecFNPipeParameter] =
    upickle.default.macroRW[MulAddRecFNPipeParameter]
}

case class MulAddRecFNPipeParameter(
  useAsyncReset: Boolean,
  latency:       Int,
  expWidth:      Int,
  sigWidth:      Int)
    extends SerializableModuleParameter {
  require(latency <= 2)
}

class MulAddRecFNPipeInterface(parameter: MulAddRecFNPipeParameter) extends Bundle {
  val clock          = Input(Clock())
  val reset          = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val validin        = Input(Bool())
  val op             = Input(UInt(2.W))
  val a              = Input(UInt((parameter.expWidth + parameter.sigWidth + 1).W))
  val b              = Input(UInt((parameter.expWidth + parameter.sigWidth + 1).W))
  val c              = Input(UInt((parameter.expWidth + parameter.sigWidth + 1).W))
  val roundingMode   = Input(UInt(3.W))
  val detectTininess = Input(UInt(1.W))
  val out            = Output(UInt((parameter.expWidth + parameter.sigWidth + 1).W))
  val exceptionFlags = Output(UInt(5.W))
  val validout       = Output(Bool())
}

@instantiable
class MulAddRecFNPipe(val parameter: MulAddRecFNPipeParameter)
    extends FixedIORawModule(new MulAddRecFNPipeInterface(parameter))
    with SerializableModule[MulAddRecFNPipeParameter]
    with Public
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val latency:  Int = parameter.latency
  val expWidth: Int = parameter.expWidth
  val sigWidth: Int = parameter.sigWidth
  // ------------------------------------------------------------------------
  // ------------------------------------------------------------------------

  val mulAddRecFNToRaw_preMul  = Module(new hardfloat.MulAddRecFNToRaw_preMul(expWidth, sigWidth))
  val mulAddRecFNToRaw_postMul = Module(new hardfloat.MulAddRecFNToRaw_postMul(expWidth, sigWidth))

  mulAddRecFNToRaw_preMul.io.op := io.op
  mulAddRecFNToRaw_preMul.io.a  := io.a
  mulAddRecFNToRaw_preMul.io.b  := io.b
  mulAddRecFNToRaw_preMul.io.c  := io.c

  val mulAddResult =
    (mulAddRecFNToRaw_preMul.io.mulAddA *
      mulAddRecFNToRaw_preMul.io.mulAddB) +&
      mulAddRecFNToRaw_preMul.io.mulAddC

  val valid_stage0          = Wire(Bool())
  val roundingMode_stage0   = Wire(UInt(3.W))
  val detectTininess_stage0 = Wire(UInt(1.W))

  val postmul_regs = if (latency > 0) 1 else 0
  mulAddRecFNToRaw_postMul.io.fromPreMul   := Pipe(io.validin, mulAddRecFNToRaw_preMul.io.toPostMul, postmul_regs).bits
  mulAddRecFNToRaw_postMul.io.mulAddResult := Pipe(io.validin, mulAddResult, postmul_regs).bits
  mulAddRecFNToRaw_postMul.io.roundingMode := Pipe(io.validin, io.roundingMode, postmul_regs).bits
  roundingMode_stage0                      := Pipe(io.validin, io.roundingMode, postmul_regs).bits
  detectTininess_stage0                    := Pipe(io.validin, io.detectTininess, postmul_regs).bits
  valid_stage0                             := Pipe(io.validin, false.B, postmul_regs).valid

  // ------------------------------------------------------------------------
  // ------------------------------------------------------------------------

  val roundRawFNToRecFN = Module(new hardfloat.RoundRawFNToRecFN(expWidth, sigWidth, 0))

  val round_regs = if (latency == 2) 1 else 0
  roundRawFNToRecFN.io.invalidExc     := Pipe(valid_stage0, mulAddRecFNToRaw_postMul.io.invalidExc, round_regs).bits
  roundRawFNToRecFN.io.in             := Pipe(valid_stage0, mulAddRecFNToRaw_postMul.io.rawOut, round_regs).bits
  roundRawFNToRecFN.io.roundingMode   := Pipe(valid_stage0, roundingMode_stage0, round_regs).bits
  roundRawFNToRecFN.io.detectTininess := Pipe(valid_stage0, detectTininess_stage0, round_regs).bits
  io.validout                         := Pipe(valid_stage0, false.B, round_regs).valid

  roundRawFNToRecFN.io.infiniteExc := false.B

  io.out            := roundRawFNToRecFN.io.out
  io.exceptionFlags := roundRawFNToRecFN.io.exceptionFlags
}
