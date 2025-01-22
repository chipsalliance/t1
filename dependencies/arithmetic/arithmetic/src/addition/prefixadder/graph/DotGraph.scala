package addition.prefixadder.graph

import upickle.default.{macroRW, ReadWriter => RW}

case class DotNode(@upickle.implicits.key("_gvid") index: Int, name: String)

object DotNode {
  implicit val rw: RW[DotNode] = macroRW
}

case class DotEdge(tail: Int, head: Int)

object DotEdge {
  implicit val rw: RW[DotEdge] = macroRW
}

case class DotGraph(@upickle.implicits.key("objects") nodes: Seq[DotNode], edges: Seq[DotEdge])

object DotGraph {
  implicit val rw: RW[DotGraph] = macroRW
}
