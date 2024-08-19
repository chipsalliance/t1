// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.instantiable
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util.{BitPat, Cat, Decoupled, Enum, Fill, Log2, log2Ceil, log2Floor}

object MulDivParameter {
  implicit def rwP: upickle.default.ReadWriter[MulDivParameter] = upickle.default.macroRW[MulDivParameter]
}

case class MulDivParameter(useAsyncReset: Boolean,
                           latency: Int,
                           xLen: Int,
                           divUnroll: Int,
                           divEarlyOut: Boolean,
                           divEarlyOutGranularity: Int,
                           mulUnroll: Int,
                           mulEarlyOut: Boolean,
                           decoderParameter: DecoderParameter)
  extends SerializableModuleParameter {
  // optional to 16 when rve?
  val nXpr:     Int = 32
  val uopWidth: Int = 4

  def FN_MUL = decoderParameter.UOPALU.mul
  def FN_MULH = decoderParameter.UOPALU.mulh
  def FN_MULHU = decoderParameter.UOPALU.mulhu
  def FN_MULHSU = decoderParameter.UOPALU.mulhsu
  def FN_DIV = decoderParameter.UOPALU.div
  def FN_REM = decoderParameter.UOPALU.rem
  def FN_DIVU = decoderParameter.UOPALU.divu
  def FN_REMU = decoderParameter.UOPALU.remu
  def DW_32 = false.B
}
class MulDivInterface(parameter: MulDivParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val req = Flipped(Decoupled(new MultiplierReq(parameter.xLen, log2Ceil(parameter.nXpr), parameter.uopWidth)))
  val kill = Input(Bool())
  val resp = Decoupled(new MultiplierResp(parameter.xLen, log2Ceil(parameter.nXpr)))
}

