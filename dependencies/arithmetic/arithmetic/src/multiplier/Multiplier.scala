package multiplier

import chisel3._

trait Multiplier[T] extends Module {
  val aWidth: Int
  val bWidth: Int
  require(aWidth > 0)
  require(bWidth > 0)
  val a: T
  val b: T
  val z: T
}

trait SignedMultiplier extends Multiplier[SInt] {
  val a: SInt = IO(Input(SInt(aWidth.W)))
  val b: SInt = IO(Input(SInt(bWidth.W)))
  val z: SInt = IO(Output(SInt((aWidth + bWidth).W)))
  assert(a * b === z)
}

trait UnsignedMultiplier extends Multiplier[UInt] {
  val a: UInt = IO(Input(UInt(aWidth.W)))
  val b: UInt = IO(Input(UInt(bWidth.W)))
  val z: UInt = IO(Output(UInt((aWidth + bWidth).W)))
  assert(a * b === z)
}
