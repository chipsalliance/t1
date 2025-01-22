package addition.prefixadder

import addition.prefixadder.common.CommonPrefixSum
import addition.prefixadder.graph.{HasPrefixSumWithGraphImp, PrefixGraph, PrefixNode}
import chiseltest.formal.BoundedCheck
import formal.FormalSuite
import utest._

/** This adder is same with BrentKungSum8*/
object BrentKungSum8ByGraph extends HasPrefixSumWithGraphImp with CommonPrefixSum {
  val zeroLayer: Seq[PrefixNode] = Seq.tabulate(8)(PrefixNode(_))
  val node11: PrefixNode = PrefixNode(zeroLayer(0), zeroLayer(1))
  val node13: PrefixNode = PrefixNode(zeroLayer(2), zeroLayer(3))
  val node15: PrefixNode = PrefixNode(zeroLayer(4), zeroLayer(5))
  val node17: PrefixNode = PrefixNode(zeroLayer(6), zeroLayer(7))
  val node22: PrefixNode = PrefixNode(node11, zeroLayer(2))
  val node23: PrefixNode = PrefixNode(node11, node13)
  val node27: PrefixNode = PrefixNode(node15, node17)
  val node35: PrefixNode = PrefixNode(node23, node15)
  val node37: PrefixNode = PrefixNode(node23, node27)
  val node34: PrefixNode = PrefixNode(node23, zeroLayer(4))
  val node46: PrefixNode = PrefixNode(node35, zeroLayer(6))

  val prefixGraph = PrefixGraph(
    zeroLayer.toSet +
      node11 + node13 + node15 + node17 +
      node22 + node23 + node27 +
      node35 + node37 + node34 +
      node46
  )
}

class DemoPrefixAdderWithGraph extends PrefixAdder(BrentKungSum8ByGraph.prefixGraph.width - 1, BrentKungSum8ByGraph)

object PrefixTreeSpecTester extends FormalSuite {

  val zeroLayer = Seq.tabulate(4)(PrefixNode(_))
  val node1 = PrefixNode(zeroLayer(0), zeroLayer(1))
  val node2 = PrefixNode(zeroLayer(2), zeroLayer(3))
  val node3 = PrefixNode(node1, node2)
  val node4 = PrefixNode(zeroLayer(2), node1)

  val tests: Tests = Tests {
    test("should serialize PrefixGraph") {
      assert(os.read(os.resource / "graph.dot") == BrentKungSum8ByGraph.prefixGraph.toString)
    }
    test("should deserialize PrefixGraph and generate correct adder.") {
      val d = new CommonPrefixSum with HasPrefixSumWithGraphImp {
        val prefixGraph: PrefixGraph = PrefixGraph(os.resource / "graph.json")
      }
      verify(new PrefixAdder(d.prefixGraph.width - 1, d), Seq(BoundedCheck(1)))
    }
    test("should abort in PrefixNode generation") {
      try {
        PrefixNode(zeroLayer(2), node2)
      } catch {
        case _: java.lang.IllegalArgumentException => true
        case _: Throwable => false
      }
    }
    test("DemoPrefixAdderWithGraph should pass test") {
      verify(new DemoPrefixAdderWithGraph, Seq(BoundedCheck(1)))
    }
    test("should generate graphML file") {
      os.write.over(os.pwd / "PrefixGraph.dot", PrefixGraph(zeroLayer.toSet + node1 + node2 + node3 + node4).toString)
    }
  }
}