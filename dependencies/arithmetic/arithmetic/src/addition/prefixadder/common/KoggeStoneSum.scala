package addition.prefixadder.common

import addition.prefixadder.PrefixAdder
import chisel3.Bool

/** [[KoggeStoneSum]]: O(NlogN) area, logN depth
  *  0 1 2 3 4
  *   \|\|\|\|
  *    x x x x
  *  0 1 2 3 4
  *   \ \ \ \
  *     \|\|\|
  *      x x x
  *  0 1 2 3 4
  *   \
  *     \
  *       \
  *         \|
  *          4
  */
object KoggeStoneSum extends CommonPrefixSum {
  def apply(summands: Seq[(Bool, Bool)]): Vector[(Bool, Bool)] = {
    def helper(offset: Int, x: Vector[(Bool, Bool)]): Vector[(Bool, Bool)] = {
      logger.trace(s"call helper(offset: $offset, x.size: ${x.size}):")
      if (offset >= x.size) {
        logger.trace(s"offset: $offset >= x.size: ${x.size}, will return:")
        x
      } else {
        logger.trace(s"offset: $offset < x.size: ${x.size}:")
        val layer = Vector.tabulate(x.size) { i =>
          if (i < offset) {
            logger.trace(s"i: $i < offset: $offset, will not associate")
            x(i)
          } else {
            logger.trace(s"i: $i >= offset: $offset, will associate ${i - offset} and $i")
            associativeOp(Seq(x(i - offset), x(i)))
          }
        }
        helper(offset << 1, layer)
      }
    }
    helper(1, summands.toVector)
  }
}

class KoggeStoneAdder(width: Int) extends PrefixAdder(width, KoggeStoneSum)
