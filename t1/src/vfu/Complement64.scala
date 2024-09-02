// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.vfu

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Instantiate}
import chisel3.util._

/** Banked complement64
  *
  * Bank width: 16, 32, 64
  */
@instantiable
class Complement64 extends Module {
  @public
  val a: UInt = IO(Input(UInt(64.W)))
  @public
  val z: UInt = IO(Output(UInt(64.W)))
  @public
  val sew          = IO(Input(UInt(3.W)))
  @public
  val doComplement = IO(Input(UInt(4.W)))

  def addOne16Bits(in: Tuple2[UInt, Bool]): (Bool, UInt) = {
    val sum = Mux(in._2, in._1 +& 1.U, in._1)

    (sum(16).asBool, sum(15, 0))
  }

  def doReverse(in: Tuple2[UInt, Bool]): UInt = {
    Mux(in._2, ~in._1, in._1).asUInt
  }

  val aSeq  = Seq(a(15, 0), a(31, 16), a(47, 32), a(63, 48))
  val doSeq = doComplement.asBools

  /** a Reverse */
  val aRev       = aSeq.zip(doSeq).map(doReverse)
  val aRevAddOne = aRev.zip(doSeq).map(addOne16Bits)
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

object Complement64 {
  def apply(a: UInt, sign: UInt, sew: UInt) = {
    val Complement = Instantiate(new Complement64)
    // This need synthesis tool to do constant propagation
    Complement.a            := a
    Complement.doComplement := sign
    Complement.sew          := sew
    Complement.z
  }
}
