package addition.csa.common

import addition.csa.CSACompressor
import chisel3._

object CSACompressor5_3 extends CSACompressor(5, 3) {
  val encodeTable: Map[BigInt, Seq[BigInt]] = tableBuilder(
    Map(
      // 0
      "000" -> Seq(
        "00000"
      ),
      // 1
      "001" -> Seq(
        "10000",
        "01000",
        "00100",
        "00010",
        "00001"
      ),
      // 2
      "010" -> Seq(
        // count(012) = 2
        "11000",
        "01100",
        "10100"
      ),
      "100" -> Seq(
        // count(012) = 1 count(34) = 1
        "10010",
        "01010",
        "00110",
        "10001",
        "01001",
        "00101",
        // count(012) = 0
        "00011"
      ),
      // 3
      "011" -> Seq(
        // count(012) = 3
        "11100",
        // count(012) = 2 count(34) = 1
        "11010",
        "01110",
        "10110",
        "11001",
        "01101",
        "10101"
      ),
      "101" -> Seq(
        // count(012) = 1 count(34) = 2
        "10011",
        "01011",
        "00111"
      ),
      // 4
      "110" -> Seq(
        "11110",
        "11101",
        "11011",
        "10111",
        "01111"
      ),
      // 5
      "111" -> Seq(
        "11111"
      )
    )
  )

  def circuit(inputs: Seq[Bool]): Seq[Bool] = {
    val ab = inputs(0) ^ inputs(1)
    val abc = ab ^ inputs(2)
    val de = inputs(3) ^ inputs(4)

    Seq(
      // count(de) == 2 | ((count(de) == 1) & (count(abc) == (1,3)))
      inputs(3) & inputs(4) | (de & abc),
      // count(abc) == (2,3)
      inputs(0) & inputs(1) | (ab & inputs(2)),
      // count(abcde) == (1,3,5)
      de ^ abc
    )
  }
}
