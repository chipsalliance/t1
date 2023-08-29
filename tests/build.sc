// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

import mill._

trait Case extends Module {
  def config: String
  def moduleName: String
  override def millSourcePath = os.pwd / moduleName
  def sourceDir: T[os.Path] = T(millSourcePath)
  def allSourceFiles = T.sources {
    os.walk(sourceDir())
      .filter(os.isFile(_))
      .filter(p => testConfig().obj("sources").arr.map(_.str).contains(p.last))
      .map(PathRef(_))
  }
  def configFile = T(os.pwd / "configs" / s"$config-$moduleName.json")
  def testConfig = T(ujson.read(os.read(configFile())))
  def vLen = T(testConfig().obj("vlen").num.toInt)
  def xLen = T(testConfig().obj("xlen").num.toInt)
  def hasFP = T(testConfig().obj("fp").bool)
  def compileOptions = T(testConfig().obj("compileOptions").arr.map(_.str))
  def includeDir = T(Seq[PathRef]())
  // TODO: merge to compileOptions
  // def linkOpts = T(Seq("-mno-relax", "-fno-PIC"))
  def elf: T[PathRef] = T {
    os.proc(
      Seq(
        if (xLen() == 32) "clang-rv32" else "clang-rv64",
        "-o",
        config + ".elf"
      ) ++
        compileOptions() ++
        includeDir().map(p => s"-I${p.path}") ++
        allSourceFiles().map(_.path.toString)
    ).call(T.ctx.dest)
    PathRef(T.ctx.dest / (config + ".elf"))
  }
}

// go-based Codegen test
object codegen
   extends Cross[CodeGenCase](
  os.walk(os.pwd / "configs")
    .filter(_.ext == "json")
    .filter(f => ujson.read(os.read(f)).obj("type").str == "codegen")
    .map(f => ujson.read(os.read(f)).obj("name").str)
)

trait CodeGenCase
  extends Case 
    with Cross.Module[String] {
  val config: String = crossValue
  override def moduleName = "codegen"
  def codeGenConfig = T(testConfig().obj("name").str)
  // String => PathRef[os.Path]
  def codegenCaseGenerator = T( sys.env.get("CODEGEN_BIN_PATH").map( rawStr => PathRef(os.Path(rawStr)) ).get )
  override def includeDir = T {
    // User can set CODEGEN_INC_PATH to "/path/to/lib /path/to/another/lib /path/to/yet/another/lib"
    sys.env.get("CODEGEN_INC_PATH").map(raw =>
      // Array[String] => Seq[os.PathRef]
      raw.split(' ').map( each => PathRef(os.Path(each)) ).toSeq
    ).get
  }
  // String => os.Path
  def asmTestConfigDir = sys.env.get("CODEGEN_CFG_PATH").map(str => os.Path(str)).get
  override def allSourceFiles = T.sources {
    val output = T.dest / s"$config.S"
    os.proc(
      codegenCaseGenerator().path,
      "-VLEN",
      vLen(),
      "-XLEN",
      xLen(),
      "-outputfile",
      output,
      "-configfile",
      asmTestConfigDir / s"${codeGenConfig()}.toml"
    ).call(T.dest)
    Seq(PathRef(output))
  }
}

// Intrinsic Cases
object intrinsic
  extends Cross[IntrinsicCase](
      os.walk(os.pwd / "configs")
        .filter(_.ext == "json")
        .filter(f => ujson.read(os.read(f)).obj("type").str == "intrinsic")
        .map(f => ujson.read(os.read(f)).obj("name").str)
    )

trait IntrinsicCase
  extends Case 
    with Cross.Module[String] {
  val config: String = crossValue
  override def moduleName = "intrinsic"
}

// Case from mlir tests
object mlir
  extends Cross[BuddyMLIRCase](
  os.walk(os.pwd / "configs")
    .filter(_.ext == "json")
    .filter(f => ujson.read(os.read(f)).obj("type").str == "mlir")
    .map(f => ujson.read(os.read(f)).obj("name").str)
)

trait BuddyMLIRCase
  extends Case 
    with Cross.Module[String] {
  val config: String = crossValue
  override def moduleName = "mlir"
  def buddyOptArg = T(testConfig().obj("buddyOptArg").arr.map(_.str))

  override def allSourceFiles = T.sources {
    // TODO: split them into different
    val buddy = T.dest / s"$config.buddy"
    val llvmir = T.dest / s"$config.llvmir"
    val asm = T.dest / s"$config.S"
    val otherAsmSrcs = super.allSourceFiles().filter(_.path.ext == "S")

    T.log.info(s"run buddy-opt with arg [${buddyOptArg().mkString(", ")}]")
    os.proc(
      "buddy-opt",
      super.allSourceFiles().filter(_.path.ext == "mlir").head.path,
      buddyOptArg()
    ).call(T.dest, stdout = buddy)
    os.proc(
      "buddy-translate",
      "--buddy-to-llvmir"
    ).call(T.dest, stdin = buddy, stdout = llvmir)
    os.proc(
      "buddy-llc",
      "-mtriple",
      "riscv32",
      "-target-abi",
      "ilp32",
      "-mattr=+m,+d,+v",
      "-riscv-v-vector-bits-min=128",
      "--filetype=asm",
      "-o",
      asm
    ).call(T.dest, stdin = llvmir)

    otherAsmSrcs :+ PathRef(asm)
  }
}

// ASM
object asm
  extends mill.Cross[AsmCase](
      os.walk(os.pwd / "configs")
        .filter(_.ext == "json")
        .filter(f => ujson.read(os.read(f)).obj("type").str == "asm")
        .map(f => ujson.read(os.read(f)).obj("name").str)
)

trait AsmCase
  extends Case 
    with Cross.Module[String] {
  val config: String = crossValue
  override def moduleName = "asm"
}

object caseBuild
  extends Cross[CaseBuilder](
  os.walk(os.pwd / "configs")
    .filter(_.ext == "json")
    .map(f => s"${ujson.read(os.read(f)).obj("name").str}-${ujson.read(os.read(f)).obj("type").str}")
)

trait CaseBuilder
  extends Cross.Module[String] {
  val task: String = crossValue
  def run = T {
    // prepare
    val outputDir = os.pwd / os.up / "tests-out"
    os.remove.all(os.pwd / "out")
    os.makeDir.all(outputDir)
    val IndexedSeq(name, module) = task.split("-").toSeq
    os.makeDir.all(outputDir / "cases" / module)
    os.makeDir.all(outputDir / "configs")

    // build elf
    val rawElfPath = os.proc("mill", "--no-server", "show", s"$module[$name].elf").call(os.pwd).out.text
    val elfPath = os.Path(ujson.read(rawElfPath).str.split(":")(2))

    // write elf path into test config
    val origConfig = ujson.read(os.read(os.pwd / "configs" / s"$task.json"))
    origConfig("elf") = ujson.Obj("path" -> s"cases/$module/${elfPath.last}")

    // install, override if file exists
    os.move.into(elfPath, outputDir / "cases" / module, true)
    os.write.over(outputDir / "configs" / s"$task.json", ujson.write(origConfig))
  }
}
