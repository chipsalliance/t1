import mill._
import mill.scalalib._
import mill.define.{TaskModule, Command}
import mill.scalalib.publish._
import mill.scalalib.scalafmt._
import mill.scalalib.TestModule.Utest
import coursier.maven.MavenRepository
import $file.dependencies.chisel3.build
import $file.dependencies.firrtl.build
import $file.dependencies.treadle.build
import $file.dependencies.chiseltest.build
import $file.dependencies.arithmetic.common
import $file.dependencies.tilelink.common
import $file.dependencies.hardfloat.build
import $file.common

object v {
  val scala = "2.13.6"
  val utest = ivy"com.lihaoyi::utest:latest.integration"
  val mainargs = ivy"com.lihaoyi::mainargs:0.3.0"
  // for arithmetic
  val bc = ivy"org.bouncycastle:bcprov-jdk15to18:latest.integration"
  val spire = ivy"org.typelevel::spire:latest.integration"
  val evilplot = ivy"io.github.cibotech::evilplot:latest.integration"
  // for hardfloat
  val parallel = ivy"org.scala-lang.modules:scala-parallel-collections_3:1.0.4"
}

object myfirrtl extends dependencies.firrtl.build.firrtlCrossModule(v.scala) {
  override def millSourcePath = os.pwd / "dependencies" / "firrtl"

  override val checkSystemAntlr4Version = false
  override val checkSystemProtocVersion = false
  override val protocVersion = os.proc("protoc", "--version").call().out.text.dropRight(1).split(' ').last
  override val antlr4Version = os.proc("antlr4").call().out.text.split('\n').head.split(' ').last
}

object mytreadle extends dependencies.treadle.build.treadleCrossModule(v.scala) {
  override def millSourcePath = os.pwd / "dependencies" / "treadle"

  def firrtlModule: Option[PublishModule] = Some(myfirrtl)
}

object mychisel3 extends dependencies.chisel3.build.chisel3CrossModule(v.scala) {
  override def millSourcePath = os.pwd / "dependencies" / "chisel3"

  def firrtlModule: Option[PublishModule] = Some(myfirrtl)

  def treadleModule: Option[PublishModule] = Some(mytreadle)

  def chiseltestModule: Option[PublishModule] = Some(mychiseltest)
}

object mychiseltest extends dependencies.chiseltest.build.chiseltestCrossModule(v.scala) {
  override def millSourcePath = os.pwd / "dependencies" / "chiseltest"

  def chisel3Module: Option[PublishModule] = Some(mychisel3)

  def treadleModule: Option[PublishModule] = Some(mytreadle)
}

object myarithmetic extends dependencies.arithmetic.common.ArithmeticModule {
  override def millSourcePath = os.pwd / "dependencies" / "arithmetic" / "arithmetic"

  def scalaVersion = T {
    v.scala
  }

  def chisel3Module: Option[PublishModule] = Some(mychisel3)

  def chisel3PluginJar = T {
    Some(mychisel3.plugin.jar())
  }

  def chiseltestModule = Some(mychiseltest)

  def spire: T[Dep] = v.spire

  def evilplot: T[Dep] = v.evilplot

  def bc: T[Dep] = v.bc

  def utest: T[Dep] = v.utest
}

object mytilelink extends dependencies.tilelink.common.TileLinkModule {
  override def millSourcePath = os.pwd / "dependencies" / "tilelink" / "tilelink"

  def scalaVersion = T {
    v.scala
  }

  def chisel3Module: Option[PublishModule] = Some(mychisel3)

  def chisel3PluginJar = T {
    Some(mychisel3.plugin.jar())
  }
}

object myhardfloat extends dependencies.`hardfloat`.build.hardfloat {
  override def millSourcePath = os.pwd /  "dependencies" / "hardfloat"

  override def scalaVersion = v.scala

  def chisel3Module: Option[PublishModule] = Some(mychisel3)

  override def ivyDeps = super.ivyDeps() ++ Agg(
    v.parallel
  )

  override def scalacOptions = T {
    Seq(s"-Xplugin:${mychisel3.plugin.jar().path}")
  }
  override def scalacPluginClasspath = T { super.scalacPluginClasspath() ++ Agg(
    mychisel3.plugin.jar()
  ) }
}

