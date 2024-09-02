// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.vfu

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Instantiate}
import chisel3.util._

/** IO formula
  * {{{
  * sew = 8 => Input.sew = b001.U,
  * cin = c3,c2,c1,c0
  * Output cout = cout
  *
  *
  * sew = 16 => input.sew = b010.U
  * Input.sew = 010,
  * cin = c_high,c_high,c_low,c_low
  * example :
  * cin = 1 and 0, Input cin should be b1100.U
  * cout = 10, Output cout = b10.U
  *
  *
  *
  * sew = 32 Input.sew = 010,
  * cin = cin,cin,cin,cin
  * example: cin = 1, Input cin should be 1111.U
  * cout = 1, Output cout = b1.U
  * }}}
  */
@instantiable
class VectorAdder64 extends Module {
  val width = 64
  @public
  val a: UInt = IO(Input(UInt(width.W)))
  @public
  val b: UInt = IO(Input(UInt(width.W)))
  @public
  val z: UInt = IO(Output(UInt(width.W)))
  @public
  val sew = IO(Input(UInt(4.W)))

  val indexSeq = Seq(0, 1, 2, 3, 4, 5, 6, 7)
  val e        = indexSeq.map(i => i * 8)
  val s        = Seq(7, 15, 23, 31, 39, 47, 55, 63)

  @public
  val cin  = IO(Input(UInt(8.W)))
  @public
  val cout = IO(Output(UInt(8.W)))

  // Split up bit vectors into individual bits and reverse it
  val as: Seq[Bool] = a.asBools
  val bs: Seq[Bool] = b.asBools

  def zeroLayer(a: Seq[Bool], b: Seq[Bool]): Seq[(Bool, Bool)] = a.zip(b).map { case (a, b) => (a ^ b, a & b) }

  def prefixadd(a: (Bool, Bool), b: (Bool, Bool)) = (a._1 & b._1, a._2 | (a._1 & b._2))

  def cgen(pg: (Bool, Bool), cin: Bool) = pg._2 || (pg._1 && cin)

  def bk8(leaf: Seq[(Bool, Bool)]): Seq[(Bool, Bool)] = leaf match {
    /** match to 8 bits fan-in */
    case Seq((p0, g0), (p1, g1), (p2, g2), (p3, g3), (p4, g4), (p5, g5), (p6, g6), (p7, g7)) => {
      val layer0 = Seq(
        prefixadd((p7, g7), (p6, g6)),
        prefixadd((p5, g5), (p4, g4)),
        prefixadd((p3, g3), (p2, g2)),
        prefixadd((p1, g1), (p0, g0))
      )
      val layer1 = Seq(prefixadd(layer0(0), layer0(1)), prefixadd(layer0(2), layer0(3)))

      val t0 = (p0, g0)
      val t1 = layer0(3)
      val t2 = prefixadd((p2, g2), t1)
      val t3 = layer1(1)
      val t4 = prefixadd((p4, g4), t3)
      val t5 = prefixadd(layer0(1), layer1(1))
      val t6 = prefixadd((p6, g6), t5)
      val t7 = prefixadd(layer1(0), layer1(1))
      Seq(t0, t1, t2, t3, t4, t5, t6, t7)
    }
  }

  val pairs: Seq[(Bool, Bool)] = zeroLayer(as, bs)

  val tree8Leaf = e.map { case i =>
    bk8(pairs.slice(i, i + 8))
  }
  val tree8: Seq[(Bool, Bool)] = tree8Leaf.fold(Nil)(_ ++ _)
  val tree16Leaf0 = tree8Leaf(0) ++ tree8Leaf(1).map(prefixadd(_, tree8Leaf(0)(7)))
  val tree16Leaf1 = tree8Leaf(2) ++ tree8Leaf(3).map(prefixadd(_, tree8Leaf(2)(7)))
  val tree16Leaf2 = tree8Leaf(4) ++ tree8Leaf(5).map(prefixadd(_, tree8Leaf(4)(7)))
  val tree16Leaf3 = tree8Leaf(6) ++ tree8Leaf(7).map(prefixadd(_, tree8Leaf(6)(7)))
  val tree16: Seq[(Bool, Bool)] = tree16Leaf0 ++ tree16Leaf1 ++ tree16Leaf2 ++ tree16Leaf3
  val tree32Leaf0 = tree16Leaf0 ++ tree16Leaf1.map(prefixadd(_, tree16Leaf0(15)))
  val tree32Leaf1 = tree16Leaf2 ++ tree16Leaf3.map(prefixadd(_, tree16Leaf2(15)))
  val tree32      = tree32Leaf0 ++ tree32Leaf1
  val tree64      = tree32Leaf0 ++ tree32Leaf1.map(prefixadd(_, tree32Leaf0(31)))