@instantiable
class MulDiv(val parameter: MulDivParameter)
    extends FixedIORawModule(new MulDivInterface(parameter))
    with SerializableModule[MulDivParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  object cfg {
    val divUnroll = parameter.divUnroll
    val divEarlyOut = parameter.divEarlyOut
    val divEarlyOutGranularity = parameter.divEarlyOutGranularity
    val mulUnroll = parameter.mulUnroll
    val mulEarlyOut = parameter.mulEarlyOut
  }

  def N = BitPat.N()
  def Y = BitPat.Y()
  def X = BitPat.dontCare(1)

  val w = io.req.bits.in1.getWidth
  val mulw = if (cfg.mulUnroll == 0) w else (w + cfg.mulUnroll - 1) / cfg.mulUnroll * cfg.mulUnroll
  val fastMulW = if (cfg.mulUnroll == 0) false else w / 2 > cfg.mulUnroll && w % (2 * cfg.mulUnroll) == 0

  val s_ready :: s_neg_inputs :: s_mul :: s_div :: s_dummy :: s_neg_output :: s_done_mul :: s_done_div :: Nil = Enum(8)
  val state = RegInit(s_ready)

  val req = Reg(chiselTypeOf(io.req.bits))
  val count = Reg(
    UInt(
      log2Ceil(
        (Option.when(cfg.divUnroll != 0)(w / cfg.divUnroll + 1).toSeq ++
          Option.when(cfg.mulUnroll != 0)(mulw / cfg.mulUnroll)).reduce(_ max _)
      ).W
    )
  )
  val neg_out = Reg(Bool())
  val isHi = Reg(Bool())
  val resHi = Reg(Bool())
  val divisor = Reg(UInt((w + 1).W)) // div only needs w bits
  val remainder = Reg(UInt((2 * mulw + 2).W)) // div only needs 2*w+1 bits

  val mulDecode = List(
    parameter.FN_MUL -> List(Y, N, X, X),
    parameter.FN_MULH -> List(Y, Y, Y, Y),
    parameter.FN_MULHU -> List(Y, Y, N, N),
    parameter.FN_MULHSU -> List(Y, Y, Y, N)
  )
  val divDecode = List(
    parameter.FN_DIV -> List(N, N, Y, Y),
    parameter.FN_REM -> List(N, Y, Y, Y),
    parameter.FN_DIVU -> List(N, N, N, N),
    parameter.FN_REMU -> List(N, Y, N, N)
  )
  // TODO: move these decoding to Decoder.
  val cmdMul :: cmdHi :: lhsSigned :: rhsSigned :: Nil =
    DecodeLogic(
      io.req.bits.fn,
      List(X, X, X, X),
      (if (cfg.divUnroll != 0) divDecode else Nil) ++ (if (cfg.mulUnroll != 0) mulDecode else Nil)
    ).map(_.asBool)

  require(w == 32 || w == 64)
  def halfWidth(req: MultiplierReq) = (w > 32).B && req.dw === parameter.DW_32

  def sext(x: Bits, halfW: Bool, signed: Bool) = {
    val sign = signed && Mux(halfW, x(w / 2 - 1), x(w - 1))
    val hi = Mux(halfW, Fill(w / 2, sign), x(w - 1, w / 2))
    (Cat(hi, x(w / 2 - 1, 0)), sign)
  }
  val (lhs_in, lhs_sign) = sext(io.req.bits.in1, halfWidth(io.req.bits), lhsSigned)
  val (rhs_in, rhs_sign) = sext(io.req.bits.in2, halfWidth(io.req.bits), rhsSigned)

  val subtractor = remainder(2 * w, w) - divisor
  val result = Mux(resHi, remainder(2 * w, w + 1), remainder(w - 1, 0))
  val negated_remainder = -result

  if (cfg.divUnroll != 0) when(state === s_neg_inputs) {
    when(remainder(w - 1)) {
      remainder := negated_remainder
    }
    when(divisor(w - 1)) {
      divisor := subtractor
    }
    state := s_div
  }
  if (cfg.divUnroll != 0) when(state === s_neg_output) {
    remainder := negated_remainder
    state := s_done_div
    resHi := false.B
  }
  if (cfg.mulUnroll != 0) when(state === s_mul) {
    val mulReg = Cat(remainder(2 * mulw + 1, w + 1), remainder(w - 1, 0))
    val mplierSign = remainder(w)
    val mplier = mulReg(mulw - 1, 0)
    val accum = mulReg(2 * mulw, mulw).asSInt
    val mpcand = divisor.asSInt
    val prod = Cat(mplierSign, mplier(cfg.mulUnroll - 1, 0)).asSInt * mpcand + accum
    val nextMulReg = Cat(prod, mplier(mulw - 1, cfg.mulUnroll))
    val nextMplierSign = count === (mulw / cfg.mulUnroll - 2).U && neg_out

    val eOutMask = ((BigInt(-1) << mulw).S >> (count * cfg.mulUnroll.U)(log2Ceil(mulw) - 1, 0))(mulw - 1, 0)
    val eOut = (cfg.mulEarlyOut).B && count =/= (mulw / cfg.mulUnroll - 1).U && count =/= 0.U &&
      !isHi && (mplier & ~eOutMask) === 0.U
    val eOutRes = (mulReg >> (mulw.U - count * cfg.mulUnroll.U)(log2Ceil(mulw) - 1, 0))
    val nextMulReg1 = Cat(nextMulReg(2 * mulw, mulw), Mux(eOut, eOutRes, nextMulReg)(mulw - 1, 0))
    remainder := Cat(nextMulReg1 >> w, nextMplierSign, nextMulReg1(w - 1, 0))

    count := count + 1.U
    when(eOut || count === (mulw / cfg.mulUnroll - 1).U) {
      state := s_done_mul
      resHi := isHi
    }
  }
  if (cfg.divUnroll != 0) when(state === s_div) {
    val unrolls = ((0 until cfg.divUnroll)
      .scanLeft(remainder)) {
        case (rem, i) =>
          // the special case for iteration 0 is to save HW, not for correctness
          val difference = if (i == 0) subtractor else rem(2 * w, w) - divisor(w - 1, 0)
          val less = difference(w)
          Cat(Mux(less, rem(2 * w - 1, w), difference(w - 1, 0)), rem(w - 1, 0), !less)
      }
      .tail

    remainder := unrolls.last
    when(count === (w / cfg.divUnroll).U) {
      state := Mux(neg_out, s_neg_output, s_done_div)
      resHi := isHi
      if (w % cfg.divUnroll < cfg.divUnroll - 1)
        remainder := unrolls(w % cfg.divUnroll)
    }
    count := count + 1.U

    val divby0 = count === 0.U && !subtractor(w)
    if (cfg.divEarlyOut) {
      val align = 1 << log2Floor(cfg.divUnroll.max(cfg.divEarlyOutGranularity))
      val alignMask = ~((align - 1).U(log2Ceil(w).W))
      val divisorMSB = Log2(divisor(w - 1, 0), w) & alignMask
      val dividendMSB = Log2(remainder(w - 1, 0), w) | ~alignMask
      val eOutPos = ~(dividendMSB - divisorMSB)
      val eOut = count === 0.U && !divby0 && eOutPos >= align.U
      when(eOut) {
        remainder := remainder(w - 1, 0) << eOutPos
        count := eOutPos >> log2Floor(cfg.divUnroll)
      }
    }
    when(divby0 && !isHi) { neg_out := false.B }
  }
  when(io.resp.fire || io.kill) {
    state := s_ready
  }
  when(io.req.fire) {
    state := Mux(cmdMul, s_mul, Mux(lhs_sign || rhs_sign, s_neg_inputs, s_div))
    isHi := cmdHi
    resHi := false.B
    count := (if (fastMulW) Mux[UInt](cmdMul && halfWidth(io.req.bits), (w / cfg.mulUnroll / 2).U, 0.U) else 0.U)
    neg_out := Mux(cmdHi, lhs_sign, lhs_sign =/= rhs_sign)
    divisor := Cat(rhs_sign, rhs_in)
    remainder := lhs_in
    req := io.req.bits
  }

  val outMul = (state & (s_done_mul ^ s_done_div)) === (s_done_mul & ~s_done_div)
  val loOut = Mux(fastMulW.B && halfWidth(req) && outMul, result(w - 1, w / 2), result(w / 2 - 1, 0))
  val hiOut = Mux(halfWidth(req), Fill(w / 2, loOut(w / 2 - 1)), result(w - 1, w / 2))
  io.resp.bits.tag := req.tag

  io.resp.bits.data := Cat(hiOut, loOut)
  io.resp.bits.full_data := Cat(remainder(2*w, w+1), remainder(w-1, 0))
  io.resp.valid := (state === s_done_mul || state === s_done_div)
  io.req.ready := state === s_ready
}
