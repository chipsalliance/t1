package addition.prefixadder.graph

import addition.prefixadder.PrefixSum
import chisel3._

trait HasPrefixSumWithGraphImp extends PrefixSum {
  val prefixGraph: PrefixGraph
  override def apply(summands: Seq[(Bool, Bool)]): Vector[(Bool, Bool)] = {
    require(summands.size == prefixGraph.width, "Module width is different to Graph width")
    def helper(level: Int, x: Map[PrefixNode, (Bool, Bool)]): Map[PrefixNode, (Bool, Bool)] = if (
      level > prefixGraph.depth
    ) x
    else
      helper(
        level + 1,
        x ++ prefixGraph.level(level).map(node => node -> associativeOp(node.fathers.map(node => x(node)).toSeq)).toMap
      )
    helper(1, prefixGraph.level(0).zip(summands).toMap)
      .filter(dict => prefixGraph.lastLevelNode.contains(dict._1))
      .toSeq
      .sortBy(_._1.bit)
      .map(_._2)
      .toVector
  }
}
