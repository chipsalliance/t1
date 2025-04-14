// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.t1rocketemu

import chisel3._
import chisel3.experimental.hierarchy.instantiable
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import org.chipsalliance.rocketv.{FPUHelper, FType}

object FPToIEEEParameter {
  implicit def rwP: upickle.default.ReadWriter[FPToIEEEParameter] = upickle.default.macroRW[FPToIEEEParameter]
}

class FPToIEEEInput(fLen: Int)                        extends Bundle {
  val typeTag = UInt(2.W)
  val data    = UInt((fLen + 1).W)
}

case class FPToIEEEParameter(
  useAsyncReset: Boolean,
  xLen:          Int,
  fLen:          Int,
  minFLen:       Int)
    extends SerializableModuleParameter
class FPToIEEEInterface(parameter: FPToIEEEParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val in    = Flipped(Valid(new FPToIEEEInput(parameter.fLen)))
  val out   = Valid(UInt(parameter.fLen.W))
}

@instantiable
class FPToIEEE(val parameter: FPToIEEEParameter)
    extends FixedIORawModule(new FPToIEEEInterface(parameter))
    with SerializableModule[FPToIEEEParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val minFLen: Int = parameter.minFLen
  val fLen:    Int = parameter.fLen
  val xLen:    Int = parameter.xLen
  val helper                            = new FPUHelper(minFLen, fLen, xLen)
  val maxExpWidth                       = helper.maxExpWidth
  val maxSigWidth                       = helper.maxSigWidth
  val floatTypes                        = helper.floatTypes
  val maxType                           = helper.maxType
  val minXLen                           = helper.minXLen
  val nIntTypes                         = helper.nIntTypes
  def ieee(x: UInt, t: FType = maxType) = helper.ieee(x, t)

  val in    = io.in.bits
  val valid = io.in.valid

  def sextTo(x: UInt, n: Int): UInt = {
    require(x.getWidth <= n)
    if (x.getWidth == n) x
    else Cat(Fill(n - x.getWidth, x(x.getWidth - 1)), x)
  }

  val store = VecInit(
    floatTypes.map(t =>
      if (t == FType.H) Fill(maxType.ieeeWidth / minXLen, sextTo(ieee(in.data)(15, 0), minXLen))
      else Fill(maxType.ieeeWidth / t.ieeeWidth, ieee(in.data)(t.ieeeWidth - 1, 0))
    )
  )(in.typeTag)

  io.out.valid := valid
  io.out.bits  := store
}
