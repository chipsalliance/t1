// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import division.srt.{SRT, SRTOutput}
import org.chipsalliance.t1.rtl.decoder._

object LaneDivParam {
  implicit def rw: upickle.default.ReadWriter[LaneDivParam] = upickle.default.macroRW
}
case class LaneDivParam(datapathWidth: Int, latency: Int) extends VFUParameter with SerializableModuleParameter {
  val decodeField: BoolField = Decoder.divider
  val inputBundle  = new LaneDivRequest(datapathWidth)
  val outputBundle = new LaneDivResponse(datapathWidth)
  override val NeedSplit:   Boolean = true
  override val singleCycle: Boolean = false
}

class LaneDivRequest(datapathWidth: Int) extends VFUPipeBundle {
  val src: Vec[UInt] = Vec(2, UInt(datapathWidth.W))
  val opcode = UInt(4.W)
  val sign:         Bool = Bool()
  // execute index in group
  val executeIndex: UInt = UInt(2.W)
  // csr
  // val vSew: UInt = UInt(2.W)
}

class LaneDivResponse(datapathWidth: Int) extends VFUPipeBundle {
  val data:         UInt = UInt(datapathWidth.W)
  val executeIndex: UInt = UInt(2.W)
  val busy:         Bool = Bool()
}

@instantiable
class LaneDiv(val parameter: LaneDivParam) extends VFUModule(parameter) with SerializableModule[LaneDivParam] {
  val response:      LaneDivResponse = Wire(new LaneDivResponse(parameter.datapathWidth))
  val responseValid: Bool            = Wire(Bool())
  val request:       LaneDivRequest  = connectIO(response, responseValid).asTypeOf(parameter.inputBundle)

  val wrapper = Instantiate(new SRTWrapper)
  wrapper.input.bits.dividend := request.src.last.asSInt
  wrapper.input.bits.divisor  := request.src.head.asSInt
  wrapper.input.bits.signIn   := request.sign
  wrapper.input.valid         := requestRegValid

  val requestFire: Bool = vfuRequestFire
  val remReg:      Bool = RegEnable(request.opcode === 1.U, false.B, requestFire)
  val indexReg:    UInt = RegEnable(request.executeIndex, 0.U, requestFire)
  response.busy := RegEnable(requestFire, false.B, requestFire ^ responseIO.valid)

  response.executeIndex := indexReg
  vfuRequestReady.foreach(_ := wrapper.input.ready)
  responseValid         := wrapper.output.valid
  response.data         := Mux(remReg, wrapper.output.bits.reminder.asUInt, wrapper.output.bits.quotient.asUInt)
}

class SRTIn extends Bundle {
  val dividend = SInt(32.W)
  val divisor  = SInt(32.W)
  val signIn   = Bool()
}

class SRTOut extends Bundle {
  val reminder = SInt(32.W)
  val quotient = SInt(32.W)
}

/** 32-bits Divider for signed and unsigned division based on SRT16 with CSA
  *
  * Input: dividend and divisor signIn: true for signed input
  *
  * Component:
  * {{{
  * divided by zero detection
  * bigger divisor detection
  * SRT16 initial process logic containing a leading zero counter
  * SRT16 recurrence module imported from dependencies/arithmetic
  * SRT16 post-process logic
  * }}}
  */
@instantiable
class SRTWrapper extends Module {
  @public
  val input  = IO(Flipped(DecoupledIO(new SRTIn)))
  @public
  val output = IO(ValidIO(new SRTOut))

  val abs: Instance[Abs] = Instantiate(new Abs(32))
  abs.io.aIn    := input.bits.dividend
  abs.io.bIn    := input.bits.divisor
  abs.io.signIn := input.bits.signIn
  val negative = abs.io.aSign ^ abs.io.bSign

  val srt: SRT = Module(new SRT(32, 32, 32, radixLog2 = 4))

  /** divided by zero detection */
  val divideZero = (input.bits.divisor === 0.S)

  /** bigger divisor detection */
  val dividend      = Wire(UInt(33.W))
  val divisor       = Wire(UInt(33.W))
  val gap           = Wire(UInt(34.W))
  val biggerdivisor = Wire(Bool())
  dividend      := abs.io.aOut
  divisor       := abs.io.bOut
  gap           := divisor +& (-dividend)
  biggerdivisor := gap(33) && !(gap(32, 0).orR === false.B)

  // bypass
  val bypassSRT = (divideZero || biggerdivisor) && input.fire

