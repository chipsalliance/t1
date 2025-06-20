package org.chipsalliance.t1.pokedex.codegen

import mainargs._

case class CSR(csrname: String, csrnumber: String, csrindex: Int)

case class CodeGeneratorParams(
  modelDir:         os.Path,
  outputDir:        os.Path,
  riscvOpCodesSrc:  os.Path,
  customOpCodesSrc: Option[os.Path])
class CodeGenerator(params: CodeGeneratorParams):
  private lazy val outputDir =
    os.makeDir.all(params.outputDir)
    params.outputDir

  private val csr_op_path               = outputDir / "csr_op.asl"
  private val execute_path              = outputDir / "execute.asl"
  private val arg_lut_path              = outputDir / "arg_lut.asl"
  private val user_inst_path            = params.modelDir / "extensions"
  private val user_csr_path             = params.modelDir / "csr"
  private lazy val enabledExtensionSets = os
    .walk(user_inst_path)
    .filter(os.isDir)
    .filter(_.baseName.startsWith("rv"))
    .map(_.baseName)

  private lazy val rvdecoderdb = org.chipsalliance.rvdecoderdb
    .instructions(params.riscvOpCodesSrc, params.customOpCodesSrc)

  private lazy val argLutDb =
    val db = org.chipsalliance.rvdecoderdb
      .argLut(params.riscvOpCodesSrc, params.customOpCodesSrc)
    if db.isEmpty then
      throw new Exception(
        "fail generating arg lut handler, please check the input opcodes path"
      )
    db

  private lazy val xlen: Int =
    val length = enabledExtensionSets
      .map({
        case "rv32_i" => 32
        case _        => 0
      })
      .sum()
    if length < 32 then
      throw new Exception(
        "Missing implementation for rv32_i in extensions/ directory"
      )
    else if length > 32 then
      throw new Exception(
        "XLEN too large, unsupported implementation found in extensions/ directory"
      )
    length

  def intToBin(i: Int, length: Int) =
    val bin = i.toBinaryString
    if bin.length > length then
      throw new Exception(
        s"input ${i} has bits length larger than the expected: ${bin.length} > ${length}"
      )

    if bin.length == length then bin
    else "0".repeat(length - bin.length) + bin

  def genCSRsOperation() =
    val csrDB = os
      .walk(user_csr_path / "read")
      .filter(_.ext == "asl")
      .map(p =>
        p.baseName match {
          case s"${csrname}_${csrnumber}" =>
            CSR(csrname, csrnumber, Integer.parseInt(csrnumber, 16))
        }
      )

    val csrRead = csrDB.map({ case CSR(csrname, csrnumber, _) =>
      csrname -> os.read(
        user_csr_path / "read" / s"${csrname}_${csrnumber}.asl"
      )
    })

    val csrWrite = csrDB.map({
      case CSR(csrname, csrnumber, _) => {
        val filename = user_csr_path / "write" / s"${csrname}_${csrnumber}.asl"
        if !os.exists(filename) then
          throw new Exception(
            s"${csrname} implemented read functions but no write function implemented"
          )

        csrname -> os.read(filename)
      }
    })

    val csrReadDispatch = csrDB
      .map({ case CSR(csrname, _, csrindex) =>
        s"""|
            |  when '${intToBin(csrindex, 12)}' =>
            |    return Read_${csrname.toUpperCase}();
            |""".stripMargin
      })
      .mkString("\n")

    val csrReadHandlers = csrRead
      .map({ case (csrname, body) =>
        s"""|
            |func Read_${csrname.toUpperCase}() => bits(${xlen})
            |begin
            |
            |${body}
            |
            |end
            |""".stripMargin
      })
      .mkString("\n")

    val csrWriteDispatch = csrDB
      .map({ case CSR(csrname, _, csrindex) =>
        s"""|
            |  when '${intToBin(csrindex, 12)}' =>
            |    return Write_${csrname.toUpperCase}(value);
            |""".stripMargin
      })
      .mkString("\n")

    val csrWriteHandlers = csrWrite
      .map({ case (csrname, body) =>
        s"""|
            |func Write_${csrname.toUpperCase}(value : bits(32))
            |begin
            |
            |${body}
            |
            |end
            |""".stripMargin
      })
      .mkString("\n")

    os.write.over(
      csr_op_path,
      s"""|
          |// CSR Dispatch
          |func ReadCSR(csr : bits(12)) => bits(${xlen})
          |begin
          |  case csr of
          |    ${csrReadDispatch}
          |
          |    otherwise =>
          |      FFI_print_str("error: read of unknown CSR");
          |  end
          |end
          |
          |func WriteCSR(csr : bits(12), value : bits(32))
          |begin
          |  case csr of
          |    ${csrWriteDispatch}
          |
          |    otherwise =>
          |      FFI_print_str("error: write of unknown CSR");
          |  end
          |end
          |
          |${csrReadHandlers}
          |
          |${csrWriteHandlers}
          |""".stripMargin
    )

  def genArgLuts() =
    val argLutsCode = argLutDb.values.map { case org.chipsalliance.rvdecoderdb.Arg(name, hi, lo) =>
      s"""|
          |func GetArg_${name.toUpperCase}(instruction: bits(32)) => bits(${hi - lo + 1})
          |begin
          |  return instruction[${hi}:${lo}];
          |end
          |""".stripMargin
    }
      .mkString("\n")

    os.write.over(arg_lut_path, argLutsCode)

  def genExecute() =
    // rv32_i has special pseudo form and contains duplicate instructions that needs to be manually handled
    val rv32Instructions = enabledExtensionSets
      .filter(_ == "rv32_i")
      .flatMap(_ =>
        rvdecoderdb
          .filter(inst => inst.instructionSets.head.name == "rv32_i")
          .filter(inst => !inst.name.endsWith("_rv32"))
      )

    val allInstructions = rvdecoderdb
      .filter(inst =>
        if inst.instructionSets.head.name == "rv32_i" then false
        else enabledExtensionSets.contains(inst.instructionSets.head.name)
      )
      .filter(_.pseudoFrom.isEmpty)

    val requiredInstructions = rv32Instructions ++ allInstructions

    val executeCode = requiredInstructions
      .map(inst => {
        val functionName = inst.name.replace(".", "_")
        val fnBodyPath   =
          user_inst_path / inst.instructionSets.head.name / s"${functionName}.asl"
        if !os.exists(fnBodyPath) then
          throw new Exception(
            s"instruction ${inst.name} not found at ${fnBodyPath}"
          )

        val functionBody = os.read(fnBodyPath)

        s"""|func Execute_${functionName.toUpperCase}(instruction : bits(32))
            |begin
            |
            |${functionBody}
            |
            |end
            |""".stripMargin
      })
      .mkString("\n")

    val matchArms    = requiredInstructions
      .map(inst => {
        val bitpat       = inst.encoding.toCustomBitPat("x")
        val functionName = inst.name.replace(".", "_").toUpperCase
        s"""|    when '${bitpat}' =>
            |        Execute_${functionName}(instruction);
            |""".stripMargin
      })
      .mkString("\n")
    val dispatchCode = s"""|func Execute(instruction : bits(32))
                           |begin
                           |    case instruction of
                           |${matchArms}
                           |    otherwise =>
                           |        FFI_print_str("TODO: throw illegal instruction");
                           |    end
                           |end
                           |""".stripMargin

    os.write.over(execute_path, executeCode + dispatchCode)

    val requiredInst = requiredInstructions.map(_.name.replace(".", "_")).toSet
    os.walk(user_inst_path)
      .filter(_.ext == "asl")
      .foreach(p => {
        val codeFile        = p.segments.toSeq.reverse.head
        val instructionName = codeFile.stripSuffix(".asl")
        if !requiredInst.contains(instructionName) then println(s"found not required file ${p}")

      })

  def run() =
    genExecute()
    genArgLuts()
    genCSRsOperation()

object Main:
  implicit object PathRead extends TokensReader.Simple[os.Path]:
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))

  @main
  def run(
    @arg(
      short = 'i',
      name = "input-model-dir",
      doc = "Path to ASL model implementation"
    )
    modelDir:           os.Path,
    @arg(
      short = 'o',
      name = "output-dir",
      doc = "Output directory path to generate sources"
    )
    outputDir:          os.Path,
    @arg(
      short = 'd',
      name = "riscv-opcodes-src-dir",
      doc = "Path to the riscv-opcodes source"
    ) riscvOpCodesSrc:  os.Path,
    @arg(
      short = 'c',
      name = "custom-opcodes-src-dir",
      doc = "Path to the custom opcodes source"
    ) customOpCodesSrc: Option[os.Path]
  ) =
    val param     = CodeGeneratorParams(
      modelDir,
      outputDir,
      riscvOpCodesSrc,
      customOpCodesSrc
    );
    val generator = new CodeGenerator(param);
    generator.run()

  def main(args: Array[String]): Unit =
    ParserForMethods(this).runOrExit(args.toIndexedSeq)
