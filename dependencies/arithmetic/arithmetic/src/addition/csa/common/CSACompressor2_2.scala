package addition.csa.common

import addition.csa.CSACompressor
import chisel3._

object CSACompressor2_2 extends CSACompressor(2, 2) {
  val encodeTable: Map[BigInt, Seq[BigInt]] = tableBuilder(
    Map(
      // 0
      "00" -> Seq(
        "00"
      ),
      // 1
      "01" -> Seq(
        "01",
        "10"
      ),
      // 2
      "10" -> Seq(
        "11"
      )
    )
  )

  def circuit(inputs: Seq[Bool]): Seq[Bool] = {
    Seq(inputs(0) & inputs(1), inputs(0) ^ inputs(1))
  }
}