object vector extends common.VectorModule with ScalafmtModule {
  m =>
  def millSourcePath = os.pwd / "v"

  def scalaVersion = T {
    v.scala
  }

  def chisel3Module = Some(mychisel3)

  def chisel3PluginJar = T {
    Some(mychisel3.plugin.jar())
  }

  def chiseltestModule = Some(mychiseltest)

  def arithmeticModule = Some(myarithmetic)

  def hardfloatModule = Some(myhardfloat)

  def tilelinkModule = Some(mytilelink)

  def utest: T[Dep] = v.utest
}

object elaborator extends mill.Cross[Elaborator](os.walk(os.pwd / "configs").filter(_.ext == "json").map(_.baseName) :_*)
// Module to generate RTL from json config
// TODO: remove testbench
class Elaborator(config: String) extends Module {
  def configDir = T(os.pwd / "configs")
  def configFile = T(configDir() / s"$config.json")
  def designConfig = T(ujson.read(os.read(configFile()))("design"))
  def mfcArgs: T[Seq[String]] = T(ujson.read(os.read(configFile()))("mfcArgs").arr.map(_.str).toSeq)
  def isTestbench = T(ujson.read(os.read(configFile()))("testbench").bool)

  object elaborate extends ScalaModule with ScalafmtModule {
    override def millSourcePath: os.Path = os.pwd / "elaborator"
    override def scalacPluginClasspath = T(Agg(mychisel3.plugin.jar()))
    override def scalacOptions = T(super.scalacOptions() ++ Some(mychisel3.plugin.jar()).map(path => s"-Xplugin:${path.path}") ++ Seq("-Ymacro-annotations"))
    override def scalaVersion = v.scala
    override def moduleDeps = Seq(vector)
    override def ivyDeps = T(Seq(v.mainargs))
    def elaborate = T {
      // class path for `moduleDeps` is only a directory, not a jar, which breaks the cache.
      // so we need to manually add the class files of `moduleDeps` here.
      upstreamCompileOutput()
      mill.modules.Jvm.runLocal(
        finalMainClass(),
        runClasspath().map(_.path),
        Seq(
          "--dir", T.dest.toString,
          "--config", os.temp(designConfig()).toString,
          "--tb", isTestbench().toString
        )
      )
      PathRef(T.dest)
    }
    def chiselAnno = T(os.walk(elaborate().path).collectFirst { case p if p.last.endsWith("anno.json") => p }.map(PathRef(_)).get)
    def chirrtl = T(os.walk(elaborate().path).collectFirst { case p if p.last.endsWith("fir") => p }.map(PathRef(_)).get)
    def topName = T(chirrtl().path.last.split('.').head)
  }
  object mfccompile extends Module {
    def compile = T {
      os.proc(Seq("firtool", elaborate.chirrtl().path.toString, s"--annotation-file=${elaborate.chiselAnno().path}", s"-o=${T.dest}") ++ mfcArgs()).call(T.dest)
      PathRef(T.dest)
    }
    def rtls = T {
      os.read(compile().path / "filelist.f").split("\n").map(str =>
        try {
          os.Path(str)
        } catch {
          case e: IllegalArgumentException if e.getMessage.contains("is not an absolute path") =>
            compile().path / str.stripPrefix("./")
        }
      ).filter(p => p.ext == "v" || p.ext == "sv").map(PathRef(_)).toSeq
    }
    def memoryConfig = T(PathRef(compile().path / "metadata" / "seq_mems.json"))
  }
}

object release extends mill.Cross[Release](os.walk(os.pwd / "configs").filter(_.ext == "json").filter(_.baseName.contains("release")).map(_.baseName) :_*)
class Release(config: String) extends Module {
  def release = T {
    val target = T.dest / s"vector-${os.proc("git", "rev-parse", "--short=7", "HEAD").call().out.text().stripLineEnd}.tar.xz"
    os.proc(Seq("tar", "-czf", target.toString, "-C", elaborator(config).mfccompile.compile().path.toString) ++ (elaborator(config).mfccompile.rtls() :+ elaborator(config).mfccompile.memoryConfig()).map(_.path.relativeTo(elaborator(config).mfccompile.compile().path).toString)).call(T.dest)
    T.log.info(s"Release tarball created at $target")
    PathRef(target)
  }
}

