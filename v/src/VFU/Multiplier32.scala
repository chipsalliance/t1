package VFU

import addition.prefixadder.common.BrentKungSum
import chisel3._
import chisel3.util._

class Multiplier32 extends Module{
  val a = IO(Input(UInt(32.W)))
  val b = IO(Input(UInt(32.W)))
  val z = IO(Output(UInt(64.W)))
  val sew = IO(Input(UInt(3.W)))

  val a0Vec = a(15, 0).asBools
  val a1Vec = a(31, 16).asBools

  val sewFor16 = Mux(sew(0), 1.U(2.W), 2.U(2.W))
  val result00 = Multiplier16(a(15,0), b(15,0), sewFor16)
  /** shift 32 */
  val result11 = Multiplier16(a(31,16),b(31,16), sewFor16)

  def make16BitsPartialProduct(a: Tuple2[Bool, Int], in: UInt): UInt = { // Seq[(weight, value)]
    val exist = Mux(a._1, in, 0.U(16.W))
    val doShift = a._2 match {
      case 0 => exist
      case c => Cat(exist, 0.U(c.W))
    }
    doShift
  }

  /** shift 16 */
  val a1x0: Seq[UInt] = a1Vec.zipWithIndex.map {
    case (a, i) => make16BitsPartialProduct((a, i), b(15, 0))
  }
  /** shift 16 */
  val a0x1: Seq[UInt] = a0Vec.zipWithIndex.map {
    case (a, i) => make16BitsPartialProduct((a, i), b(31, 16))
  }

  /** output effect width = 32 */
  def compress16_2(in: Seq[UInt]): (UInt, UInt) = {
    val layer00 = csa42(20)(VecInit(in.dropRight(12)))
    /** 4 */
    val layer01 = csa42(20)(VecInit(in(4)>>4,  in(5)>>4,  in(6)>>4,  in(7)>>4))
    /** 8 */
    val layer02 = csa42(20)(VecInit(in(8)>>8,  in(9)>>8,  in(10)>>8, in(11)>>8))
    /** 12 */
    val layer03 = csa42(20)(VecInit(in(12)>>12, in(13)>>12, in(14)>>12, in(15)>>12))
    /** 0 */
    val layer10 = csa42(24)(VecInit(layer00._1 << 1, layer00._2, layer01._1 << 5, layer01._2 << 4))
    val layer11 = csa42(24)(VecInit(layer02._1 << 1, layer02._2, layer03._1 << 5, layer03._2 << 4))
    val layer2  = csa42(32)(VecInit(layer10._1 << 1, layer10._2, layer11._1 << 9, layer11._2 << 8))
    ((layer2._1(31, 0) << 1).asUInt, layer2._2(31, 0))
  }

  val ax01 = compress16_2(a0x1)
  val ax10 = compress16_2(a1x0)

  val merge = csa42(32)(
    VecInit(
      ax10._1,
      ax10._2,
      ax01._1,
      ax01._2))

  val result32 = csa42(64)(
    VecInit(
      result00,
      (merge._1 << 17).asUInt,
      (merge._2 << 16).asUInt,
      result11 << 32))
  val output32 = addition.prefixadder.apply(BrentKungSum)((result32._1 << 1).asUInt(63,0), result32._2(63,0))
  val output8And16 = result11 ## result00

  z := Mux(sew(2), output32, output8And16)
}