  val tree8P  = VecInit(tree8.map(_._1)).asUInt
  val tree8G  = VecInit(tree8.map(_._2)).asUInt
  val tree16P = VecInit(tree16.map(_._1)).asUInt
  val tree16G = VecInit(tree16.map(_._2)).asUInt
  val tree32P = VecInit(tree32.map(_._1)).asUInt
  val tree32G = VecInit(tree32.map(_._2)).asUInt
  val tree64P = VecInit(tree64.map(_._1)).asUInt
  val tree64G = VecInit(tree64.map(_._2)).asUInt

  val treeP = Mux1H(
    Seq(
      sew(0) -> tree8P,
      sew(1) -> tree16P,
      sew(2) -> tree32P,
      sew(3) -> tree64P
    )
  )

  val treeG = Mux1H(
    Seq(
      sew(0) -> tree8G,
      sew(1) -> tree16G,
      sew(2) -> tree32G,
      sew(3) -> tree64G
    )
  )
  val tree  = treeP.asBools.zip(treeG.asBools)

  /** Each 8 bits pg tree will be combined with their own cin to get carryResult in each bit.
    *
    * It's why 16sew input cin should be xxyy instead of 0x0y.
    */
  def buildCarry(tree: Seq[(Bool, Bool)], cin: UInt): UInt = {
    val cbank = e.zip(indexSeq).map { case (e, i) =>
      VecInit(tree.slice(e, e + 8).map(pg => cgen(pg, cin(i)))).asUInt
    }
    Cat(cbank(7), cbank(6), cbank(5), cbank(4), cbank(3), cbank(2), cbank(1), cbank(0))
  }

  /** if carry generated in each bit , in order */
  val carryResult: UInt = buildCarry(tree, cin)

  /** build final cout */
  val cout8  =
    carryResult(s(7)) ##
      carryResult(s(6)) ##
      carryResult(s(5)) ##
      carryResult(s(4)) ##
      carryResult(s(3)) ##
      carryResult(s(2)) ##
      carryResult(s(1)) ##
      carryResult(s(0))
  val cout16 =
    carryResult(s(7)) ##
      carryResult(s(5)) ##
      carryResult(s(3)) ##
      carryResult(s(1))
  val cout32 =
    carryResult(s(7)) ## carryResult(s(3))
  val cout64 = carryResult(s(7))

  /** build cs for all cases
    *
    * {{{
    * cs for banked 8 :
    * bank3 ## cin3    ## bank2 ## cin2    ## bank1 ##  cin1   ## bank0 ## cin0
    *
    * cs for bank 16:
    * bank3 ## connect ## bank2 ## cin2    ## bank1 ## connect ## bank0 ## cin0
    *
    * cs for bank 32:
    * bank3 ## connect ## bank2 ## connect ## bank1 ## connect ## bank0 ## cin0
    *
    * banki is carry(i*8+6,i*8), 7bits
    * connect is carry(i*8+7)
    * }}}
    *
    * carryInSele is to append the holes in cs
    */
  val carryInSele = Mux1H(
    Seq(
      sew(0) -> cin,
      sew(1) ->
        carryResult(s(6)) ##
        cin(6) ##
        carryResult(s(4)) ##
        cin(4) ##
        carryResult(s(2)) ##
        cin(2) ##
        carryResult(s(0)) ##
        cin(0),
      sew(2) ->
        carryResult(s(6)) ##
        carryResult(s(5)) ##
        carryResult(s(4)) ##
        cin(4) ##
        carryResult(s(2)) ##
        carryResult(s(1)) ##
        carryResult(s(0)) ##
        cin(0),
      sew(3) ->
        carryResult(s(6)) ##
        carryResult(s(5)) ##
        carryResult(s(4)) ##
        carryResult(s(3)) ##
        carryResult(s(2)) ##
        carryResult(s(1)) ##
        carryResult(s(0)) ##
        cin(0)
    )
  )
  val cs          = Cat(
    carryResult(62, 56),
    carryInSele(7),
    carryResult(54, 48),
    carryInSele(6),
    carryResult(46, 40),
    carryInSele(5),
    carryResult(38, 32),
    carryInSele(4),
    carryResult(30, 24),
    carryInSele(3),
    carryResult(22, 16),
    carryInSele(2),
    carryResult(14, 8),
    carryInSele(1),
    carryResult(6, 0),
    carryInSele(0)
  )

  cout := Mux1H(
    Seq(
      sew(0) -> cout8,
      sew(1) -> cout16,
      sew(2) -> cout32,
      sew(3) -> cout64
    )
  )

  val ps = VecInit(pairs.map(_._1)).asUInt

  z := ps ^ cs
}

object VectorAdder64 {
  def apply(a: UInt, b: UInt, sew: UInt) = {
    val adder64 = Instantiate(new VectorAdder64)
    adder64.a   := a
    adder64.b   := b
    adder64.cin := 0.U
    adder64.sew := sew
    adder64.z
  }
}