object emulator extends mill.Cross[emulator](
  "v1024l8b2-test",
  "v1024l8b2-test-trace"
)

class emulator(config: String) extends Module {
  def configDir = T(os.pwd / "configs")

  def configFile = T(configDir() / s"$config.json")

  def trace = T(ujson.read(os.read(configFile()))("trace").bool)

  def csrcDir = T.source {
    PathRef(millSourcePath / os.up / "src")
  }

  def allCHeaderFiles = T.sources {
    os.walk(csrcDir().path).filter(_.ext == "h").map(PathRef(_))
  }

  def allCSourceFiles = T.sources {
    Seq(
      "spike_event.cc",
      "vbridge_impl.cc",
      "dpi.cc",
      "elf.cc",
      "rtl_config.cc",
    ).map(f => PathRef(csrcDir().path / f))
  }

  def verilatorArgs = T {
    Seq(
      // format: off
      "--x-initial unique",
      "--output-split 100000",
      "--max-num-width 1048576",
      "--main",
      "--timing",
      // use for coverage
      "--coverage-user",
      "--assert",
      // format: on
    )
  }

  def topName = T {
    "V"
  }

  def verilatorThreads = T {
    8
  }

  def CMakeListsString = T {
    // format: off
    s"""cmake_minimum_required(VERSION 3.20)
       |project(emulator)
       |set(CMAKE_CXX_STANDARD 17)
       |
       |find_package(args REQUIRED)
       |find_package(glog REQUIRED)
       |find_package(fmt REQUIRED)
       |find_package(libspike REQUIRED)
       |find_package(verilator REQUIRED)
       |find_package(jsoncpp REQUIRED)
       |find_package(Threads REQUIRED)
       |set(THREADS_PREFER_PTHREAD_FLAG ON)
       |
       |add_executable(emulator
       |${allCSourceFiles().map(_.path).mkString("\n")}
       |)
       |
       |target_include_directories(emulator PUBLIC ${csrcDir().path.toString})
       |
       |target_link_libraries(emulator PUBLIC $${CMAKE_THREAD_LIBS_INIT})
       |target_link_libraries(emulator PUBLIC libspike fmt::fmt glog::glog jsoncpp)  # note that libargs is header only, nothing to link
       |target_compile_definitions(emulator PRIVATE COSIM_VERILATOR)
       |
       |verilate(emulator
       |  SOURCES
       |  ${elaborator(config).mfccompile.rtls().map(_.path.toString).mkString("\n")}
       |  ${if (trace()) "TRACE_FST" else ""}
       |  TOP_MODULE ${elaborator(config).elaborate.topName()}
       |  PREFIX V${elaborator(config).elaborate.topName()}
       |  OPT_FAST
       |  THREADS ${verilatorThreads()}
       |  VERILATOR_ARGS ${verilatorArgs().mkString(" ")}
       |)
       |""".stripMargin
    // format: on
  }

  def cmakefileLists = T {
    val path = T.dest / "CMakeLists.txt"
    os.write.over(path, CMakeListsString())
    PathRef(T.dest)
  }

  def buildDir = T {
    PathRef(T.dest)
  }

  def cmake = T {
    mill.modules.Jvm.runSubprocess(Seq("cmake",
      "-G", "Ninja",
      "-S", cmakefileLists().path,
      "-B", buildDir().path
    ).map(_.toString), Map[String, String](), T.dest)
  }

  def elf = T {
    // either rtl or testbench change should trigger elf rebuild
    elaborator(config).mfccompile.rtls()
    allCSourceFiles()
    allCHeaderFiles()
    cmake()
    mill.modules.Jvm.runSubprocess(Seq("ninja", "-C", buildDir().path).map(_.toString), Map[String, String](), buildDir().path)
    PathRef(buildDir().path / "emulator")
  }
}

object tests extends Module {

  object cases extends Module {
    c =>
    trait Case extends Module {
      def name: String = millOuterCtx.segment.pathSegments.last

      def sources = T.sources {
        millSourcePath
      }

      def allSourceFiles = T {
        Lib.findSourceFiles(sources(), Seq("S", "s", "c", "cpp")).map(PathRef(_))
      }

      def includeDir = T {
        Seq[PathRef]()
      }

