package addition.prefixadder.common

import addition.prefixadder.PrefixSum
import chisel3.Bool

/** 2 inputs Prefix sum is has implementation of
  * [[RippleCarrySum]], [[KoggeStoneSum]], [[BrentKungSum]]
  * You should read those code in sequence for better understanding.
  */
trait CommonPrefixSum extends PrefixSum {
  def associativeOp(leaf: Seq[(Bool, Bool)]): (Bool, Bool) = leaf match {
    /** match to 2 bits fan-in */
    case Seq((p0, g0), (p1, g1)) => (p0 && p1, (g0 && p1) || g1)

    /** match to 3 bits fan-in */
    case Seq((p0, g0), (p1, g1), (p2, g2)) => (p0 && p1 && p2, (g0 && p1 && p2) || (g1 && p2) || g2)

    /** match to 4 bits fan-in */
    case Seq((p0, g0), (p1, g1), (p2, g2), (p3, g3)) =>
      (p0 && p1 && p2 && p3, (g0 && p1 && p2 && p3) || (g1 && p2 && p3) || (g2 && p3) || g3)

    /** match to 5 bits fan-in */
    case Seq((p0, g0), (p1, g1), (p2, g2), (p3, g3), (p4, g4)) =>
      (
        p0 && p1 && p2 && p3 && p4,
        (g0 && p1 && p2 && p3 && p4) || (g1 && p2 && p3 && p4) || (g2 && p3 && p4) || (g3 && p4) || g4
      )

    /** match to 6 bits fan-in */
    case Seq((p0, g0), (p1, g1), (p2, g2), (p3, g3), (p4, g4), (p5, g5)) =>
      (
        p0 && p1 && p2 && p3 && p4 && p5,
        (g0 && p1 && p2 && p3 && p4 && p5) || (g1 && p2 && p3 && p4 && p5) || (g2 && p3 && p4 && p5) || (g3 && p4 && p5) || (g4 && p5) || g5
      )

    /** match to 7 bits fan-in */
    case Seq((p0, g0), (p1, g1), (p2, g2), (p3, g3), (p4, g4), (p5, g5), (p6, g6)) =>
      (
        p0 && p1 && p2 && p3 && p4 && p5 && p6,
        (g0 && p1 && p2 && p3 && p4 && p5 && p6) || (g1 && p2 && p3 && p4 && p5 && p6) || (g2 && p3 && p4 && p5 && p6) || (g3 && p4 && p5 && p6) || (g4 && p5 && p6) || (g5 && p6) || g6
      )

    /** match to 8 bits fan-in */
    case Seq((p0, g0), (p1, g1), (p2, g2), (p3, g3), (p4, g4), (p5, g5), (p6, g6), (p7, g7)) =>
      (
        p0 && p1 && p2 && p3 && p4 && p5 && p6 && p7,
        (g0 && p1 && p2 && p3 && p4 && p5 && p6 && p7) || (g1 && p2 && p3 && p4 && p5 && p6 && p7) || (g2 && p3 && p4 && p5 && p6 && p7) || (g3 && p4 && p5 && p6 && p7) || (g4 && p5 && p6 && p7) || (g5 && p6 && p7) || (g6 && p7) || g7
      )

    /** match to 9 bits fan-in */
    case Seq((p0, g0), (p1, g1), (p2, g2), (p3, g3), (p4, g4), (p5, g5), (p6, g6), (p7, g7), (p8, g8)) =>
      (
        p0 && p1 && p2 && p3 && p4 && p5 && p6 && p7 && p8,
        (g0 && p1 && p2 && p3 && p4 && p5 && p6 && p7 && p8) || (g1 && p2 && p3 && p4 && p5 && p6 && p7 && p8) || (g2 && p3 && p4 && p5 && p6 && p7 && p8) || (g3 && p4 && p5 && p6 && p7 && p8) || (g4 && p5 && p6 && p7 && p8) || (g5 && p6 && p7 && p8) || (g6 && p7 && p8) || (g7 && p8) || g8
      )

    /** match to 10 bits fan-in */
    case Seq((p0, g0), (p1, g1), (p2, g2), (p3, g3), (p4, g4), (p5, g5), (p6, g6), (p7, g7), (p8, g8), (p9, g9)) =>
      (
        p0 && p1 && p2 && p3 && p4 && p5 && p6 && p7 && p8 && p9,
        (g0 && p1 && p2 && p3 && p4 && p5 && p6 && p7 && p8 && p9) || (g1 && p2 && p3 && p4 && p5 && p6 && p7 && p8 && p9) || (g2 && p3 && p4 && p5 && p6 && p7 && p8 && p9) || (g3 && p4 && p5 && p6 && p7 && p8 && p9) || (g4 && p5 && p6 && p7 && p8 && p9) || (g5 && p6 && p7 && p8 && p9) || (g6 && p7 && p8 && p9) || (g7 && p8 && p9) || (g8 && p9) || g9
      )
  }

  def zeroLayer(a: Seq[Bool], b: Seq[Bool]): Seq[(Bool, Bool)] = a.zip(b).map { case (a, b) => (a ^ b, a && b) }
}
