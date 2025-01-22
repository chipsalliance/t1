package elaborator

import chisel3._
import chisel3.util._
import float._

class DUT(expWidth: Int, sigWidth: Int) extends Module {
  val input = IO(Flipped(Decoupled(new DutPoke(expWidth, sigWidth))))
  val output = IO(Valid(new DutPeek(expWidth, sigWidth)))

  val ds = Module(new DivSqrtMerge(expWidth: Int, sigWidth: Int))
  ds.input.valid := input.valid
  ds.input.bits.sqrt := input.bits.op
  ds.input.bits.dividend := input.bits.a
  ds.input.bits.divisor := input.bits.b
  ds.input.bits.roundingMode := input.bits.roundingMode

  input.ready := ds.input.ready

  output.bits.result := ds.output.bits.result
  output.bits.fflags := ds.output.bits.exceptionFlags
  output.valid := ds.output.valid
}

class DutPoke(expWidth: Int, sigWidth: Int) extends Bundle {
  val a = UInt((expWidth + sigWidth).W)
  val b = UInt((expWidth + sigWidth).W)
  val op = Bool()
  val roundingMode = UInt(3.W)
}

class DutPeek(expWidth: Int, sigWidth: Int) extends Bundle {
  val result = UInt((expWidth + sigWidth).W)
  val fflags = UInt(5.W)
}




