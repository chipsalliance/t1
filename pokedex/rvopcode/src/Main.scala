package org.chipsalliance.t1.pokedex.rvopcode

import mainargs._
import scala.io.AnsiColor._
import org.chipsalliance.rvdecoderdb.Instruction

case class CSR(csrname: String, csrnumber: String, csrindex: Int)

case class OpcodeInfoParams(
  riscvOpcodesSrc:        os.Path,
  customOpcodesSrc:       Option[os.Path],
  enabledInstructionSets: Seq[String])
class OpcodeInfo(params: OpcodeInfoParams):
  private lazy val rvdecoderdb = org.chipsalliance.rvdecoderdb
    .instructions(params.riscvOpcodesSrc, params.customOpcodesSrc)

  def dumpInstructions() =
    // rv32_i has special pseudo form and contains duplicate instructions that needs to be manually handled
    val rv32Instructions = params.enabledInstructionSets
      .filter(_ == "rv32_i")
      .flatMap(_ =>
        rvdecoderdb
          .filter(inst => inst.instructionSets.head.name == "rv32_i")
          .filter(inst => !inst.name.endsWith("_rv32"))
      )

    val excluded = Seq("rv32_i", "rv32_c", "rv_c", "rv32_c_f")

    val realInstruction = rvdecoderdb
      .filter(inst =>
        (!excluded.contains(inst.instructionSets.head.name))
          && params.enabledInstructionSets.contains(inst.instructionSets.head.name)
      )
      .filter(_.pseudoFrom.isEmpty)

    def instToMeta(inst: Instruction, width: Int) = Map(
      "name"            -> inst.name,
      "bit_width"       -> width.toString(),
      "bit_pattern"     -> inst.encoding.toCustomBitPat("x", width),
      "instruction_set" -> inst.instructionSets.head.name
    )

    // Count and sort the amount of bit pattern: we always wants the pattern with
    // least granularity to be at the end of the pattern match list.
    val allInstructions = (rv32Instructions ++ realInstruction)
      .map(inst => (inst, inst.encoding.toCustomBitPat("x", 32)))
      // first sort to ensure bits is listed from smallest to largest
      .sortBy((_, encoding) => encoding)
      // second sort to ensure most concrete pattern match are on top
      .sortBy((_, encoding) => encoding.count(b => b == 'x'))
      .map((inst, _) => instToMeta(inst, 32))

    val rvcInstruction = params.enabledInstructionSets
      .filter(Seq("rv_c", "rv32_c", "rv32_c_f").contains(_))
      .distinctBy(_ => true)
      .flatMap(_ =>
        rvdecoderdb
          .filter(inst => Seq("rv_c", "rv32_c", "rv32_c_f").contains(inst.instructionSets.head.name))
          .filter(inst => !inst.name.endsWith("_rv32"))
      )
      .map(inst => (inst, inst.encoding.toCustomBitPat("x", 16)))
      .sortBy((_, encoding) => encoding)
      .sortBy((_, encoding) => encoding.count(b => b == 'x'))
      .map((inst, _) => instToMeta(inst, 16))

    allInstructions ++ rvcInstruction

  def dumpCauses() =
    org.chipsalliance.rvdecoderdb
      .causes(params.riscvOpcodesSrc)
      .toSeq
      .sortBy((_, number) => number)
      .map((name, addr) =>
        Map(
          "name" -> name,
          "addr" -> s"0x${Integer.toString(addr, 16)}"
        )
      )

object Main:
  import upickle.default._

  implicit object PathRead extends TokensReader.Simple[os.Path]:
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))

  @main
  case class CommonArgs(
    @arg(
      short = 'o',
      name = "output",
      doc = "Output path for generated file"
    )
    output:            os.Path,
    @arg(
      short = 'd',
      name = "riscv-opcodes-src-dir",
      doc = "Path to the riscv-opcodes source"
    ) riscvOpcodesSrc: os.Path,
    @arg(
      short = 'c',
      name = "custom-opcodes-src-dir",
      doc = "Path to the custom opcodes source"
    ) customOpcodesSrc: Option[os.Path]):
    def convert(enabledInstructionSets: Seq[String] = Seq.empty) = OpcodeInfoParams(
      riscvOpcodesSrc = riscvOpcodesSrc,
      customOpcodesSrc = customOpcodesSrc,
      enabledInstructionSets = enabledInstructionSets
    )

  implicit def commonArgsParser: ParserForClass[CommonArgs] = ParserForClass[CommonArgs]

  @main
  def causes(args: CommonArgs) =
    val generator = new OpcodeInfo(args.convert())
    val obj       = Map("causes" -> generator.dumpCauses())
    os.write.over(args.output, write(obj))

  @main
  def instructions(
    @arg(short = 'e', name = "enable-instruction-sets", doc = "List of instruction sets enabled")
    enabledInstructionSets: Seq[String],
    args:                   CommonArgs
  ) =
    val generator    = new OpcodeInfo(args.convert(enabledInstructionSets))
    val instructions = Map("instructions" -> generator.dumpInstructions())
    os.write.over(args.output, write(instructions))

  @main
  def csr(dir: os.Path, output: os.Path) =
    val csr_meta = os
      .walk(dir)
      .filter(p => p.last.endsWith(".asl"))
      .flatMap(p =>
        p.baseName match
          case s"${mode}_${addr}_${name}" => Some((name, Integer.parseInt(addr, 16), p.last))
          case _                          => None
      )
      .groupBy(identity)
      .view
      .map((item, impl) => {
        if impl.length != 1 then println(s"${BOLD}${YELLOW}WARNING:${RESET} CSR ${item._1} implementation duplicate")

        val (name, addr, filename) = item

        Map(
          "name"       -> name,
          "filename"   -> filename,
          "hex_addr"   -> s"0x${Integer.toString(addr, 16)}",
          "bin_addr"   -> intToBin(addr, 12),
          // 0x000 - 0xbff are RW; 0xc00 - 0xfff are RO
          "read_write" -> s"${addr < 0xc00}"
        )
      })
    os.write.over(output, write(Map("csr_metadata" -> csr_meta)))

  def intToBin(i: Int, length: Int) =
    val bin = i.toBinaryString
    if bin.length > length then
      throw new Exception(
        s"input ${i} has bits length larger than the expected: ${bin.length} > ${length}"
      )

    if bin.length == length then bin
    else "0".repeat(length - bin.length) + bin

  def main(args: Array[String]): Unit =
    ParserForMethods(this).runOrExit(args.toIndexedSeq)
