// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import division.srt.{SRT, SRTOutput}
object LaneDivParam {
  implicit def rw: upickle.default.ReadWriter[LaneDivParam] = upickle.default.macroRW
}
case class LaneDivParam(datapathWidth: Int) extends VFUParameter with SerializableModuleParameter {
  val decodeField: BoolField = Decoder.divider
  val inputBundle = new LaneDivRequest(datapathWidth)
  val outputBundle = new LaneDivResponse(datapathWidth)
}

class LaneDivRequest(datapathWidth: Int) extends Bundle {
  val src:  Vec[UInt] = Vec(2, UInt(datapathWidth.W))
  val rem:  Bool = Bool()
  val sign: Bool = Bool()
  // execute index in group
  val executeIndex: UInt = UInt(2.W)
  // csr
  // val vSew: UInt = UInt(2.W)
}

class LaneDivResponse(datapathWidth: Int) extends Bundle {
  val data: UInt = UInt(datapathWidth.W)
  val executeIndex: UInt = UInt(2.W)
  val busy: Bool = Bool()
}

class LaneDiv(val parameter: LaneDivParam) extends VFUModule(parameter) with SerializableModule[LaneDivParam] {
  val response: LaneDivResponse = Wire(new LaneDivResponse(parameter.datapathWidth))
  val request: LaneDivRequest = connectIO(response).asTypeOf(parameter.inputBundle)

  val wrapper = Module(new SRTWrapper)
  wrapper.input.bits.dividend := request.src.last.asSInt
  wrapper.input.bits.divisor := request.src.head.asSInt
  wrapper.input.bits.signIn := request.sign
  wrapper.input.valid := requestIO.valid

  val requestFire: Bool = requestIO.fire
  val remReg:   Bool = RegEnable(request.rem, false.B, requestFire)
  val indexReg: UInt = RegEnable(request.executeIndex, 0.U, requestFire)
  response.busy := RegEnable(requestFire, false.B, requestFire ^ responseIO.valid)

  response.executeIndex := indexReg
  requestIO.ready := wrapper.input.ready
  responseIO.valid := wrapper.output.valid
  response.data := Mux(remReg, wrapper.output.bits.reminder.asUInt, wrapper.output.bits.quotient.asUInt)
}

class SRTIn extends Bundle {
  val dividend = SInt(32.W)
  val divisor = SInt(32.W)
  val signIn = Bool()
}

class SRTOut extends Bundle {
  val reminder = SInt(32.W)
  val quotient = SInt(32.W)
}

/** 32-bits Divider for signed and unsigned division based on SRT16 with CSA
  *
  * Input:
  * dividend and divisor
  * signIn: true for signed input
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
class SRTWrapper extends Module {
  val input = IO(Flipped(DecoupledIO(new SRTIn)))
  val output = IO(ValidIO(new SRTOut))

  val abs = Module(new Abs(32))
  abs.io.aIn := input.bits.dividend
  abs.io.bIn := input.bits.divisor
  abs.io.signIn := input.bits.signIn
  val negative = abs.io.aSign ^ abs.io.bSign

  val LZC0 = Module(new LZC32)
  val LZC1 = Module(new LZC32)
  LZC0.io.a := abs.io.aOut
  LZC1.io.a := abs.io.bOut

  val srt: SRT = Module(new SRT(32, 32, 32, radixLog2 = 4))

  /** divided by zero detection */
  val divideZero = (input.bits.divisor === 0.S)

  /** bigger divisor detection */
  val dividend = Wire(UInt(33.W))
  val divisor = Wire(UInt(33.W))
  val gap = Wire(UInt(34.W))
  val biggerdivisor = Wire(Bool())
  dividend := abs.io.aOut
  divisor := abs.io.bOut
  gap := divisor +& (-dividend)
  biggerdivisor := gap(33) && !(gap(32, 0).orR === false.B)

  // bypass
  val bypassSRT = (divideZero || biggerdivisor) && input.fire

  /** SRT16 initial process logic containing a leading zero counter */
  // extend one bit for calculation
  val zeroHeadDividend = Wire(UInt(6.W))
  val zeroHeadDivisor = Wire(UInt(6.W))
  zeroHeadDividend := ~LZC0.io.z
  zeroHeadDivisor := ~LZC1.io.z
  // sub = zeroHeadDivider - zeroHeadDividend
  val sub = Wire(UInt(6.W))
  sub := (-zeroHeadDividend) +& zeroHeadDivisor
  // needComputerWidth: Int = zeroHeadDivider - zeroHeadDividend + 2
  val needComputerWidth = Wire(UInt(7.W))
  needComputerWidth := sub +& 2.U
  // noguard: Boolean =  needComputerWidth % 4 == 0
  val noguard = !needComputerWidth(0) && !needComputerWidth(1)
  // guardWidth: Int =  if (noguard) 0 else 4 - needComputerWidth % 4
  val guardWidth = Wire(UInt(2.W))
  guardWidth := Mux(noguard, 0.U, 4.U + -needComputerWidth(1, 0))
  // counter: Int = (needComputerWidth + guardWidth) / radixLog2
  val counter = ((needComputerWidth +& guardWidth) >> 2).asUInt

  val leftShiftWidthDividend = Wire(UInt(6.W))
  val leftShiftWidthDivisor = Wire(UInt(6.W))
  leftShiftWidthDividend := zeroHeadDividend +& -Cat(0.U(4.W), guardWidth) + 3.U
  leftShiftWidthDivisor := zeroHeadDivisor(4, 0)

  // control signals used in SRT post-process
  val negativeSRT = RegEnable(negative, srt.input.fire)
  val zeroHeadDivisorSRT = RegEnable(zeroHeadDivisor, srt.input.fire)
  val dividendSignSRT = RegEnable(abs.io.aSign, srt.input.fire)

  // keep for one cycle
  val divideZeroReg = RegEnable(divideZero, false.B, input.fire)
  val biggerdivisorReg = RegEnable(biggerdivisor, false.B, input.fire)
  val bypassSRTReg = RegNext(bypassSRT, false.B)
  val dividendInputReg = RegEnable(input.bits.dividend.asUInt, 0.U(32.W), input.fire)

  // SRT16 recurrence module input
  srt.input.bits.dividend := abs.io.aOut << leftShiftWidthDividend
  srt.input.bits.divider := abs.io.bOut << leftShiftWidthDivisor
  srt.input.bits.counter := counter

  // if dividezero or biggerdivisor, bypass SRT
  srt.input.valid := input.valid && !bypassSRT
  input.ready := srt.input.ready

  // calculate quotient and remainder in ABS
  val quotientAbs = Wire(UInt(32.W))
  val remainderAbs = Wire(UInt(32.W))
  quotientAbs := srt.output.bits.quotient
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

