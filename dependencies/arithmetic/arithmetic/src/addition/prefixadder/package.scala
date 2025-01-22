package addition

import addition.prefixadder.common.{BrentKungSum, KoggeStoneSum, RippleCarrySum}
import chisel3._
import chisel3.util.Cat
import utils.{extend, sIntToBitPat}

package object prefixadder {

  def apply(
    prefixSum: PrefixSum,
    width:     Option[Int] = None,
    hasSign:   Boolean = false
  )(a:         UInt,
    b:         UInt,
    cin:       Bool = false.B
  ) = {
    val w = width.getOrElse(Seq(a, b).flatMap(_.widthOption).max)
    val m = Module(new PrefixAdder(w, prefixSum))
    // This need synthesis tool to do constant propagation
    m.a := extend(a, w, hasSign)
    m.b := extend(b, w, hasSign)
    m.cin := cin
    Cat(m.cout, m.z)
  }

  def brentKun(a: UInt, b: UInt, cin: Bool = false.B, width: Option[Int] = None, hasSign: Boolean = false) =
    apply(BrentKungSum, width, hasSign)(a, b, cin)

  def koggeStone(a: UInt, b: UInt, cin: Bool = false.B, width: Option[Int] = None, hasSign: Boolean = false) =
    apply(KoggeStoneSum, width, hasSign)(a, b, cin)

  def rippleCarry(a: UInt, b: UInt, cin: Bool = false.B, width: Option[Int] = None, hasSign: Boolean = false) =
    apply(RippleCarrySum, width, hasSign)(a, b, cin)
}
