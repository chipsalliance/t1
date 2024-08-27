// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.instantiable
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._

object FPToIntParameter {
  implicit def rwP: upickle.default.ReadWriter[FPToIntParameter] = upickle.default.macroRW[FPToIntParameter]
}

case class FPToIntParameter(
  useAsyncReset: Boolean,
  xLen:          Int,
  fLen:          Int,
  minFLen:       Int)
    extends SerializableModuleParameter
class FPToIntInterface(parameter: FPToIntParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val in = Flipped(Valid(new FPInput(parameter.fLen)))
  val out = Valid(new FPToIntOutput(parameter.fLen, parameter.xLen))
}

@instantiable
class FPToInt(val parameter: FPToIntParameter)
    extends FixedIORawModule(new FPToIntInterface(parameter))
    with SerializableModule[FPToIntParameter]
    with Public
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val minFLen: Int = parameter.minFLen
  val fLen:    Int = parameter.fLen
  val xLen:    Int = parameter.xLen
  val helper = new FPUHelper(minFLen, fLen, xLen)
  val maxExpWidth = helper.maxExpWidth
  val maxSigWidth = helper.maxSigWidth
  val floatTypes = helper.floatTypes
  val maxType = helper.maxType
  val minXLen = helper.minXLen
  val nIntTypes = helper.nIntTypes
  def ieee(x: UInt, t: FType = maxType) = helper.ieee(x, t)

  val in = RegEnable(io.in.bits, io.in.valid)
  val valid = RegNext(io.in.valid)

  def sextTo(x: UInt, n: Int): UInt = {
    require(x.getWidth <= n)
    if (x.getWidth == n) x
    else Cat(Fill(n - x.getWidth, x(x.getWidth - 1)), x)
  }

  val dcmp = Module(new hardfloat.CompareRecFN(maxExpWidth, maxSigWidth))
  dcmp.io.a := in.in1
  dcmp.io.b := in.in2
  dcmp.io.signaling := !in.rm(1)

  val tag = in.fpuControl.typeTagOut
  val store = VecInit(
    floatTypes.map(t =>
      if (t == FType.H) Fill(maxType.ieeeWidth / minXLen, sextTo(ieee(in.in1)(15, 0), minXLen))
      else Fill(maxType.ieeeWidth / t.ieeeWidth, ieee(in.in1)(t.ieeeWidth - 1, 0))
    )
  )(tag)
  val toint = WireDefault(store)
  val intType = WireDefault(in.fmt(0))
  io.out.bits.store := store
  io.out.bits.toint := VecInit(
    (0 until helper.nIntTypes).map(i => sextTo(toint((helper.minXLen << i) - 1, 0), xLen)): Seq[UInt]
  )(intType)
  io.out.bits.exc := 0.U

  when(in.rm(0)) {
    val classify_out = VecInit(floatTypes.map(t => t.classify(maxType.unsafeConvert(in.in1, t))))(tag)
    toint := classify_out | (store >> minXLen << minXLen)
    intType := false.B
  }

  when(in.fpuControl.wflags) { // feq/flt/fle, fcvt
    toint := (~in.rm & Cat(dcmp.io.lt, dcmp.io.eq)).orR | (store >> minXLen << minXLen)
    io.out.bits.exc := dcmp.io.exceptionFlags
    intType := false.B

    when(!in.fpuControl.ren2) { // fcvt
      val cvtType = if (log2Ceil(nIntTypes) == 0) 0.U else in.typ(log2Ceil(nIntTypes), 1)
      intType := cvtType
      val conv = Module(new hardfloat.RecFNToIN(maxExpWidth, maxSigWidth, xLen))
      conv.io.in := in.in1
      conv.io.roundingMode := in.rm
      conv.io.signedOut := ~in.typ(0)
      toint := conv.io.out
      io.out.bits.exc := Cat(conv.io.intExceptionFlags(2, 1).orR, 0.U(3.W), conv.io.intExceptionFlags(0))

      for (i <- 0 until nIntTypes - 1) {
        val w = minXLen << i
        when(cvtType === i.U) {
          val narrow = Module(new hardfloat.RecFNToIN(maxExpWidth, maxSigWidth, w))
          narrow.io.in := in.in1
          narrow.io.roundingMode := in.rm
          narrow.io.signedOut := ~in.typ(0)

          val excSign = in.in1(maxExpWidth + maxSigWidth) && !maxType.isNaN(in.in1)
          val excOut = Cat(conv.io.signedOut === excSign, Fill(w - 1, !excSign))
          val invalid = conv.io.intExceptionFlags(2) || narrow.io.intExceptionFlags(1)
          when(invalid) { toint := Cat(conv.io.out >> w, excOut) }
          io.out.bits.exc := Cat(invalid, 0.U(3.W), !invalid && conv.io.intExceptionFlags(0))
        }
      }
    }
  }

  io.out.valid := valid
  io.out.bits.lt := dcmp.io.lt || (dcmp.io.a.asSInt < 0.S && dcmp.io.b.asSInt >= 0.S)
  io.out.bits.in := in
}
