package VFU

import addition.prefixadder.common.BrentKungSum
import chisel3._
import chisel3.util._

class Multiplier16 extends Module{
  val a = IO(Input(UInt(16.W)))
  val b = IO(Input(UInt(16.W)))
  val z = IO(Output(UInt(32.W)))
  val sew = IO(Input(UInt(2.W)))

  val a0Vec = a(7, 0).asBools
  val a1Vec = a(15, 8).asBools

  def make8BitsPartialProduct(a : Tuple2[Bool, Int], in:UInt): UInt = { // Seq[(weight, value)]
    val exist = Mux(a._1, in, 0.U(8.W))
    val doShift = a._2 match {
      case 0 => exist
      case c => Cat(exist, 0.U(c.W))
    }
    doShift
  }

  val a0x0: Seq[UInt] = a0Vec.zipWithIndex.map {
    case (a,i) => make8BitsPartialProduct((a,i),b(7,0))
  }
  /** shift 8 */
  val a1x0: Seq[UInt] = a1Vec.zipWithIndex.map {
    case (a, i) => make8BitsPartialProduct((a, i), b(7, 0))
  }
  /** shift 8 */
  val a0x1: Seq[UInt] = a0Vec.zipWithIndex.map {
    case (a, i) => make8BitsPartialProduct((a, i), b(15, 8))
  }
  /** shift 16 */
  val a1x1: Seq[UInt] = a1Vec.zipWithIndex.map {
    case (a, i) => make8BitsPartialProduct((a, i), b(15, 8))
  }

  /** output effect width = 16 */
  def compress82(in: Seq[UInt]): (UInt, UInt) = {
    val layer0 = csa42(12)(VecInit(in.dropRight(4)))
    val layer1 = csa42(12)(VecInit(in(4)(11, 4), in(5)(12, 4), in(6)(13, 4), in(7)(14, 4)))
    val layerOut = csa42(16)(VecInit(layer0._1 << 1, layer0._2, layer1._1 << 5, layer1._2 << 4))
    ((layerOut._1(14, 0) << 1).asUInt, layerOut._2(15, 0))
  }

  def add82(in: Seq[UInt]): UInt = {
    val compress = compress82(in)
    compress._1.asUInt + compress._2.asUInt
  }

  val ax00 = compress82(a0x0)
  /** 16bits << 8 */
  val ax10 = compress82(a1x0)
  val ax01 = compress82(a0x1)
  /** 16bits << 16 */
  val ax11 = compress82(a1x1)

  val ax00result = addition.prefixadder.apply(BrentKungSum)(ax00._1(15,0) , ax00._2(15,0), false.B)
  val ax11result = addition.prefixadder.apply(BrentKungSum)(ax11._1(15,0) , ax11._2(15,0), false.B)

  val merge = csa42(16)(
    VecInit(
      ax10._1,
      ax10._2,
      ax01._1,
      ax01._2))
  val result16 = csa42(32)(
    VecInit(
      ax00result,
      merge._1<<9,
      merge._2<<8,
      ax11result<<16))
  val output16 = addition.prefixadder.apply(BrentKungSum)((result16._1<<1)(31,0).asUInt, result16._2(31,0))

  val output8 = ax11result(15,0) ## ax00result(15,0)

  z := Mux(sew(0), output8, output16)
}

object Multiplier16 {
  def apply(a: UInt,
             b: UInt, sew:UInt) = {
    val mul16 = Module(new Multiplier16)
    // This need synthesis tool to do constant propagation
    mul16.a := a
    mul16.b := b
    mul16.sew := sew
    mul16.z
  }
}