package addition.prefixadder.common

import addition.prefixadder.PrefixAdder
import chisel3.Bool

/** [[RippleCarrySum]]: N-1 area, N-1 depth
  * not need to explain
  */
object RippleCarrySum extends CommonPrefixSum {
  def apply(summands: Seq[(Bool, Bool)]): Vector[(Bool, Bool)] = {

    /**
      * generate prefix tree layer by layer.
      * offset is the mark of layers'end(It should generate next layer)
      */
    def helper(offset: Int, x: Vector[(Bool, Bool)]): Vector[(Bool, Bool)] = {
      if (offset >= x.size) {

        /**
          * if `offset >= x.size`, return the apply function,
          * Since type T = (Bool, Bool), It return the refering to these P, G pairs
          * which will be used to generate carry signal.
          */
        logger.trace(s"offset: $offset >= x.size: ${x.size}, will return:")
        x
      } else {

        /**
          * if `offset < x.size` means we still didn't reach the end of adder,
          * that the last bits signal is not embbed to the pg generation pair.
          * [[layer]] means the current layer of reference.
          */
        logger.trace(s"offset: $offset < x.size: ${x.size}:")
        val layer: Vector[(Bool, Bool)] = Vector.tabulate(x.size) { i =>
          if (i != offset) {

            /**
              * if `i != offset`,
              * return last layer's reference.
              */
            logger.trace(s"i: $i != offset: $offset, will not associate")
            x(i)
          } else {

            /**
              * if `i == offset`
              * this will call [[associativeOp]] to prefix multi bits in the prefix adder into current bit,
              * and return the reference to it.
              */
            logger.trace(s"i: $i == offset: $offset, will associate ${i - 1} and $i")
            associativeOp(Seq(x(i - 1), x(i)))
          }
        }

        /**
          * RippleCarrySum, add offset by 1.
          */
        helper(offset + 1, layer)
      }
    }

    /**
      * Start from `offset = 1`,
      * and all prefix from layer 0.
      */
    helper(1, summands.toVector)
  }
}

class RippleCarryAdder(width: Int) extends PrefixAdder(width, RippleCarrySum)