      def linkOpts = T {
        Seq("-mno-relax", "-fno-PIC")
      }

      def elf: T[PathRef] = T {
        os.proc(Seq("clang-rv32", "-o", name + ".elf", "-mabi=ilp32f", "-march=rv32gcv") ++ linkOpts() ++ includeDir().map(p => s"-I${p.path}") ++ allSourceFiles().map(_.path.toString)).call(T.ctx.dest)
        PathRef(T.ctx.dest / (name + ".elf"))
      }
    }

    object `riscv-vector-tests` extends Module {
      u =>
      override def millSourcePath = os.pwd / "dependencies" / "riscv-vector-tests"
      def allTests = os.walk(millSourcePath / "configs").filter(_.ext == "toml").filter{ p =>
        os.read(p).contains("Zve32x")
      }.map(_.last.replace(".toml", ""))

      def allGoSources = T.sources {
        os.walk(millSourcePath).filter(f => f.ext == "go" || f.last == "go.mod").map(PathRef(_))
      }

      def asmGenerator = T {
        // depends on GO
        allGoSources()
        val elf = T.dest / "generator"
        os.proc("go", "build", "-o", elf, "single/single.go").call(cwd = millSourcePath)
        PathRef(elf)
      }

      object ut extends mill.Cross[ut](allTests: _*)

      class ut(caseName: String) extends c.Case {
        override def name = caseName

        override def includeDir = T {
          Seq(
            u.millSourcePath / "env" / "sequencer-vector",
            u.millSourcePath / "macros" / "sequencer-vector"
          ).map(PathRef(_))
        }

        override def linkOpts = T {
          Seq("-mno-relax", "-static", "-mcmodel=medany", "-fvisibility=hidden", "-nostdlib", "-fno-PIC")
        }

        override def allSourceFiles: T[Seq[PathRef]] = T {
          val f = T.dest / s"${name.replace('_', '.')}.S"
          os.proc(
            asmGenerator().path,
            "-VLEN", 1024,
            "-XLEN", 32,
            "-outputfile", f,
            "-configfile", u.millSourcePath / "configs" / s"${name.replace('_', '.')}.toml"
          ).call(T.dest)
          Seq(PathRef(f))
        }
      }
    }

    class BuddyMLIRCase(mlirSourceName: String) extends Case {
      def mlirFile = T.source(PathRef(millSourcePath / mlirSourceName))

      override def millSourcePath = super.millSourcePath / os.up

      override def linkOpts = T {
        Seq("-mno-relax", "-static", "-mcmodel=medany", "-fvisibility=hidden", "-nostdlib", "-Wl,--entry=start", "-fno-PIC")
      }

      // Parse the header comment of the given file to get the command line arguments to pass to the buddy-opt.
      // These arguments must be wrapped in a block, starting with "BUDDY-OPT" and ending with "BUDDY-OPT-END".
      def parseBuddyOptArg(testFile: os.Path) = os.read
            .lines(testFile)
            .dropWhile(bound => bound.startsWith("//") && bound.contains("BUDDY-OPT"))
            .takeWhile(bound => bound.startsWith("//") && !bound.contains("BUDDY-OPT-END"))
            .map(lines => lines.stripPrefix("//").trim().split(" "))
            .flatten

