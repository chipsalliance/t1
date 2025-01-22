package addition.csa

import chisel3._
import chisel3.util.{pla, BitPat}

/** A [[CSACompressor]] encodes the number of 1 from input bool sequence to `n` bits output.
  * So `m+1` terms are encoded to `n` bits output.
  *
  * for example a classic csa 3in 2out, which encodes:
  * {{{
  *   input          number of 1     output
  *   000         -> 0            -> 00
  *   100 010 001 -> 1            -> 01
  *   110 101 011 -> 2            -> 10
  *   111         -> 3            -> 11
  * }}}
  */
abstract class CSACompressor(val inputSize: Int, val outputSize: Int) {
  require(math.pow(2, outputSize) >= inputSize, "not enough output bits to encode.")

  lazy val decodeTable = encodeTable.map {
    case (key, values) =>
      key -> {
        val one = values.map { v =>
          v.bitCount
        }.distinct
        require(one.size == 1)
        one.head
      }
  }

  val encodeTable: Map[BigInt, Seq[BigInt]]

  def decode(outputBits: UInt): UInt = util.MuxLookup(outputBits, 0.U)(
    decodeTable.map { case (k, v) => k.U -> v.U }.toSeq
  )

  def circuit(inputs: Seq[Bool]): Seq[Bool]

  def generateCircuit(inputs: Seq[Bool], formal: Boolean): Seq[Bool] = {
    val out = circuit(inputs)
    if (formal) {
      val (refIn, refOut) = pla(
        encodeTable.flatMap {
          case (key, value) =>
            value.map(v => v -> key)
        }.map {
          case (key, value) =>
            BitPat(key.U(inputSize.W)) -> BitPat(value.U(outputSize.W))
        }.toSeq
      )
      refIn := VecInit(inputs.reverse).asUInt
      assert(VecInit(out.reverse).asUInt === refOut)
    }
    out
  }

  protected def tableBuilder(table: Map[String, Seq[String]]): Map[BigInt, Seq[BigInt]] = table.map {
    case (k, v) => BigInt(k, 2) -> v.map(BigInt(_, 2))
  }
}