class Abs(n: Int) extends Module {
  val io = IO(new Bundle() {
    val aIn = Input(SInt(n.W))
    val bIn = Input(SInt(n.W))
    val signIn = Input(Bool())
    val aOut = Output(UInt(n.W))
    val bOut = Output(UInt(n.W))
    val aSign = Output(Bool())
    val bSign = Output(Bool())
  })
  val a = Wire(SInt(n.W))
  val b = Wire(SInt(n.W))
  val aSign = io.aIn(n - 1)
  val bSign = io.bIn(n - 1)
  a := io.aIn
  b := io.bIn
  io.aOut := Mux(io.signIn && aSign, -a, a).asUInt
  io.bOut := Mux(io.signIn && bSign, -b, b).asUInt
  io.aSign := io.signIn && aSign
  io.bSign := io.signIn && bSign
}

/** 8-bits Leading Zero Counter */
class LZC8 extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(8.W))
    val z = Output(UInt(3.W))
    val v = Output(UInt(1.W))
  })
  val a = io.a
  val z0: UInt = (!(a(7) | (!a(6)) & a(5))) & ((a(6) | a(4)) | !(a(3) | (!a(2) & a(1))))
  val z1: UInt = !(a(7) | a(6)) & ((a(5) | a(4)) | !(a(3) | a(2)))
  val z2: UInt = !(a(7) | a(6)) & !(a(5) | a(4))
  val v:  UInt = !(!(a(7) | a(6)) & !(a(5) | a(4))) | !(!(a(3) | a(2)) & !(a(1) | a(0)))

  io.z := Cat(~z2, ~z1, ~z0)
  io.v := v
}

/** 16-bits Leading Zero Counter */
class LZC16 extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(16.W))
    val z = Output(UInt(4.W))
    val v = Output(UInt(1.W))
  })
  val L0 = Module(new LZC8)
  val L1 = Module(new LZC8)
  L1.io.a := io.a(15, 8)
  L0.io.a := io.a(7, 0)

  val flag = L1.io.v.asBool
  val z3 = flag
  val z2 = Mux(flag, L1.io.z(2), L0.io.z(2))
  val z1 = Mux(flag, L1.io.z(1), L0.io.z(1))
  val z0 = Mux(flag, L1.io.z(0), L0.io.z(0))

  io.z := Cat(z3, z2, z1, z0)
  io.v := L1.io.v | L0.io.v
}

/** 32-bits Leading Zero Counter */
class LZC32 extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(32.W))
    val z = Output(UInt(5.W))
    val v = Output(UInt(1.W))
  })
  val L0 = Module(new LZC16)
  val L1 = Module(new LZC16)
  L1.io.a := io.a(31, 16)
  L0.io.a := io.a(15, 0)

  val flag = L1.io.v.asBool
  val z4 = flag
  val z3 = Mux(flag, L1.io.z(3), L0.io.z(3))
  val z2 = Mux(flag, L1.io.z(2), L0.io.z(2))
  val z1 = Mux(flag, L1.io.z(1), L0.io.z(1))
  val z0 = Mux(flag, L1.io.z(0), L0.io.z(0))

  io.z := Cat(z4, z3, z2, z1, z0)
  io.v := L1.io.v | L0.io.v
}
