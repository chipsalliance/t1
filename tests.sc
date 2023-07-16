import mill._

// For each test, it has unique name in the current test suites
// the elf should generate its binary,
// but for most test specific configurations, designers should provide a json file
trait Case extends Module {
  def config: String
  def sourceDir: T[os.Path] = T(millSourcePath)
  def name: T[String] = T(testConfig().obj("name").str)
  def configDir = T(os.pwd / "tests" / "configs")
  def configFile = T(configDir() / s"$config.json")
  def testConfig = T(ujson.read(os.read(configFile())))
  def vLen = T(testConfig().obj("vlen").num.toInt)
  def xLen = T(testConfig().obj("xlen").num.toInt)
  def hasFP = T(testConfig().obj("fp").bool)
  def compileOptions = T(testConfig().obj("compileOptions").arr.map(_.str))
  def allSourceFiles: T[Seq[PathRef]] = T(testConfig().obj("sources").arr.map(s => PathRef(sourceDir() / s.str)).toSeq)
  def includeDir = T(Seq[PathRef]())
  // TODO: merge to compileOptions
  def linkOpts = T(Seq("-mno-relax", "-fno-PIC"))
  def elf: T[PathRef] = T {
    os.proc(Seq(
      if(xLen() == 32) "clang-rv32" else "clang-rv64",
      "-o", name() + ".elf",
      // TODO: merge to compileOptions
      "-mabi=ilp32f", "-march=rv32gcv"
    ) ++ compileOptions() ++ linkOpts() ++ includeDir().map(p => s"-I${p.path}") ++ allSourceFiles().map(_.path.toString)).call(T.ctx.dest)
    PathRef(T.ctx.dest / (name() + ".elf"))
  }
}

// TODO: add router
object router extends mill.Cross[Router](os.walk(os.pwd / "tests" / "configs").filter(_.ext == "json").map(_.baseName))
class Router(config: String) extends Module {
  def elf = T {
    config match {
      case "codegen" => codegen(config).elf()
      case "intrinsic" => intrinsic(config).elf()
      case "buddy" => buddy(config).elf()
      case "asm" => asm(config).elf()
      case _ => throw new Exception(s"Unknown test config $config")
    }
  }
}
// go-based Codegen test
object codegen extends mill.Cross[CodeGenCase](
  os.walk(os.pwd / "tests" / "configs" / "codegen").filter(_.ext == "json").map(_.baseName)
)
class CodeGenCase(val config: String) extends Case {
  def codeGenConfig = T(testConfig().obj("config").str)
  override def configDir = T(super.configDir() / "codegen")
  override def includeDir = T {
    Seq(
      codegenCaseGenerator.millSourcePath / "env" / "sequencer-vector",
      codegenCaseGenerator.millSourcePath / "macros" / "sequencer-vector"
    ).map(PathRef(_))
  }
  override def linkOpts = T(Seq("-mno-relax", "-static", "-mcmodel=medany", "-fvisibility=hidden", "-nostdlib", "-fno-PIC"))
  override def allSourceFiles: T[Seq[PathRef]] = T {
    val output = T.dest / s"${name()}.S"
    os.proc(
      codegenCaseGenerator.asmGenerator().path,
      "-VLEN", vLen(),
      "-XLEN", xLen(),
      "-outputfile", output,
      "-configfile", codegenCaseGenerator.millSourcePath / "configs" / s"${codeGenConfig()}.toml"
    ).call(T.dest)
    Seq(PathRef(output))
  }
}
// standalone unit to compile the codegen
object codegenCaseGenerator extends Module { u =>
  override def millSourcePath = os.pwd / "dependencies" / "riscv-vector-tests"
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
object intrinsic extends mill.Cross[IntrinsicCase](
  os.walk(os.pwd / "tests" /"configs" / "intrinsic").filter(_.ext == "json").map(_.baseName)
)
class IntrinsicCase(val config: String) extends Case {
  override def linkOpts = T(Seq("-mno-relax", "-static", "-mcmodel=medany", "-fvisibility=hidden", "-nostdlib", "-Wl,--entry=start", "-fno-PIC"))
}

// Case from buddy compiler
object buddy extends mill.Cross[BuddyMLIRCase](
  os.walk(os.pwd / "tests" / "configs" / "buddy").filter(_.ext == "json").map(_.baseName)
)

class BuddyMLIRCase(val config: String) extends Case {
  // TODO: provide it in json
  override def linkOpts = T {
    Seq("-mno-relax", "-static", "-mcmodel=medany", "-fvisibility=hidden", "-nostdlib", "-Wl,--entry=start", "-fno-PIC")
  }

  // Parse the header comment of the given file to get the command line arguments to pass to the buddy-opt.
  // These arguments must be wrapped in a block, starting with "BUDDY-OPT" and ending with "BUDDY-OPT-END".
  // TODO: provide it in json
  def parseBuddyOptArg(testFile: os.Path) = os.read
        .lines(testFile)
        .dropWhile(bound => bound.startsWith("//") && bound.contains("BUDDY-OPT"))
        .takeWhile(bound => bound.startsWith("//") && !bound.contains("BUDDY-OPT-END"))
        .map(lines => lines.stripPrefix("//").trim().split(" "))
        .flatten

  override def allSourceFiles: T[Seq[PathRef]] = T {
    val buddy = T.dest / s"${name()}.buddy"
    val llvmir = T.dest / s"${name()}.llvmir"
    val asm = T.dest / s"${name()}.S"
    // TODO: fix it
    val buddyOptArg = Seq()

    T.log.info(s"run buddy-opt with arg ${buddyOptArg}")
    os.proc(
      "buddy-opt",
      super.allSourceFiles().head.path,
      buddyOptArg
    ).call(T.dest, stdout = buddy)
    os.proc(
      "buddy-translate", "--buddy-to-llvmir"
    ).call(T.dest, stdin = buddy, stdout = llvmir)
    os.proc(
      "buddy-llc",
      "-mtriple", "riscv32",
      "-target-abi", "ilp32",
      "-mattr=+m,+d,+v",
      "-riscv-v-vector-bits-min=128",
      "--filetype=asm",
      "-o", asm
    ).call(T.dest, stdin = llvmir)
    super.allSourceFiles() ++ Seq(PathRef(asm))
  }
}

// ASM
object asm extends mill.Cross[AsmCase](
  os.walk(os.pwd / "tests" / "configs" / "asm").filter(_.ext == "json").map(_.baseName)
)
class AsmCase(val config: String) extends Case {
  // TODO: read from json
  override def linkOpts = T {
    Seq("-mno-relax", "-static", "-mcmodel=medany", "-fvisibility=hidden", "-nostdlib", "-Wl,--entry=start", "-fno-PIC")
  }
}