      override def allSourceFiles: T[Seq[PathRef]] = T {
        val buddy = T.dest / s"${mlirSourceName}.buddy"
        val llvmir = T.dest / s"${mlirSourceName}.llvmir"
        val asm = T.dest / s"${mlirSourceName}.S"
        val buddyOptArg = parseBuddyOptArg(mlirFile().path)

        T.log.info(s"run buddy-opt with arg ${buddyOptArg}")
        os.proc(
          "buddy-opt",
          mlirFile().path,
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

    def mlirTests = os.walk(millSourcePath / "buddy").filter(_.ext == "mlir").map(_.last.toString)

    object buddy extends Cross[BuddyMLIRCase](mlirTests: _*) {
      def allTests = mlirTests
    }

    class AsmCase(asmSourceName: String) extends Case {
      def asmFile = T.source(PathRef(millSourcePath / asmSourceName))

      override def millSourcePath = super.millSourcePath / os.up

      override def linkOpts = T {
        Seq("-mno-relax", "-static", "-mcmodel=medany", "-fvisibility=hidden", "-nostdlib", "-Wl,--entry=start", "-fno-PIC")
      }

      override def allSourceFiles: T[Seq[PathRef]] = T {
        super.allSourceFiles() ++ Seq(PathRef(asmFile().path))
      }
    }

    def asmTests = os.walk(millSourcePath / "asm").filter(_.ext == "asm").map(_.last.toString)

    object asm extends Cross[AsmCase](asmTests: _*) {
      def allTests = asmTests
    }

    class IntrinsicCase(intrinsicSourceName: String) extends Case {
      def intrinsicFile = T.source(PathRef(millSourcePath / intrinsicSourceName))
      def mainAsmFile = T.source(PathRef(millSourcePath / "main.S"))

      override def millSourcePath = super.millSourcePath / os.up

      override def linkOpts = T {
        Seq("-mno-relax", "-static", "-mcmodel=medany", "-fvisibility=hidden", "-nostdlib", "-Wl,--entry=start", "-fno-PIC")
      }

      override def allSourceFiles: T[Seq[PathRef]] = T {
        Seq(PathRef(intrinsicFile().path), PathRef(mainAsmFile().path))
      }
    }

    def intrinsicTests = os.walk(millSourcePath / "intrinsic").filter(_.ext == "c").map(_.last.toString)

    object intrinsic extends Cross[IntrinsicCase](intrinsicTests: _*) {
      def allTests = intrinsicTests
    }
  }

  object run extends mill.Cross[run]((cases.`riscv-vector-tests`.allTests ++ cases.buddy.allTests ++ cases.asm.allTests ++ cases.intrinsic.allTests): _*)

  class run(name: String) extends Module with TaskModule {
    override def defaultCommandName() = "run"

    val mlirTestPattern = raw"(.+\.mlir)$$".r
    val asmTestPattern = raw"(.+\.asm)$$".r
    val intrinsicTestPattern = raw"(.+\.c)$$".r
    def caseToRun = name match {
      case mlirTestPattern(testName) => cases.buddy(testName)
      case asmTestPattern(testName) => cases.asm(testName)
      case intrinsicTestPattern(testName) => cases.intrinsic(testName)
      case _ => cases.`riscv-vector-tests`.ut(name)
    }

    def ciRun  = T {
      val runEnv = Map(
        "COSIM_bin" -> caseToRun.elf().path.toString,
        "COSIM_wave" -> (T.dest / "wave").toString,
        "COSIM_reset_vector" -> "1000",
        "COSIM_timeout" -> "1000000",
        "COSIM_config" -> emulator("v1024l8b2-test").configFile().toString,
        "GLOG_logtostderr" -> "0",
        "PERF_output_file" -> (T.dest / "perf.txt").toString,
      )
      T.log.info(s"run test: ${caseToRun.name} with:\n ${runEnv.map { case (k, v) => s"$k=$v" }.mkString(" ")} ${emulator("v1024l8b2-test").elf().path.toString}")
      os.proc(Seq(emulator("v1024l8b2-test").elf().path.toString)).call(env = runEnv, check = false).exitCode
    }

    def run(args: String*) = T.command {
      def envDefault(name: String, default: String) = {
        name -> args.map(_.split("=")).collectFirst {
          case arg if arg.head == name => arg.last
        }.getOrElse(default)
      }

      val runEnv = Map(
        "COSIM_bin" -> caseToRun.elf().path.toString,
        "COSIM_wave" -> (T.dest / "wave").toString,
        "COSIM_reset_vector" -> "1000",
        "COSIM_config" -> emulator("v1024l8b2-test-trace").configFile().toString,
        envDefault("COSIM_timeout", "1000000"),
        envDefault("GLOG_logtostderr", "1"),
        "PERF_output_file" -> (T.dest / "perf.txt").toString,
      )
      T.log.info(s"run test: ${caseToRun.name} with:\n ${runEnv.map { case (k, v) => s"$k=$v" }.mkString(" ")} ${emulator("v1024l8b2-test-trace").elf().path.toString}")
      os.proc(Seq(emulator("v1024l8b2-test-trace").elf().path.toString)).call(env = runEnv)
      PathRef(T.dest)
    }
  }
}
