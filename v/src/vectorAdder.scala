package v

import chisel3._
import chisel3.util._

/**
  * IO formula
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
  *}}}
  *
  */
class vectorAdder(val width: Int) extends Module {
  require(width > 0)
  val a: UInt = IO(Input(UInt(width.W)))
  val b: UInt = IO(Input(UInt(width.W)))
  val z: UInt = IO(Output(UInt(width.W)))
  val sew = IO(Input(UInt(3.W)))

  val cin = IO(Input(UInt(4.W)))
  val cout = IO(Output(UInt(4.W)))

  // Split up bit vectors into individual bits and reverse it
  val as: Seq[Bool] = a.asBools
  val bs: Seq[Bool] = b.asBools

  def zeroLayer(a: Seq[Bool], b: Seq[Bool]): Seq[(Bool, Bool)] = a.zip(b).map { case (a, b) => (a ^ b, a & b) }

  def prefixadd(a: (Bool, Bool), b: (Bool, Bool)) = (a._1 & b._1, a._2 | (a._1 & b._2))

  def cgen(pg: (Bool, Bool), cin: Bool) = pg._2 || (pg._1 && cin)

  def bk8(leaf: Seq[(Bool, Bool)]): Seq[(Bool, Bool)] = leaf match {
    /** match to 8 bits fan-in */
    case Seq((p0, g0), (p1, g1), (p2, g2), (p3, g3), (p4, g4), (p5, g5), (p6, g6), (p7, g7)) => {
      val layer0 = Seq(prefixadd((p7, g7), (p6, g6)), prefixadd((p5, g5), (p4, g4)), prefixadd((p3, g3), (p2, g2)), prefixadd((p1, g1), (p0, g0)))
      val layer1 = Seq(prefixadd(layer0(0), layer0(1)), prefixadd(layer0(2), layer0(3)))

      val s0 = (p0, g0)
      val s1 = layer0(3)
      val s2 = prefixadd((p2, g2), s1)
      val s3 = layer1(1)
      val s4 = prefixadd((p4, g4), s3)
      val s5 = prefixadd(layer0(1), layer1(1))
      val s6 = prefixadd((p6, g6), s5)
      val s7 = prefixadd(layer1(0), layer1(1))
      Seq(s0, s1, s2, s3, s4, s5, s6, s7)
    }
  }

  val pairs: Seq[(Bool, Bool)] = zeroLayer(as, bs)
  val tree8Leaf0 = bk8(pairs.slice(0, 8))
  val tree8Leaf1 = bk8(pairs.slice(8, 16))
  val tree8Leaf2 = bk8(pairs.slice(16, 24))
  val tree8Leaf3 = bk8(pairs.slice(24, 32))
  val tree8: Seq[(Bool, Bool)] = tree8Leaf0 ++ tree8Leaf1 ++ tree8Leaf2 ++ tree8Leaf3
  val tree16Leaf0 = tree8Leaf0 ++ tree8Leaf1.map(prefixadd(_, tree8Leaf0(7)))
  val tree16Leaf1 = tree8Leaf2 ++ tree8Leaf3.map(prefixadd(_, tree8Leaf2(7)))
  val tree16: Seq[(Bool, Bool)] = tree16Leaf0 ++ tree16Leaf1
  val tree32 = tree16Leaf0 ++ tree16Leaf1.map(prefixadd(_, tree16Leaf0(15)))

  val tree8P  = VecInit(tree8.map(_._1)).asUInt
  val tree8G  = VecInit(tree8.map(_._2)).asUInt
  val tree16P = VecInit(tree16.map(_._1)).asUInt
  val tree16G = VecInit(tree16.map(_._2)).asUInt
  val tree32P = VecInit(tree32.map(_._1)).asUInt
  val tree32G = VecInit(tree32.map(_._2)).asUInt

  val treeRestoreP = Mux1H(Seq(
    sew(0) -> tree8P,
    sew(1) -> tree16P,
    sew(2) -> tree32P
  ))

  val treeRestoreG = Mux1H(Seq(
    sew(0) -> tree8G,
    sew(1) -> tree16G,
    sew(2) -> tree32G
  ))
  val treeRestore = treeRestoreP.asBools.zip(treeRestoreG.asBools)

  def buildCarry(tree: Seq[(Bool, Bool)], pg: Seq[(Bool, Bool)], cin: UInt): UInt = {
    val ci0 = VecInit(tree.slice(0, 8).map(pg => cgen(pg, cin(0)))).asUInt
    val ci1 = VecInit(tree.slice(8, 16).map(pg => cgen(pg, cin(1)))).asUInt
    val ci2 = VecInit(tree.slice(16, 24).map(pg => cgen(pg, cin(2)))).asUInt
    val ci3 = VecInit(tree.slice(24, 32).map(pg => cgen(pg, cin(3)))).asUInt
    Cat(ci3, ci2, ci1, ci0)
  }

  /** if carry generated in each bit */
  val carry = buildCarry(treeRestore, pairs, cin)
  val cout8  = carry(31) ## carry(23) ## carry(15) ## carry(7)
  val cout16 = carry(31) ## carry(15)
  val cout32 = carry(31)
  val ps = VecInit(pairs.map(_._1)).asUInt
  val carryInSele = Mux1H(Seq(
    sew(0) -> cin,
    sew(1) -> carry(23) ## cin(2)    ## carry(7) ## cin(0),
    sew(2) -> carry(23) ## carry(15) ## carry(7) ## cin(0),
  ))
  /** the shifted carry to calculate sum */
  val cs = Cat(carry(30, 24), carryInSele(3),
    carry(22, 16), carryInSele(2),
    carry(14, 8),  carryInSele(1),
    carry(6, 0),   carryInSele(0))

  cout := Mux1H(Seq(
    sew(0) -> cout8,
    sew(1) -> cout16,
    sew(2) -> cout32,
  ))

  z := ps ^ cs
}