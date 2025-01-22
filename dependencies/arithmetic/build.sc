import mill._
import mill.scalalib._
import mill.scalalib.scalafmt._
import $file.common
import os._

object v {
  val scala = "2.13.10"
  val spire = ivy"org.typelevel::spire:0.18.0"
  val evilplot = ivy"io.github.cibotech::evilplot:0.9.0"
  val oslib =  ivy"com.lihaoyi::os-lib:0.9.1"
  val mainargs = ivy"com.lihaoyi::mainargs:0.5.0"
  val chiselCrossVersions = Map(
    "5.0.0" -> (ivy"org.chipsalliance::chisel:5.0.0", ivy"org.chipsalliance:::chisel-plugin:5.0.0"),
  )
}

object arithmetic extends Cross[Arithmetic](v.chiselCrossVersions.keys.toSeq)

object elaborator
  extends mill.Cross[Elaborator](v.chiselCrossVersions.keys.toSeq)

object emulator
  extends mill.Cross[Emulator](v.chiselCrossVersions.keys.toSeq)

object test
  extends mill.Cross[Test](v.chiselCrossVersions.keys.toSeq)


trait Arithmetic
  extends common.ArithmeticModule
    with ScalafmtModule
    with Cross.Module[String] {

  override def scalaVersion = T(v.scala)

  override def millSourcePath = os.pwd / "arithmetic"

  def spireIvy = v.spire

  def evilplotIvy = v.evilplot

  def chiselModule = None

  def chiselPluginJar = None

  def chiselIvy = Some(v.chiselCrossVersions(crossValue)._1)

  def chiselPluginIvy = Some(v.chiselCrossVersions(crossValue)._2)
}

trait Elaborator
  extends Cross.Module[String] {

  object elaborate extends ScalaModule with ScalafmtModule{
    override def millSourcePath: os.Path = os.pwd / "elaborator"

    override def scalaVersion = v.scala

    override def scalacOptions = T(super.scalacOptions())

    def chiselIvy = Some(v.chiselCrossVersions(crossValue)._1)

    def chiselPluginIvy = Some(v.chiselCrossVersions(crossValue)._2)

    override def scalacPluginIvyDeps: T[Agg[Dep]] = T(super.scalacPluginIvyDeps() ++ chiselPluginIvy.map(Agg(_)).getOrElse(Agg.empty[Dep]))

    def arithmeticModule = arithmetic(crossValue)

    override def moduleDeps = Seq(arithmeticModule)

    override def ivyDeps = T(Seq(v.mainargs,v.oslib))

    def elaborate = T {
      upstreamCompileOutput()
      mill.util.Jvm.runLocal(
        finalMainClass(),
        runClasspath().map(_.path),
        Seq(
          "--dir",
          T.dest.toString,
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
  }

  object mfccompile extends Module {
    def compile = T {
      os.proc(
        Seq(
          "firtool",
          elaborate.chirrtl().path.toString,
          s"--annotation-file=${elaborate.chiselAnno().path}",
          s"-o=${T.dest}",
          "-dedup",
          "-O=debug",
          "--split-verilog",
          "--preserve-values=named"
        )
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
  }
}

trait Emulator
  extends Cross.Module[String] {
  val config: String = crossValue

  val topName = "TestBench"

  def csrcDir = T.source {
    PathRef(millSourcePath / "src")
  }

  def allCSourceFiles = T.sources {
    Seq(
      "dpi.cc",
      "testharness.cc",
      "testharness.h",
      "util.h"
    ).map(f => PathRef(csrcDir().path / f))
  }

  def verilatorArgs = T {
    Seq(
      "--timing",
      "--main"
    )
  }

  val testfloatLibDir = sys.env("TEST_FLOAT_LIB_DIR")
  val softfloatLibDir = sys.env("SOFT_FLOAT_LIB_DIR")


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
       |find_package(verilator REQUIRED)
       |find_package(Threads REQUIRED)
       |set(THREADS_PREFER_PTHREAD_FLAG ON)
       |
       |add_executable(emulator
       |${allCSourceFiles().map(_.path).mkString("\n")}
       |)
       |
       |target_include_directories(emulator PUBLIC
       |${testfloatLibDir}/dist/source
       |${softfloatLibDir}/include
       |)
       |
       |target_link_libraries(emulator PUBLIC $${CMAKE_THREAD_LIBS_INIT})
       |target_link_libraries(emulator PUBLIC
       |fmt::fmt
       |glog::glog
       |${testfloatLibDir}/lib/testfloat.a
       |${softfloatLibDir}/lib/softfloat.a
       |)  # note that libargs is header only, nothing to link
       |target_compile_definitions(emulator PRIVATE COSIM_VERILATOR)
       |
       |verilate(emulator
       |  SOURCES
       |  ${elaborator(config).mfccompile.rtls().map(_.path.toString).mkString("\n")}
       |  "TRACE_FST"
       |  TOP_MODULE $topName
       |  PREFIX V$topName
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
    mill.util.Jvm.runSubprocess(
      Seq("cmake", "-G", "Ninja", "-S", cmakefileLists().path, "-B", buildDir().path, "-DCMAKE_EXPORT_COMPILE_COMMANDS=1").map(_.toString),
      Map[String, String](),
      T.dest
    )
  }

  def elf = T {
    // either rtl or testbench change should trigger elf rebuild
    elaborator(config).mfccompile.rtls()
    allCSourceFiles()
    cmake()
    mill.util.Jvm.runSubprocess(
      Seq("ninja", "-C", buildDir().path).map(_.toString),
      Map[String, String](),
      buildDir().path
    )
    PathRef(buildDir().path / "emulator")
  }
}

trait Test
  extends Cross.Module[String] {
  def test = T {
    def testOp(op: String): Unit ={
      for (roundingMode <- 0 to 4) {
        val rmMaps = Map(
          0 -> "RNE",
          1 -> "RTZ",
          2 -> "RDN",
          3 -> "RUP",
          4 -> "RMM"
        )
        val runEnv = Map(
          "wave" -> s"${T.dest}/",
          "op" -> s"$op",
          "rm" -> s"$roundingMode"
        )
        os.proc(emulator(crossValue).elf().path).call(stdout = T.dest / s"${op}_${rmMaps(roundingMode)}.log", cwd = T.dest, env = runEnv)
      }
    }
    testOp("div")
    testOp("sqrt")
  }
}
