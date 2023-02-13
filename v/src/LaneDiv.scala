package v

import chisel3._
import chisel3.util._
import division.srt.{SRT, SRTOutput}

class LaneDivRequest(param: DataPathParam) extends Bundle {
  val src:  Vec[UInt] = Vec(2, UInt(param.dataWidth.W))
  val rem:  Bool = Bool()
  val sign: Bool = Bool()
}

class LaneDiv(param: DataPathParam) extends Module {
  val req:  DecoupledIO[LaneDivRequest] = IO(Flipped(Decoupled(new LaneDivRequest(param))))
  val vSew: UInt = IO(Input(UInt(2.W)))
  // mask for sew
  val mask: UInt = IO(Input(UInt(param.dataWidth.W)))
  val resp: ValidIO[UInt] = IO(Valid(UInt(param.dataWidth.W)))

  val sign1h: UInt = mask & (~mask >> 1).asUInt

  val srcExtend: IndexedSeq[UInt] = req.bits.src.map { src =>
    val signValue:  Bool = (sign1h & src).orR
    val signExtend: UInt = Fill(param.dataWidth, signValue)
    (src & mask) | (signExtend & (~mask).asUInt)
  }

  val div = Module(new SRTWrapper)
  div.input.bits.dividend := req.bits.src.last
  div.input.bits.divisor := req.bits.src.head
  div.input.valid := req.valid

  req.ready := div.input.ready

  val remReg: Bool = RegEnable(req.bits.rem, false.B, req.fire)

  resp.valid := div.output.valid
  resp.bits := Mux(remReg, div.output.bits.reminder, div.output.bits.quotient)
}

class SRTIn extends Bundle {
  val dividend = UInt(32.W)
  val divisor = UInt(32.W)
}
class SRTWrapper extends Module{

  val input = IO(Flipped(DecoupledIO(new SRTIn)))
  val output = IO(ValidIO(new SRTOutput(32, 32)))

  val LZC0 = Module(new LZC32)
  val LZC1 = Module(new LZC32)
  LZC0.io.a := input.bits.dividend
  LZC1.io.a := input.bits.divisor
  val div: SRT = Module(new SRT(32, 32, 32))

  // pre-process

  // 6-bits , above zero
  // add one bit for calculate complement
  val zeroHeadDividend = Wire(UInt(6.W))
  val zeroHeadDivisor = Wire(UInt(6.W))
  zeroHeadDividend := ~LZC0.io.z
  zeroHeadDivisor := ~LZC1.io.z
  // sub = zeroHeadDivider - zeroHeadDividend
  val sub = Wire(UInt(6.W))
  sub := addition.prefixadder.koggeStone(-zeroHeadDividend, zeroHeadDivisor, false.B)
  // needComputerWidth: Int = zeroHeadDivider - zeroHeadDividend + 2
  val needComputerWidth = Wire(UInt(7.W))
  needComputerWidth := addition.prefixadder.koggeStone(sub, 2.U, false.B)
  // noguard: Boolean = needComputerWidth % radixLog2 == 0
  val noguard = !needComputerWidth(0)
  // counter: Int = (needComputerWidth + 1) / 2
  val counter = (addition.prefixadder.koggeStone(needComputerWidth, 1.U, false.B) >> 1).asUInt
  // leftShiftWidthDividend: Int = zeroHeadDividend - (if (noguard) 0 else 1)
  val leftShiftWidthDividend = Wire(UInt(6.W))
  val leftShiftWidthDivisor = Wire(UInt(6.W))
  leftShiftWidthDividend := Mux(noguard,zeroHeadDividend(4,0),
    addition.prefixadder.koggeStone(zeroHeadDividend(4,0), "b111111".asUInt, false.B))
  leftShiftWidthDivisor := zeroHeadDivisor(4,0)

  // do SRT
  div.input.bits.divider := input.bits.divisor << leftShiftWidthDivisor
  div.input.bits.dividend := input.bits.dividend << leftShiftWidthDividend
  div.input.bits.counter := counter
  div.input.valid := input.valid && !(input.bits.divisor === 0.U)
  input.ready := div.input.ready

  // logic
  val divideZero = Wire(Bool())
  divideZero := (input.bits.divisor === 0.U) && input.fire

  // post-process
  output.valid := div.output.valid | divideZero
  output.bits.quotient := Mux(divideZero,"hffffffff".U(32.W),div.output.bits.quotient)
  output.bits.reminder := div.output.bits.reminder

}

class LZC8 extends Module{
  val io = IO(new Bundle{
    val a = Input(UInt(8.W))
    val z = Output(UInt(3.W))
    val v = Output(UInt(1.W))
  })
  val a = io.a
  val z0 : UInt = (!(a(7) | (!a(6)) & a(5))) & ((a(6) | a(4)) | !(a(3) | (!a(2) & a(1))))
  val z1 : UInt = !(a(7) | a(6)) & ((a(5) | a(4)) | !(a(3) | a(2)))
  val z2 : UInt = !(a(7) | a(6)) & !(a(5) | a(4))
  val _v : UInt = !(!(a(7) | a(6)) & !(a(5) | a(4))) | !(!(a(3) | a(2)) & !(a(1) | a(0)))

  io.z := Cat(~z2,~z1,~z0)
  io.v := _v
}

class LZC16 extends Module{
  val io = IO(new Bundle{
    val a = Input(UInt(16.W))
    val z = Output(UInt(4.W))
    val v = Output(UInt(1.W))
  })
  val L0 = Module(new LZC8)
  val L1 = Module(new LZC8)
  L1.io.a := io.a(15,8)
  L0.io.a := io.a(7,0)

  val flag = L1.io.v.asBool
  val z3 = Mux(flag, 1.U, 0.U)
  val z2 = Mux(flag, L1.io.z(2), L0.io.z(2))
  val z1 = Mux(flag, L1.io.z(1), L0.io.z(1))
  val z0 = Mux(flag, L1.io.z(0), L0.io.z(0))

  io.z := Cat(z3,z2,z1,z0)
  io.v := L1.io.v | L0.io.v
}
class LZC32 extends Module{
  val io = IO(new Bundle{
    val a = Input(UInt(32.W))
    val z = Output(UInt(5.W))
    val v = Output(UInt(1.W))
  })
  val L0 = Module(new LZC16)
  val L1 = Module(new LZC16)
  L1.io.a := io.a(31,16)
  L0.io.a := io.a(15,0)

  val flag = L1.io.v.asBool
  val z4 = Mux(flag, 1.U, 0.U)
  val z3 = Mux(flag, L1.io.z(3), L0.io.z(3))
  val z2 = Mux(flag, L1.io.z(2), L0.io.z(2))
  val z1 = Mux(flag, L1.io.z(1), L0.io.z(1))
  val z0 = Mux(flag, L1.io.z(0), L0.io.z(0))

  io.z := Cat(z4,z3,z2,z1,z0)
  io.v := L1.io.v | L0.io.v
}
