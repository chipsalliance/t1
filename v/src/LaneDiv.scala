package v

import chisel3._
import chisel3.util._
import division.srt.srt16.SRT16
import division.srt.{SRT, SRTOutput}

class LaneDivRequest(param: DataPathParam) extends Bundle {
  val src:  Vec[UInt] = Vec(2, UInt(param.dataWidth.W))
  val rem:  Bool = Bool()
  val sign: Bool = Bool()
  val index = UInt(2.W)
}

class LaneDiv(param: DataPathParam) extends Module {
  val req:  DecoupledIO[LaneDivRequest] = IO(Flipped(Decoupled(new LaneDivRequest(param))))
  val vSew: UInt = IO(Input(UInt(2.W)))
  // mask for sew
  val mask: UInt = IO(Input(UInt(param.dataWidth.W)))
  val resp: ValidIO[UInt] = IO(Valid(UInt(param.dataWidth.W)))
  val index = IO(Output(UInt(2.W)))
  val busy: Bool = IO(Output(Bool()))

  val sign1h: UInt = mask & (~mask >> 1).asUInt

  val srcExtend: IndexedSeq[UInt] = req.bits.src.map { src =>
    val signValue:  Bool = (sign1h & src).orR
    val signExtend: UInt = Fill(param.dataWidth, signValue)
    (src & mask) | (signExtend & (~mask).asUInt)
  }

  val wrapper = Module(new SRTWrapper)
  wrapper.input.bits.dividend := req.bits.src.last.asSInt
  wrapper.input.bits.divisor := req.bits.src.head.asSInt
  wrapper.input.bits.signIn := req.bits.sign
  wrapper.input.valid := req.valid

  val remReg:   Bool = RegEnable(req.bits.rem, false.B, req.fire)
  val indexReg: UInt = RegEnable(req.bits.index, 0.U, req.fire)
  busy := RegEnable(req.fire, false.B, req.fire ^ resp.valid)

  index := indexReg
  req.ready := wrapper.input.ready
  resp.valid := wrapper.output.valid
  resp.bits := Mux(remReg, wrapper.output.bits.reminder.asUInt, wrapper.output.bits.quotient.asUInt)
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

/** 32-bits Divider for signed and unsigned division based on SRT4
  *
  * Input:
  * dividend and divisor
  * sign: true for signed input
  *
  * Component:
  * {{{
  * divided by zero detection
  * bigger divisor detection
  * leading zero process
  * sign process
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

  /** Leading Zero component */
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
  leftShiftWidthDividend := zeroHeadDividend +& -Cat(0.U(4.W), guardWidth)
  leftShiftWidthDivisor := zeroHeadDivisor(4, 0)
  val rightshift = leftShiftWidthDividend(5)
  val dividendAppend = dividend(2, 0)
  val appendwidth = Mux(rightshift, (-leftShiftWidthDividend)(1, 0), 0.U(2.W))

  // keep mutiple cycles for SRT
  val negativeSRT = RegEnable(negative, srt.input.fire)
  val zeroHeadDivisorSRT = RegEnable(zeroHeadDivisor, srt.input.fire)
  val dividendSignSRT = RegEnable(abs.io.aSign, srt.input.fire)

  // keep for one cycle
  val divideZeroReg = RegEnable(divideZero, false.B, input.fire)
  val biggerdivisorReg = RegEnable(biggerdivisor, false.B, input.fire)
  val bypassSRTReg = RegNext(bypassSRT, false.B)
  val dividendInputReg = RegEnable(input.bits.dividend.asUInt, 0.U(32.W), input.fire)

  // do SRT

  srt.input.bits.dividend := Mux(
    rightshift,
    abs.io.aOut >> (-leftShiftWidthDividend)(1, 0),
    abs.io.aOut << leftShiftWidthDividend(4, 0)
  )
  srt.input.bits.divider := abs.io.bOut << leftShiftWidthDivisor
  srt.input.bits.counter := counter
  srt.dividendAppend := dividendAppend
  srt.appendWidth := appendwidth

  // if dividezero or biggerdivisor, bypass SRT
  srt.input.valid := input.valid && !bypassSRT
  // copy srt ready to top
  input.ready := srt.input.ready

  // post-process for sign
  val quotientAbs = Wire(UInt(32.W))
  val remainderAbs = Wire(UInt(32.W))
  quotientAbs := srt.output.bits.quotient
  remainderAbs := srt.output.bits.reminder >> zeroHeadDivisorSRT(4, 0)

  output.valid := srt.output.valid | bypassSRTReg
  // the quotient of division by zero has all bits set, and the remainder of division by zero equals the dividend.
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
  io.aSign := Mux(io.signIn, aSign, false.B)
  io.bSign := Mux(io.signIn, bSign, false.B)
}

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
  val _v: UInt = !(!(a(7) | a(6)) & !(a(5) | a(4))) | !(!(a(3) | a(2)) & !(a(1) | a(0)))

  io.z := Cat(~z2, ~z1, ~z0)
  io.v := _v
}

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
  val z3 = Mux(flag, 1.U, 0.U)
  val z2 = Mux(flag, L1.io.z(2), L0.io.z(2))
  val z1 = Mux(flag, L1.io.z(1), L0.io.z(1))
  val z0 = Mux(flag, L1.io.z(0), L0.io.z(0))

  io.z := Cat(z3, z2, z1, z0)
  io.v := L1.io.v | L0.io.v
}
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
  val z4 = Mux(flag, 1.U, 0.U)
  val z3 = Mux(flag, L1.io.z(3), L0.io.z(3))
  val z2 = Mux(flag, L1.io.z(2), L0.io.z(2))
  val z1 = Mux(flag, L1.io.z(1), L0.io.z(1))
  val z0 = Mux(flag, L1.io.z(0), L0.io.z(0))

  io.z := Cat(z4, z3, z2, z1, z0)
  io.v := L1.io.v | L0.io.v
}
