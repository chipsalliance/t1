// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2023 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.rvdecoderdb

import upickle.default.{ReadWriter => RW, macroRW}

object Encoding {
  implicit val rw: RW[Encoding] = macroRW

  def fromString(str: String): Encoding = {
    require(str.length == 32)
    Encoding(
      str.reverse.zipWithIndex.map {
        case (c, i) =>
          c match {
            case '1' => BigInt(1) << i
            case '0' => BigInt(0)
            case '?' => BigInt(0)
          }
      }.sum,
      str.reverse.zipWithIndex.map {
        case (c, i) =>
          c match {
            case '1' => BigInt(1) << i
            case '0' => BigInt(1) << i
            case '?' => BigInt(0)
          }
      }.sum
    )
  }
}

/** Like chisel3.BitPat, this is a 32-bits field stores the Instruction encoding. */
case class Encoding(value: BigInt, mask: BigInt) {
  override def toString =
    Seq.tabulate(32)(i => if (!mask.testBit(i)) "?" else if (value.testBit(i)) "1" else "0").reverse.mkString
}

object Arg {
  implicit val rw: RW[Arg] = macroRW
}

case class Arg(name: String, msb: Int, lsb: Int) {
  override def toString: String = name
}

object InstructionSet {
  implicit val rw: RW[InstructionSet] = macroRW
}

/** represent an riscv sub instruction set, aka a file in riscv-opcodes. */
case class InstructionSet(name: String)

object Instruction {
  implicit val rw: RW[Instruction] = macroRW
}

/** All information can be parsed from riscv/riscv-opcode.
  *
  * @param name            name of this instruction
  * @param encoding        encoding of this instruction
  * @param instructionSets base instruction set that this instruction lives in
  * @param pseudoFrom      if this is defined, means this instruction is an Pseudo Instruction from another instruction
  * @param ratified        true if this instruction is ratified
  */
case class Instruction(
  name:            String,
  encoding:        Encoding,
  args:            Seq[Arg],
  instructionSets: Seq[InstructionSet],
  pseudoFrom:      Option[Instruction],
  ratified:        Boolean,
  custom:          Boolean) {
  require(!custom || (custom && !ratified), "All custom instructions are unratified.")

  def instructionSet: InstructionSet = instructionSets.head

  def importTo: Seq[InstructionSet] = instructionSets.drop(1)

  def simpleName = s"${instructionSet.name}::$name"

  override def toString: String =
    instructionSet.name.padTo(16, ' ') +
      s"$name${pseudoFrom.map(_.simpleName).map(s => s" [pseudo $s]").getOrElse("")}".padTo(48, ' ') +
      s"[${Seq(
        Option.when(Utils.isR(this))("R "),
        Option.when(Utils.isR4(this))("R4"),
        Option.when(Utils.isI(this))("I "),
        Option.when(Utils.isS(this))("S "),
        Option.when(Utils.isB(this))("B "),
        Option.when(Utils.isU(this))("U "),
        Option.when(Utils.isJ(this))("J ")
      ).flatten.headOption.getOrElse("  ")}]".padTo(4, ' ') +
      args.mkString(",").padTo(40, ' ') +
      encoding.toString.padTo(48, ' ') +
      ("[" +
        Option.when(custom)("CUSTOM ").getOrElse(Option.when(!ratified)("UNRATIFIED ").getOrElse("")) +
        (if (importTo.nonEmpty) Some(importTo.map(_.name).mkString(",")) else None)
          .map(s => s"import to $s")
          .getOrElse("") +
        "]").replace(" ]", "]").replace("[]", "")
}
