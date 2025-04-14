// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.instantiable
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util.{Cat, Pipe, Valid}

object FPToFPParameter {
  implicit def rwP: upickle.default.ReadWriter[FPToFPParameter] = upickle.default.macroRW[FPToFPParameter]
}

case class FPToFPParameter(
  useAsyncReset: Boolean,
  latency:       Int,
  xLen:          Int,
  fLen:          Int,
  minFLen:       Int)
    extends SerializableModuleParameter

class FPToFPInterface(parameter: FPToFPParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val in    = Flipped(Valid(new FPInput(parameter.fLen)))
  val out   = Valid(new FPResult(parameter.fLen))
  val lt    = Input(Bool()) // from FPToInt
}

@instantiable
class FPToFP(val parameter: FPToFPParameter)
    extends FixedIORawModule(new FPToFPInterface(parameter))
    with SerializableModule[FPToFPParameter]
    with Public
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val fLen                           = parameter.fLen
  val minFLen                        = parameter.minFLen
  val xLen                           = parameter.xLen
  val latency                        = parameter.latency
  val helper                         = new FPUHelper(minFLen, fLen, xLen)
  val maxType                        = helper.maxType
  val floatTypes                     = helper.floatTypes
  def typeTag(t:     FType)          = helper.typeTag(t)
  def sanitizeNaN(x: UInt, t: FType) = helper.sanitizeNaN(x, t)

  val in = Pipe(io.in)

  val signNum = Mux(in.bits.rm(1), in.bits.in1 ^ in.bits.in2, Mux(in.bits.rm(0), ~in.bits.in2, in.bits.in2))
  val fsgnj   = Cat(signNum(fLen), in.bits.in1(fLen - 1, 0))

  val fsgnjMux = Wire(new FPResult(parameter.fLen))
  fsgnjMux.exc  := 0.U
  fsgnjMux.data := fsgnj

  when(in.bits.fpuControl.wflags) { // fmin/fmax
    val isnan1    = maxType.isNaN(in.bits.in1)
    val isnan2    = maxType.isNaN(in.bits.in2)
    val isInvalid = maxType.isSNaN(in.bits.in1) || maxType.isSNaN(in.bits.in2)
    val isNaNOut  = isnan1 && isnan2
    val isLHS     = isnan2 || in.bits.rm(0) =/= io.lt && !isnan1
    fsgnjMux.exc  := isInvalid << 4
    fsgnjMux.data := Mux(isNaNOut, maxType.qNaN, Mux(isLHS, in.bits.in1, in.bits.in2))
  }

  val inTag  = in.bits.fpuControl.typeTagIn
  val outTag = in.bits.fpuControl.typeTagOut
  val mux    = WireDefault(fsgnjMux)
  for (t <- floatTypes.init) {
    when(outTag === typeTag(t).U) {
      mux.data := Cat(fsgnjMux.data >> t.recodedWidth, maxType.unsafeConvert(fsgnjMux.data, t))
    }
  }

  when(in.bits.fpuControl.wflags && !in.bits.fpuControl.ren2) { // fcvt
    if (floatTypes.size > 1) {
      // widening conversions simply canonicalize NaN operands
      val widened = Mux(maxType.isNaN(in.bits.in1), maxType.qNaN, in.bits.in1)
      fsgnjMux.data := widened
      fsgnjMux.exc  := maxType.isSNaN(in.bits.in1) << 4

      // narrowing conversions require rounding (for RVQ, this could be
      // optimized to use a single variable-position rounding unit, rather
      // than two fixed-position ones)
      for (outType <- floatTypes.init)
        when(outTag === typeTag(outType).U && ((typeTag(outType) == 0).B || outTag < inTag)) {
          val narrower = Module(new hardfloat.RecFNToRecFN(maxType.exp, maxType.sig, outType.exp, outType.sig))
          narrower.io.in             := in.bits.in1
          narrower.io.roundingMode   := in.bits.rm
          narrower.io.detectTininess := hardfloat.consts.tininess_afterRounding
          val narrowed = sanitizeNaN(narrower.io.out, outType)
          mux.data := Cat(fsgnjMux.data >> narrowed.getWidth, narrowed)
          mux.exc  := narrower.io.exceptionFlags
        }
    }
  }

  io.out <> Pipe(in.valid, mux, latency - 1)
}
