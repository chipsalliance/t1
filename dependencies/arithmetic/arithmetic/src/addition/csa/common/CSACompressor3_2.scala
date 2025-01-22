package addition.csa.common

import addition.csa.CSACompressor
import chisel3._

object CSACompressor3_2 extends CSACompressor(3, 2) {
  val encodeTable: Map[BigInt, Seq[BigInt]] = tableBuilder(
    Map(
      // 0
      "00" -> Seq(
        "000"
      ),
      // 1
      "01" -> Seq(
        "100",
        "010",
        "001"
      ),
      // 2
      "10" -> Seq(
        "110",
        "011",
        "101"
      ),
      // 3
      "11" -> Seq(
        "111"
      )
    )
  )

  def circuit(inputs: Seq[Bool]): Seq[Bool] = {
    val xor01 = inputs(0) ^ inputs(1)

    Seq(inputs(0) & inputs(1) | (xor01 & inputs(2)), xor01 ^ inputs(2))
  }
}
