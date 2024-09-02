// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.vfu

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._

@instantiable
class Multiplier16 extends Module {
  @public
  val a        = IO(Input(UInt(16.W)))
  @public
  val b        = IO(Input(UInt(16.W)))
  @public
  val sew      = IO(Input(UInt(2.W)))
  @public
  val outCarry = IO(Output(UInt(32.W)))
  @public
  val outSum   = IO(Output(UInt(32.W)))

  val a0Vec = a(7, 0).asBools
  val a1Vec = a(15, 8).asBools

  def make8BitsPartialProduct(a: Tuple2[Bool, Int], in: UInt): UInt = { // Seq[(weight, value)]
    val exist   = Mux(a._1, in, 0.U(8.W))
    val doShift = a._2 match {
      case 0 => exist
      case c => Cat(exist, 0.U(c.W))
    }
    doShift
  }

  val a0x0: Seq[UInt] = a0Vec.zipWithIndex.map { case (a, i) =>
    make8BitsPartialProduct((a, i), b(7, 0))
  }

  /** shift 8 */
  val a1x0: Seq[UInt] = a1Vec.zipWithIndex.map { case (a, i) =>
    make8BitsPartialProduct((a, i), b(7, 0))
  }

  /** shift 8 */
  val a0x1: Seq[UInt] = a0Vec.zipWithIndex.map { case (a, i) =>
    make8BitsPartialProduct((a, i), b(15, 8))
  }

  /** shift 16 */
  val a1x1: Seq[UInt] = a1Vec.zipWithIndex.map { case (a, i) =>
    make8BitsPartialProduct((a, i), b(15, 8))
  }

  /** output effect width = 16 */
  def compress82(in: Seq[UInt]): (UInt, UInt) = {
    val layer0   = CSA42(12)(VecInit(in.dropRight(4)))
    val layer1   = CSA42(12)(VecInit(in(4) >> 4, in(5) >> 4, in(6) >> 4, in(7) >> 4))
    val layerOut = CSA42(16)(VecInit(layer0._1 << 1, layer0._2, layer1._1 << 5, layer1._2 << 4))
    ((layerOut._1(14, 0) << 1).asUInt, layerOut._2(15, 0))
  }

  val ax00 = compress82(a0x0)

  /** 16bits << 8 */
  val ax10 = compress82(a1x0)
  val ax01 = compress82(a0x1)

  /** 16bits << 16 */
  val ax11 = compress82(a1x1)

  val axSeq:                          Seq[(UInt, UInt)] = Seq(ax00, ax01, ax10, ax11)
  def merge16(in: Seq[(UInt, UInt)]): (UInt, UInt)      = {
    val layer00 = CSA42(24)(VecInit(in(0)._1, in(0)._2, in(1)._1 << 8, in(1)._2 << 8))
    val layer01 = CSA42(24)(VecInit(in(2)._1, in(2)._2, in(3)._1 << 8, in(3)._2 << 8))
    val layer1  = CSA42(32)(VecInit(layer00._1(22, 0) << 1, layer00._2, layer01._1(22, 0) << 9, layer01._2 << 8))
    ((layer1._1(30, 0) << 1).asUInt, layer1._2(31, 0))
  }

  val result16 = merge16(axSeq)

  outCarry := Mux(sew(0), ax11._1(15, 0) ## ax00._1(15, 0), result16._1)
  outSum   := Mux(sew(0), ax11._2(15, 0) ## ax00._2(15, 0), result16._2)
}

object Multiplier16 {
  def apply(a: UInt, b: UInt, sew: UInt) = {
    val mul16 = Module(new Multiplier16)
    // This need synthesis tool to do constant propagation
    mul16.a   := a
    mul16.b   := b
    mul16.sew := sew
    Seq(mul16.outCarry, mul16.outSum)
  }
}