  /** SRT16 initial process logic containing a leading zero counter */
  // extend one bit for calculation
  val zeroHeadDividend = Wire(UInt(6.W))
  val zeroHeadDivisor  = Wire(UInt(6.W))
  zeroHeadDividend := float.countLeadingZeros(abs.io.aOut)
  zeroHeadDivisor  := float.countLeadingZeros(abs.io.bOut)
  // sub = zeroHeadDivider - zeroHeadDividend
  val sub = Wire(UInt(6.W))
  sub := (-zeroHeadDividend) +& zeroHeadDivisor
  // needComputerWidth: Int = zeroHeadDivider - zeroHeadDividend + 2
  val needComputerWidth = Wire(UInt(7.W))
  needComputerWidth := sub +& 2.U
  // guardWidth = needComputerWidth % 4
  val guardSele  = UIntToOH(needComputerWidth(1, 0))
  val guardWidth = Mux1H(
    Seq(
      guardSele(0) -> 0.U(2.W),
      guardSele(1) -> 3.U(2.W),
      guardSele(2) -> 2.U(2.W),
      guardSele(3) -> 1.U(2.W)
    )
  )
  // counter: Int = (needComputerWidth + guardWidth) / radixLog2
  val counter    = ((needComputerWidth +& guardWidth) >> 2).asUInt

  val leftShiftWidthDividend = Wire(UInt(6.W))
  val leftShiftWidthDivisor  = Wire(UInt(6.W))
  leftShiftWidthDividend := zeroHeadDividend +& -Cat(0.U(4.W), guardWidth) + 3.U
  leftShiftWidthDivisor  := zeroHeadDivisor(4, 0)

  // control signals used in SRT post-process
  val negativeSRT        = RegEnable(negative, srt.input.fire)
  val zeroHeadDivisorSRT = RegEnable(zeroHeadDivisor, srt.input.fire)
  val dividendSignSRT    = RegEnable(abs.io.aSign, srt.input.fire)

  // keep for one cycle
  val divideZeroReg    = RegEnable(divideZero, false.B, input.fire)
  val biggerdivisorReg = RegEnable(biggerdivisor, false.B, input.fire)
  val bypassSRTReg     = RegNext(bypassSRT, false.B)
  val dividendInputReg = RegEnable(input.bits.dividend.asUInt, 0.U(32.W), input.fire)

  // SRT16 recurrence module input
  srt.input.bits.dividend := abs.io.aOut << leftShiftWidthDividend
  srt.input.bits.divider  := abs.io.bOut << leftShiftWidthDivisor
  srt.input.bits.counter  := counter

  // if dividezero or biggerdivisor, bypass SRT
  srt.input.valid := input.valid && !bypassSRT
  input.ready     := srt.input.ready

  // calculate quotient and remainder in ABS
  val quotientAbs  = Wire(UInt(32.W))
  val remainderAbs = Wire(UInt(32.W))
  quotientAbs  := srt.output.bits.quotient
  remainderAbs := srt.output.bits.reminder >> zeroHeadDivisorSRT(4, 0)

  /** DIV output
    *
    * when divisor equals to zero, the quotient has all bits set and the remainder equals the dividend
    */
  output.valid := srt.output.valid | bypassSRTReg

  output.bits.quotient := Mux(
    divideZeroReg,
    "hffffffff".U(32.W),
    Mux(
      biggerdivisorReg,
      0.U,
      Mux(negativeSRT, -quotientAbs, quotientAbs)
    )
  ).asSInt
  output.bits.reminder := Mux(
    divideZeroReg || biggerdivisorReg,
    dividendInputReg,
    Mux(dividendSignSRT, -remainderAbs, remainderAbs)
  ).asSInt
}

@instantiable
class Abs(n: Int) extends Module {
  @public
  val io    = IO(new Bundle() {
    val aIn    = Input(SInt(n.W))
    val bIn    = Input(SInt(n.W))
    val signIn = Input(Bool())
    val aOut   = Output(UInt(n.W))
    val bOut   = Output(UInt(n.W))
    val aSign  = Output(Bool())
    val bSign  = Output(Bool())
  })
  val a     = Wire(SInt(n.W))
  val b     = Wire(SInt(n.W))
  val aSign = io.aIn(n - 1)
  val bSign = io.bIn(n - 1)
  a        := io.aIn
  b        := io.bIn
  io.aOut  := Mux(io.signIn && aSign, -a, a).asUInt
  io.bOut  := Mux(io.signIn && bSign, -b, b).asUInt
  io.aSign := io.signIn && aSign
  io.bSign := io.signIn && bSign
}
