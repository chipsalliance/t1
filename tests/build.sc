import mill._

trait Case extends Module {
  def config: String
  def sourceDir: T[os.Path] = T(millSourcePath)
  def allSourceFiles = T.sources {
    os.walk(sourceDir())
      .filter(os.isFile(_))
      .filter(p => testConfig().obj("sources").arr.map(_.str).contains(p.last))
      .map(PathRef(_))
  }
  def configFile = T(os.pwd / "configs" / s"$config.json")
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
    extends mill.Cross[CodeGenCase](
      os.walk(os.pwd / "configs")
        .filter(_.ext == "json")
        .filter(f => ujson.read(os.read(f)).obj("type").str == "codegen")
        .map(_.baseName): _*
    )
class CodeGenCase(val config: String) extends Case {
  override def millSourcePath = os.pwd / "codegen"
  def codeGenConfig = T(testConfig().obj("config").str)
  def genelf = T(codegenCaseGenerator.asmGenerator().path)
  override def includeDir = T {
    Seq(
      millSourcePath / "env" / "sequencer-vector",
      millSourcePath / "macros" / "sequencer-vector"
    ).map(PathRef(_))
  }
  override def allSourceFiles = T.sources {
    val output = T.dest / s"$config.S"
    os.proc(
      codegenCaseGenerator.asmGenerator().path,
      "-VLEN",
      vLen(),
      "-XLEN",
      xLen(),
      "-outputfile",
      output,
      "-configfile",
      millSourcePath / "configs" / s"${codeGenConfig()}.toml"
    ).call(T.dest)
    Seq(PathRef(output))
  }
}
object codegenCaseGenerator extends CodegenCaseGenerator
// standalone unit to compile the codegen
class CodegenCaseGenerator extends Module { u =>
  override def millSourcePath = os.pwd / "codegen"
  def allGoSources = T.sources(os.walk(millSourcePath).filter(f => f.ext == "go" || f.last == "go.mod").map(PathRef(_)))
  def asmGenerator = T {
    // depends on GO
    allGoSources()
    val elf = T.dest / "generator"
    os.proc("go", "build", "-o", elf, "single/single.go").call(cwd = millSourcePath)
    PathRef(elf)
  }
}

// Intrinsic Cases
object intrinsic
    extends mill.Cross[IntrinsicCase](
      os.walk(os.pwd / "configs")
        .filter(_.ext == "json")
        .filter(f => ujson.read(os.read(f)).obj("type").str == "intrinsic")
        .map(_.baseName): _*
    )
class IntrinsicCase(val config: String) extends Case {
  override def millSourcePath = os.pwd / "intrinsic"
}

// Case from buddy compiler
object buddy
    extends mill.Cross[BuddyMLIRCase](
      os.walk(os.pwd / "configs")
        .filter(_.ext == "json")
        .filter(f => ujson.read(os.read(f)).obj("type").str == "buddy")
        .map(_.baseName): _*
    )
class BuddyMLIRCase(val config: String) extends Case {
  override def millSourcePath = os.pwd / "buddy"
  def buddyOptArg = T(testConfig().obj("buddyOptArg").arr.map(_.str))

  override def allSourceFiles = T.sources {
    // TODO: split them into different
    val buddy = T.dest / s"$config.buddy"
    val llvmir = T.dest / s"$config.llvmir"
    val asm = T.dest / s"$config.S"

    T.log.info(s"run buddy-opt with arg [${buddyOptArg().mkString(", ")}]")
    os.proc(
      "buddy-opt",
      super.allSourceFiles().head.path,
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
    Seq(PathRef(asm))
  }
}

// ASM
object asm
    extends mill.Cross[AsmCase](
      os.walk(os.pwd / "configs")
        .filter(_.ext == "json")
        .filter(f => ujson.read(os.read(f)).obj("type").str == "asm")
        .map(_.baseName): _*
    )
class AsmCase(val config: String) extends Case {
  override def millSourcePath = os.pwd / "asm"
}
