// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.vfu

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._

@instantiable
class VectorMultiplier32Unsigned extends Module {
  @public
  val a   = IO(Input(UInt(32.W)))
  @public
  val b   = IO(Input(UInt(32.W)))
  @public
  val z   = IO(Output(UInt(64.W)))
  @public
  val sew = IO(Input(UInt(3.W)))
  @public
  val multiplierSum:   UInt = IO(Output(UInt(64.W)))
  @public
  val multiplierCarry: UInt = IO(Output(UInt(64.W)))

  val a0Vec = a(15, 0).asBools
  val a1Vec = a(31, 16).asBools

  val sewFor16 = Mux(sew(0), 1.U(2.W), 2.U(2.W))

  def make16BitsPartialProduct(a: Tuple2[Bool, Int], in: UInt): UInt = { // Seq[(weight, value)]
    val exist   = Mux(a._1, in, 0.U(16.W))
    val doShift = a._2 match {
      case 0 => exist
      case c => Cat(exist, 0.U(c.W))
    }
    doShift
  }

  val a1x0: Seq[UInt] = a1Vec.zipWithIndex.map { case (a, i) =>
    make16BitsPartialProduct((a, i), b(15, 0))
  }

  /** shift 16 */
  val a0x1: Seq[UInt] = a0Vec.zipWithIndex.map { case (a, i) =>
    make16BitsPartialProduct((a, i), b(31, 16))
  }

  /** output effect width = 32 */
  def compress16_2(in: Seq[UInt]): Seq[UInt] = {
    val layer00 = CSA42(20)(VecInit(in.dropRight(12)))

    /** 4 */
    val layer01 = CSA42(20)(VecInit(in(4) >> 4, in(5) >> 4, in(6) >> 4, in(7) >> 4))

    /** 8 */
    val layer02 = CSA42(20)(VecInit(in(8) >> 8, in(9) >> 8, in(10) >> 8, in(11) >> 8))

    /** 12 */
    val layer03 = CSA42(20)(VecInit(in(12) >> 12, in(13) >> 12, in(14) >> 12, in(15) >> 12))

    /** 0 */
    val layer10 = CSA42(24)(VecInit(layer00._1 << 1, layer00._2, layer01._1 << 5, layer01._2 << 4))
    val layer11 = CSA42(24)(VecInit(layer02._1 << 1, layer02._2, layer03._1 << 5, layer03._2 << 4))
    val layer2  = CSA42(32)(VecInit(layer10._1 << 1, layer10._2, layer11._1 << 9, layer11._2 << 8))
    Seq((layer2._1(30, 0) << 1).asUInt, layer2._2(31, 0))
  }

  /** shift 16 */
  val ax01 = compress16_2(a0x1)
  val ax10 = compress16_2(a1x0)

  val ax00: Seq[UInt] = Multiplier16(a(15, 0), b(15, 0), sewFor16)

  /** shift 32 */
  val ax11: Seq[UInt] = Multiplier16(a(31, 16), b(31, 16), sewFor16)

  def compress8_2(in: Seq[UInt]): (UInt, UInt) = {
    val layer00 = CSA42(48)(VecInit(in(0), in(1), in(2) << 16, in(3) << 16))
    val layer01 = CSA42(48)(VecInit(in(4), in(5), in(6) << 16, in(7) << 16))
    val layer1  = CSA42(64)(VecInit(layer00._1 << 1, layer00._2, layer01._1 << 17, layer01._2 << 16))
    ((layer1._1(62, 0) << 1).asUInt, layer1._2(63, 0))
  }

  val mergeSplit32: (UInt, UInt) = compress8_2(ax00 ++ ax01 ++ ax10 ++ ax11)

  val sumForAdder   = Mux(sew(2), mergeSplit32._1, ax11(0) ## ax00(0))
  val carryForAdder = Mux(sew(2), mergeSplit32._2, ax11(1) ## ax00(1))

  val result = VectorAdder64(sumForAdder, carryForAdder, sew ## 0.U(1.W))

  multiplierSum   := sumForAdder
  multiplierCarry := carryForAdder
  z               := result
}
