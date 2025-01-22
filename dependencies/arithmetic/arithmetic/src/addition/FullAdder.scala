package addition

import chisel3._
import chisel3.util.Cat

trait FullAdder extends Module {
  val width: Int
  require(width > 0)
  val a: UInt = IO(Input(UInt(width.W)))
  val b: UInt = IO(Input(UInt(width.W)))
  val z: UInt = IO(Output(UInt(width.W)))

  val cin:  Bool = IO(Input(Bool()))
  val cout: Bool = IO(Output(Bool()))

  assert(a +& b + cin === Cat(cout, z))
}
