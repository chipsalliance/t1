package addition.prefixadder.common

import addition.prefixadder.PrefixAdder
import chisel3.Bool

/** the 3 fan in version of [[RippleCarrySum]]
  * quite stupid architecture
  */
object RippleCarry3Sum extends CommonPrefixSum {
  def apply(summands: Seq[(Bool, Bool)]): Vector[(Bool, Bool)] = {
    def helper(offset: Int, x: Vector[(Bool, Bool)]): Vector[(Bool, Bool)] = {
      if (offset >= x.size + 1) {
        logger.trace(s"offset: $offset >= x.size: ${x.size}, will return:")
        x
      } else {
        logger.trace(s"offset: $offset < x.size: ${x.size}:")
        val layer: Vector[(Bool, Bool)] = Vector.tabulate(x.size) { i =>
          if (i == offset - 1) {
            logger.trace(s"i: $i == offset - 1: $offset, will associate ${i - 2} to $i")
            associativeOp(Seq(x(i - 1), x(i)))
          } else if (i == offset) {
            logger.trace(s"i: $i == offset: $offset, will associate ${i - 2} to $i")
            associativeOp(Seq(x(i - 2), x(i - 1), x(i)))
          } else {
            logger.trace(s"i: $i != offset: $offset, will not associate")
            x(i)
          }
        }
        helper(offset + 2, layer)
      }
    }
    helper(2, summands.toVector)
  }
}

class RippleCarry3Adder(width: Int) extends PrefixAdder(width, RippleCarry3Sum)
