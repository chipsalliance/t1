// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.vfu

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Instantiate}
import chisel3.util._

/** Banked Abs32
  *
  * Bank width: 8, 16, 32
  */
@instantiable
class Abs32 extends Module {
  @public
  val a: UInt = IO(Input(UInt(32.W)))
  @public
  val z: UInt = IO(Output(UInt(32.W)))
  @public
  val sew = IO(Input(UInt(3.W)))

  def addOne8Bits(in: Tuple2[UInt, Bool]): (Bool, UInt) = {
    val sum = Mux(in._2, in._1 +& 1.U, in._1)

    (sum(8).asBool, sum(7, 0))
  }

  def doReverse(in: Tuple2[UInt, Bool]): UInt = {
    Mux(in._2, ~in._1, in._1).asUInt
  }

  val doComplement = Mux1H(
    Seq(
      sew(0) -> a(31) ## a(23) ## a(15) ## a(7),
      sew(1) -> a(31) ## a(31) ## a(15) ## a(15),
      sew(2) -> a(31) ## a(31) ## a(31) ## a(31)
    )
  )

  val aSeq  = Seq(a(7, 0), a(15, 8), a(23, 16), a(31, 24))
  val doSeq = doComplement.asBools

  /** a Reverse */
  val aRev       = aSeq.zip(doSeq).map(doReverse)
  val aRevAddOne = aRev.zip(doSeq).map(addOne8Bits)
  val cout       = aRevAddOne.map(_._1)

  val result8  = VecInit(aRevAddOne.map(_._2)).asUInt
  val result16 = VecInit(
    aRevAddOne(0)._2,
    Mux(cout(0), aRevAddOne(1)._2, aRev(1)),
    aRevAddOne(2)._2,
    Mux(cout(2), aRevAddOne(3)._2, aRev(3))
  ).asUInt

  val result32 = VecInit(
    aRevAddOne(0)._2,
    Mux(cout(0), aRevAddOne(1)._2, aRev(1)),
    Mux(cout(0) && cout(1), aRevAddOne(2)._2, aRev(2)),
    Mux(cout(0) && cout(1) && cout(2), aRevAddOne(3)._2, aRev(3))
  ).asUInt

  z := Mux1H(
    Seq(
      sew(0) -> result8,
      sew(1) -> result16,
      sew(2) -> result32
    )
  )
}

object Abs32 {
  def apply(a: UInt, sew: UInt) = {
    val Abs = Instantiate(new Abs32)
    Abs.a   := a
    Abs.sew := sew
    Abs.z
  }
}
