// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.{Instantiate, instantiable}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util.{Cat, Pipe, Valid}


case class FPUFMAPipeParameter(latency: Int, t: FType) extends SerializableModuleParameter {
  val fLen: Int = ???
  val xLen: Int = ???
  val minFLen: Int = ???
  require(latency>0)
}

class FPUFMAPipeInterface(parameter: FPUFMAPipeParameter) extends Bundle {
  val in = Flipped(Valid(new FPInput(parameter.fLen)))
  val out = Valid(new FPResult(parameter.fLen))
}

@instantiable
class FPUFMAPipe(val parameter: FPUFMAPipeParameter)
  extends FixedIORawModule(new FPUFMAPipeInterface(parameter))
    with SerializableModule[FPUFMAPipeParameter] {

  val fLen = parameter.fLen
  val t = parameter.t
  val minFLen: Int = parameter.minFLen
  val xLen: Int = parameter.xLen
  val latency: Int = parameter.latency
  val helper = new FPUHelper(minFLen, fLen, xLen)
  def sanitizeNaN(x: UInt, t: FType): UInt = helper.sanitizeNaN(x, t)

  val valid = RegNext(io.in.valid)
  val in = Reg(new FPInput(fLen))
  when (io.in.valid) {
    val one = 1.U << (t.sig + t.exp - 1)
    val zero = (io.in.bits.in1 ^ io.in.bits.in2) & (1.U << (t.sig + t.exp))
    val cmd_fma = io.in.bits.fpuControl.ren3
    val cmd_addsub = io.in.bits.fpuControl.swap23
    in := io.in.bits
    when (cmd_addsub) { in.in2 := one }
    when (!(cmd_fma || cmd_addsub)) { in.in3 := zero }
  }

  val fma: MulAddRecFNPipe = Instantiate(new MulAddRecFNPipe(MulAddRecFNPipeParameter((latency-1) min 2, t.exp, t.sig)))
  fma.io.validin := valid
  fma.io.op := in.fmaCmd
  fma.io.roundingMode := in.rm
  fma.io.detectTininess := hardfloat.consts.tininess_afterRounding
  fma.io.a := in.in1
  fma.io.b := in.in2
  fma.io.c := in.in3

  val res = Wire(new FPResult(parameter.fLen))
  res.data := sanitizeNaN(fma.io.out, t)
  res.exc := fma.io.exceptionFlags

  io.out := Pipe(fma.io.validout, res, (latency-3) max 0)
}