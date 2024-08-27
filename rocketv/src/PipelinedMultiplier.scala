// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.instantiable
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._

object PipelinedMultiplierParameter {
  implicit def rwP: upickle.default.ReadWriter[PipelinedMultiplierParameter] =
    upickle.default.macroRW[PipelinedMultiplierParameter]
}

case class PipelinedMultiplierParameter(
                                         useAsyncReset: Boolean,
                                         latency:       Int,
                                         xLen:         Int)
    extends SerializableModuleParameter {

  val nXpr:     Int = 32
  val uopWidth: Int = 4

  def FN_MUL = 0.U(4.W)
  def FN_MULH = 1.U(4.W)
  def FN_MULHSU = 2.U(4.W)
  def FN_MULHU = 3.U(4.W)

  def DW_32 = false.B
  def DW_64 = true.B
}
class PipelinedMultiplierInterface(parameter: PipelinedMultiplierParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val req = Flipped(Valid(new MultiplierReq(parameter.xLen, log2Ceil(parameter.nXpr), parameter.uopWidth)))
  val resp = Valid(new MultiplierResp(parameter.xLen, log2Ceil(parameter.nXpr)))
}

@instantiable
class PipelinedMultiplier(val parameter: PipelinedMultiplierParameter)
    extends FixedIORawModule(new PipelinedMultiplierInterface(parameter))
    with SerializableModule[PipelinedMultiplierParameter]
    with Public
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val width = parameter.xLen
  val latency = parameter.latency
  def N = BitPat.N()
  def Y = BitPat.N()
  def X = BitPat.dontCare(1)
  def sextTo(x: UInt, n: Int): UInt = {
    require(x.getWidth <= n)
    if (x.getWidth == n) x
    else Cat(Fill(n - x.getWidth, x(x.getWidth - 1)), x)
  }

  val in = Pipe(io.req)

  val decode = List(
    parameter.FN_MUL -> List(N, X, X),
    parameter.FN_MULH -> List(Y, Y, Y),
    parameter.FN_MULHU -> List(Y, N, N),
    parameter.FN_MULHSU -> List(Y, Y, N)
  )
  // TODO: move these decoding to Decoder.
  val cmdHi :: lhsSigned :: rhsSigned :: Nil =
    DecodeLogic(in.bits.fn, List(X, X, X), decode).map(_.asBool)
  val cmdHalf = (width > 32).B && in.bits.dw === parameter.DW_32

  val lhs = Cat(lhsSigned && in.bits.in1(width - 1), in.bits.in1).asSInt
  val rhs = Cat(rhsSigned && in.bits.in2(width - 1), in.bits.in2).asSInt
  val prod = lhs * rhs
  val muxed =
    Mux(cmdHi, prod(2 * width - 1, width), Mux(cmdHalf, sextTo(prod(width / 2 - 1, 0), width), prod(width - 1, 0)))

  val resp = Pipe(in, latency - 1)
  io.resp.valid := resp.valid
  io.resp.bits.tag := resp.bits.tag
  io.resp.bits.data := Pipe(in.valid, muxed, latency - 1).bits
  io.resp.bits.full_data := Pipe(in.valid, prod, latency - 1).bits.asUInt
}
