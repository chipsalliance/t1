package addition.prefixadder.common

import addition.prefixadder.PrefixAdder
import chisel3.Bool

/** [[BrentKungSum]]: 2N area, 2logN depth
  *  example with width = 8
  *  0 1 2 3 4 5 6 7
  *   \|  \|  \|  \|
  *    1   3   5   7 -> expand(generate layer 1) offset: 1
  *     \       \
  *      \       \
  *       \|      \|
  *        3       7 -> expand(generate layer 2) offset: 2
  *         \
  *          \
  *           \
  *            \
  *             \
  *              \
  *               \|
  *                7 -> expand(generate layer 3) offset: 4
  *                  -> expand(generate layer 4) offset: 8
  *        3   5     -> contract(merge layer 4 to 3) offset: 4
  *         \
  *          \
  *           \|
  *            5     -> contract (merge layer 3 to 2) offset: 2
  *    1 2 3 4 5 6 7 -> contract (merge layer 2 to 1) offset: 1
  *     \|  \|  \|
  *      2   4   6
  */
object BrentKungSum extends CommonPrefixSum {
  def apply(summands: Seq[(Bool, Bool)]): Vector[(Bool, Bool)] = {
    def contract(offset: Int, x: Vector[(Bool, Bool)]): Vector[(Bool, Bool)] = {
      val double = offset << 1
      val offset1 = offset - 1
      logger.trace(s"""
                      |call contract(offset: $offset, x.size: ${x.size}):
                      |  double: $double
                      |  offset1: $offset1""".stripMargin)
      if (offset <= 0) {
        logger.trace(s"offset: $offset <= 0, will directly return")
        x
      } else if (double + offset1 >= x.size) {
        logger.trace(s"double: $double + offset1: $offset1 = ${double + offset1} >= x.size, will contract:")
        contract(offset >> 1, x)
      } else {
        logger.trace(s"offset: $offset > 0 and double + offset1 ${double + offset1} < x.size: ${x.size}:")
        val layer = Vector.tabulate(x.size) { i =>
          if (i % double == offset1 && i >= offset) {
            logger.trace(s"i: $i % double: $double && i: $i >= offset: $offset, will associate ${i - offset} and $i")
            associativeOp(Seq(x(i - offset), x(i)))
          } else {
            logger.trace(s"i = $i, will not associate")
            x(i)
          }
        }
        contract(offset >> 1, layer)
      }
    }
    def expand(offset: Int, x: Vector[(Bool, Bool)]): Vector[(Bool, Bool)] = {
      val double = offset << 1
      val double1 = double - 1
      logger.trace(
        s"""
           |call expand(offset: $offset, x.size: ${x.size}):
           |  double: $double
           |  double1: $double1""".stripMargin
      )
      if (double1 >= x.size) {
        logger.trace(s"double1: $double1 >= x.size: ${x.size}, will contract:")
        contract(offset >> 1, x)
      } else {
        logger.trace(s"double1: $double1 < x.size: ${x.size}:")
        val layer = Vector.tabulate(x.size) { i =>
          if (i % double == double1) {
            logger.trace(s"i: $i % double: $double == $double1: double1, will associate ${i - offset} and $i")
            associativeOp(Seq(x(i - offset), x(i)))
          } else {
            logger.trace(s"i: $i % double: $double != $double1, will not associate")
            x(i)
          }
        }
        expand(double, layer)
      }
    }
    expand(1, summands.toVector)
  }
}

class BrentKungAdder(width: Int) extends PrefixAdder(width, BrentKungSum)
