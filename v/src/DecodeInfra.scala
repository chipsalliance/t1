// TODO[1]: upstream to chisel3
package chisel3.util.experimental.decode

import chisel3._
import chisel3.util.BitPat
import chisel3.util.experimental.decode.TruthTable

import scala.collection.immutable.SeqMap

trait Instruction {
  def bitPat: BitPat
}

trait DecodeField[T <: Instruction, D <: Data] {
  def name: String

  def chiselType: D

  final def width: Int = chiselType.getWidth

  def dc: BitPat = BitPat.dontCare(width)

  def genTable(op: T): BitPat
}

trait BoolDecodeField[T <: Instruction] extends DecodeField[T, Bool] {
  def chiselType: Bool = Bool()

  def y: BitPat = BitPat.Y(1)

  def n: BitPat = BitPat.N(1)
}

class DecodeBundle(fields: Seq[DecodeField[_, _]]) extends Record with chisel3.experimental.AutoCloneType {
  require(fields.map(_.name).distinct.size == fields.size, "Field names must be unique")
  val elements: SeqMap[String, Data] = fields.map(k => k.name -> UInt(k.width.W)).to(SeqMap)

  def apply[D <: Data](field: DecodeField[_, D]): D = elements(field.name).asTypeOf(field.chiselType)
}

class DecodeTable[I <: Instruction](instructions: Seq[I], fields: Seq[DecodeField[I, _]]) {
  require(instructions.map(_.bitPat.getWidth).distinct.size == 1, "All instructions must have the same width")

  def bundle: DecodeBundle = new DecodeBundle(fields)

  private val _table: SeqMap[BitPat, BitPat] = instructions.map { op =>
    op.bitPat -> fields.reverse.map(field => field.genTable(op)).reduce(_ ## _)
  }.to(SeqMap)
  val table: TruthTable = TruthTable(_table, BitPat.dontCare(_table.head._2.getWidth))

  def decode(instruction: UInt): DecodeBundle = chisel3.util.experimental.decode.decoder(instruction, table).asTypeOf(bundle)
}
