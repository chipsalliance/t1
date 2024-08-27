// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.instantiable
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util.{Cat, Pipe, Valid, log2Ceil}

object IntToFPParameter {
  implicit def rwP: upickle.default.ReadWriter[IntToFPParameter] = upickle.default.macroRW[IntToFPParameter]
}

case class IntToFPParameter(
  useAsyncReset: Boolean,
  latency:       Int,
  fLen:          Int,
  xLen:          Int,
  minFLen:       Int)
    extends SerializableModuleParameter {
  val minXLen = 32
}
class IntToFPInterface(parameter: IntToFPParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val in = Flipped(Valid(new IntToFPInput(parameter.xLen)))
  val out = Valid(new FPResult(parameter.fLen))
}

@instantiable
class IntToFP(val parameter: IntToFPParameter)
    extends FixedIORawModule(new IntToFPInterface(parameter))
    with SerializableModule[IntToFPParameter]
    with Public
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  // retime
  val latency: Int = parameter.latency
  val fLen:    Int = parameter.fLen
  val minFLen: Int = parameter.minFLen
  val minXLen: Int = parameter.minXLen
  val xLen:    Int = parameter.xLen
  val helper = new FPUHelper(minFLen: Int, fLen: Int, xLen: Int)
  def recode(x: UInt, tag: UInt) = helper.recode(x, tag)
  val nIntTypes:  Int = helper.nIntTypes
  val floatTypes: Seq[FType] = helper.floatTypes
  def sanitizeNaN(x: UInt, t: FType) = helper.sanitizeNaN(x, t)

  val in = Pipe(io.in)
  val tag = in.bits.fpuControl.typeTagIn

  val mux = Wire(new FPResult(fLen))
  mux.exc := 0.U
  mux.data := recode(in.bits.in1, tag)

  val intValue = {
    val res = WireDefault(in.bits.in1.asSInt)
    for (i <- 0 until nIntTypes - 1) {
      val smallInt = in.bits.in1((minXLen << i) - 1, 0)
      when(in.bits.typ(log2Ceil(nIntTypes), 1) === i.U) {
        res := Mux(in.bits.typ(0), smallInt.zext, smallInt.asSInt)
      }
    }
    res.asUInt
  }

  when(in.bits.fpuControl.wflags) { // fcvt
    // could be improved for RVD/RVQ with a single variable-position rounding
    // unit, rather than N fixed-position ones
    val i2fResults = for (t <- floatTypes) yield {
      val i2f = Module(new hardfloat.INToRecFN(xLen, t.exp, t.sig))
      i2f.io.signedIn := ~in.bits.typ(0)
      i2f.io.in := intValue
      i2f.io.roundingMode := in.bits.rm
      i2f.io.detectTininess := hardfloat.consts.tininess_afterRounding
      (sanitizeNaN(i2f.io.out, t), i2f.io.exceptionFlags)
    }

    val (data, exc) = i2fResults.unzip
    val dataPadded = data.init.map(d => Cat(data.last >> d.getWidth, d)) :+ data.last
    mux.data := VecInit(dataPadded)(tag)
    mux.exc := VecInit(exc)(tag)
  }

  io.out <> Pipe(in.valid, mux, latency - 1)
}
