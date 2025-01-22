package crypto.modmul

import chisel3._
import chisel3.util.Decoupled

abstract class ModMul extends Module {
  val p: BigInt
  val width: Int = p.bitLength
  class InputBundle extends Bundle {
    val a = UInt(width.W)
    val b = UInt(width.W)
  }
  val input = IO(Flipped(Decoupled(new InputBundle)))
  when(input.fire) {
    assert(input.bits.a < p.U, "a should exist in the field.")
    assert(input.bits.b < p.U, "b should exist in the field.")
  }
  val z = IO(Decoupled(UInt(width.W)))
  when(z.fire) {
    assert(z.bits < p.U, "z should exist in the field.")
  }
}
