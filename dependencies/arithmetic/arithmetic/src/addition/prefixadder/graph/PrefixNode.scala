package addition.prefixadder.graph

import scala.math.Ordered.orderingToOrdered

case class PrefixNode(level: Int, fathers: Set[PrefixNode], prefixData: Set[Int], index: Int)
    extends Ordered[PrefixNode] {
  val bit:               Int = prefixData.max
  override def toString: String = s"Node$level-$bit-$index"
  override def compare(that: PrefixNode): Int = (this.bit, this.level).compare(that.bit, that.level)
}

object PrefixNode {
  var index = 0

  def apply(father: PrefixNode*): PrefixNode = {
    require(father.map(_.prefixData).reduce(_ intersect _).isEmpty, "father's prefixData has same prefixes")
    require(
      father.flatMap(_.prefixData).toList.sorted.zipWithIndex.map { case (idx, ele) => ele - idx }.toSet.size == 1,
      "prefixData is not continues"
    )
    new PrefixNode(father.map(_.level).max + 1, father.sorted.toSet, father.flatMap(_.prefixData).toSet, indexInc)
  }

  def indexInc = {
    index = index + 1
    index - 1
  }

  def apply(bit: Int): PrefixNode = {
    new PrefixNode(0, Set(), Set(bit), indexInc)
  }
}
