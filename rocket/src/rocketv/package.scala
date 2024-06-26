package org.chipsalliance

import chisel3._
import amba.axi4.bundle._
import chisel3.util.{Cat, MuxLookup}

package object rocketv {

  def axiHelper(x: AXI4ChiselBundle, fire: Bool): (Bool, Bool, Bool, UInt) = {
    // same as len
    val count = RegInit(0.U(8.W))
    val first = count === 0.U
    val last: Bool = x match {
      case r: R => r.last
      case w: W => w.last
      case _ => true.B
    }
    val done = last && fire
    when(fire) {
      count := Mux(last, 0.U, count + 1.U)
    }
    (first, last, done, count)
  }


  def nextState(cmd: UInt) = {
    val wr: UInt = Cat(true.B, true.B)   // Op actually writes
    val wi: UInt = Cat(false.B, true.B)  // Future op will write
    val rd: UInt = Cat(false.B, false.B) // Op only reads

    // cache state
    val width = 2
    val Nothing = 0.U(width.W)
    val Branch  = 1.U(width.W)
    val Trunk   = 2.U(width.W)
    val Dirty   = 3.U(width.W)
    MuxLookup(cmd, Nothing)(Seq(
      rd   -> Trunk,
      wi   -> Trunk,
      wr   -> Dirty))
  }
}
