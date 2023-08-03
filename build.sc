import mill._
import mill.scalalib._
import mill.define.{Command, TaskModule}
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
import $file.common
import $file.tests.build

object v {
  val scala = "2.13.6"
  val utest = ivy"com.lihaoyi::utest:latest.integration"
  val mainargs = ivy"com.lihaoyi::mainargs:0.3.0"
  // for arithmetic
  val bc = ivy"org.bouncycastle:bcprov-jdk15to18:latest.integration"
  val spire = ivy"org.typelevel::spire:latest.integration"
  val evilplot = ivy"io.github.cibotech::evilplot:latest.integration"
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

object myhardfloat extends common.HardfloatModule {
  override def millSourcePath = os.pwd / "dependencies" / "hardfloat"

  override def scalaVersion = v.scala

  def chisel3Module: Option[PublishModule] = Some(mychisel3)

  def chisel3PluginJar = T(Some(mychisel3.plugin.jar()))
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

  def tilelinkModule = Some(mytilelink)

  def hardfloatModule = Some(myhardfloat)

  def utest: T[Dep] = v.utest
}

object elaborator
    extends mill.Cross[Elaborator](os.walk(os.pwd / "configs").filter(_.ext == "json").map(_.baseName): _*)
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
    override def scalacOptions = T(
      super.scalacOptions() ++ Some(mychisel3.plugin.jar()).map(path => s"-Xplugin:${path.path}") ++ Seq(
        "-Ymacro-annotations"
      )
    )
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
          "--dir",
          T.dest.toString,
          "--config",
          os.temp(designConfig()).toString,
          "--tb",
          isTestbench().toString
        )
      )
      PathRef(T.dest)
    }
    def chiselAnno = T(
      os.walk(elaborate().path).collectFirst { case p if p.last.endsWith("anno.json") => p }.map(PathRef(_)).get
    )
    def chirrtl = T(
      os.walk(elaborate().path).collectFirst { case p if p.last.endsWith("fir") => p }.map(PathRef(_)).get
    )
    def topName = T(chirrtl().path.last.split('.').head)
  }
  object mfccompile extends Module {
    def compile = T {
      os.proc(
        Seq(
          "firtool",
          elaborate.chirrtl().path.toString,
          s"--annotation-file=${elaborate.chiselAnno().path}",
          s"-o=${T.dest}"
        ) ++ mfcArgs()
      ).call(T.dest)
      PathRef(T.dest)
    }
    def rtls = T {
      os.read(compile().path / "filelist.f")
        .split("\n")
        .map(str =>
          try {
            os.Path(str)
          } catch {
            case e: IllegalArgumentException if e.getMessage.contains("is not an absolute path") =>
              compile().path / str.stripPrefix("./")
          }
        )
        .filter(p => p.ext == "v" || p.ext == "sv")
        .map(PathRef(_))
        .toSeq
    }
    def memoryConfig = T(PathRef(compile().path / "metadata" / "seq_mems.json"))
  }
}

object release
    extends mill.Cross[Release](
      os.walk(os.pwd / "configs").filter(_.ext == "json").filter(_.baseName.contains("release")).map(_.baseName): _*
    )
class Release(config: String) extends Module {
  def release = T {
    val target =
      T.dest / s"vector-${os.proc("git", "rev-parse", "--short=7", "HEAD").call().out.text().stripLineEnd}.tar.xz"
    os.proc(
      Seq("tar", "-czf", target.toString, "-C", elaborator(config).mfccompile.compile().path.toString) ++ (elaborator(
        config
      ).mfccompile.rtls() :+ elaborator(config).mfccompile.memoryConfig())
        .map(_.path.relativeTo(elaborator(config).mfccompile.compile().path).toString)
    ).call(T.dest)
    T.log.info(s"Release tarball created at $target")
    PathRef(target)
  }
}

def emulatorTarget: Seq[String] = os.walk(os.pwd / "configs")
  .filter(_.ext == "json")
  .filter(_.baseName.contains("test"))
  .map(_.baseName)

object emulator extends mill.Cross[emulator](emulatorTarget: _*)

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
      "rtl_config.cc"
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
    mill.modules.Jvm.runSubprocess(
      Seq("cmake", "-G", "Ninja", "-S", cmakefileLists().path, "-B", buildDir().path).map(_.toString),
      Map[String, String](),
      T.dest
    )
  }

  def elf = T {
    // either rtl or testbench change should trigger elf rebuild
    elaborator(config).mfccompile.rtls()
    allCSourceFiles()
    allCHeaderFiles()
    cmake()
    mill.modules.Jvm.runSubprocess(
      Seq("ninja", "-C", buildDir().path).map(_.toString),
      Map[String, String](),
      buildDir().path
    )
    PathRef(buildDir().path / "emulator")
  }
}

def testsOutDir = os.Path(sys.env("TEST_CASE_DIR"))
def testConfigs = os.walk(testsOutDir / "configs").filter(_.ext == "json").map(_.baseName)
def runtimeConfigs = os.walk(os.pwd / "run").filter(_.ext == "json")

// Generate a cross product from elaborator config, test config, runtime config
def crossGenConfigProduct: Seq[(String, String, String)] =
  emulatorTarget.flatMap(emuTarget =>
      testConfigs.flatMap(testTarget =>
          runtimeConfigs.map(runCfg =>
              (emuTarget, testTarget, runCfg.baseName))))

object verilatorEmulator extends mill.Cross[RunVerilatorEmulator]((crossGenConfigProduct): _*)

class RunVerilatorEmulator(elaboratorConfig: String, testTask: String, config: String) extends Module with TaskModule {
  override def defaultCommandName() = "run"

  def configDir:  T[os.Path] = T(os.pwd / "run")
  def configFile: T[os.Path] = T(configDir() / s"$config.json")
  def runConfig:  T[ujson.Value.Value] = T(ujson.read(os.read(configFile())))
  def testConfig: T[ujson.Value.Value] = T(ujson.read(os.read(testsOutDir / "configs" / s"$testTask.json")))
  def binPath: T[os.Path] = T(testsOutDir / os.RelPath(testConfig().obj("elf").obj("path").str))

  def run = T {
    def wave: String = runConfig().obj.get("wave").map(_.str).getOrElse((T.dest / "wave").toString)
    def resetVector = runConfig().obj("reset_vector").num.toInt
    def timeout = runConfig().obj("timeout").num.toInt
    def logtostderr = if (runConfig().obj("logtostderr").bool) "1" else "0"
    def perfFile = runConfig().obj.get("perf_file").map(_.str).getOrElse((T.dest / "perf.txt").toString)
    def runEnv = Map(
      "COSIM_bin" -> binPath().toString,
      "COSIM_wave" -> wave,
      "COSIM_reset_vector" -> resetVector.toString,
      "COSIM_timeout" -> timeout.toString,
      // TODO: really need elaboratorConfig?
      "COSIM_config" -> emulator(elaboratorConfig).configFile().toString,
      "GLOG_logtostderr" -> logtostderr,
      "PERF_output_file" -> perfFile
    )
    os.proc(Seq(emulator(elaboratorConfig).elf().path.toString)).call(env = runEnv, check = true)
  }
}
