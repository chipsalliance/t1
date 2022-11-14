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
import $file.common

object v {
  val scala = "2.13.6"
  val utest = ivy"com.lihaoyi::utest:latest.integration"
  val mainargs = ivy"com.lihaoyi::mainargs:0.3.0"
  // for arithmetic
  val upickle = ivy"com.lihaoyi::upickle:latest.integration"
  val osLib = ivy"com.lihaoyi::os-lib:latest.integration"
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

  def upickle: T[Dep] = v.upickle

  def osLib: T[Dep] = v.osLib

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

  def utest: T[Dep] = v.utest
}

object tests extends Module {
  object elaborate extends ScalaModule {
    override def scalacPluginClasspath = T {
      Agg(mychisel3.plugin.jar())
    }

    override def scalacOptions = T {
      super.scalacOptions() ++ Some(mychisel3.plugin.jar()).map(path => s"-Xplugin:${path.path}") ++ Seq("-Ymacro-annotations")
    }

    override def scalaVersion = v.scala

    override def moduleDeps = Seq(vector)

    override def ivyDeps = T {
      Seq(
        v.mainargs
      )
    }

    def elaborate = T {
      mill.modules.Jvm.runSubprocess(
        finalMainClass(),
        runClasspath().map(_.path),
        forkArgs(),
        forkEnv(),
        Seq(
          "--dir", T.dest.toString,
        ),
        workingDir = forkWorkingDir()
      )
      PathRef(T.dest)
    }

    def rtls = T {
      os.read(elaborate().path / "filelist.f").split("\n").map(str =>
        try {
          os.Path(str)
        } catch {
          case e: IllegalArgumentException if e.getMessage.contains("is not an absolute path") =>
            elaborate().path / str
        }
      ).filter(p => p.ext == "v" || p.ext == "sv").map(PathRef(_)).toSeq
    }

    def annotations = T {
      os.walk(elaborate().path).filter(p => p.last.endsWith("anno.json")).map(PathRef(_))
    }
  }

  object emulator extends Module {

    object spike extends Module {
      override def millSourcePath = os.pwd / "dependencies" / "riscv-isa-sim"

      // ask make to cache file.
      def compile = T.persistent {
        os.proc(millSourcePath / "configure", "--prefix", "/usr", "--without-boost", "--without-boost-asio", "--without-boost-regex", "--enable-commitlog").call(
          T.ctx.dest, Map(
            "CC" -> "clang",
            "CXX" -> "clang++",
            "AR" -> "llvm-ar",
            "RANLIB" -> "llvm-ranlib",
            "LD" -> "lld",
          )
        )
        os.proc("make", "-j", Runtime.getRuntime().availableProcessors()).call(T.ctx.dest)
        PathRef(T.ctx.dest)
      }
    }

    def csrcDir = T.source {
      PathRef(millSourcePath / "src")
    }

    def allCSourceFiles = T.sources {
      Seq(
        "main.cc",
        "vbridge.cc",
        "rtl_event.cc",
        "spike_event.cc",
        "vbridge_impl.cc",
      ).map(f => PathRef(csrcDir().path / f))
    }

    def verilatorConfig = T {
      val traceConfigPath = T.dest / "verilator.vlt"
      os.write(
        traceConfigPath,
        "`verilator_config\n" +
          ujson.read(elaborate.annotations().collectFirst(f => os.read(f.path)).get).arr.flatMap {
            case anno if anno("class").str == "chisel3.experimental.Trace$TraceAnnotation" =>
              Some(anno("target").str)
            case _ => None
          }.toSet.map { t: String =>
            val s = t.split('|').last.split("/").last
            val M = s.split(">").head.split(":").last
            val S = s.split(">").last
            s"""//$t\npublic_flat_rd -module "$M" -var "$S""""
          }.mkString("\n")
      )
      PathRef(traceConfigPath)
    }

    def verilatorArgs = T {
      Seq(
        // format: off
        "--x-initial unique",
        "--output-split 100000",
        "--max-num-width 1048576",
        "--vpi"
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
         |find_package(args REQUIRED)
         |find_package(glog REQUIRED)
         |find_package(fmt REQUIRED)
         |
         |find_package(verilator)
         |set(CMAKE_CXX_STANDARD 17)
         |set(CMAKE_CXX_COMPILER_ID "clang")
         |set(CMAKE_C_COMPILER "clang")
         |set(CMAKE_CXX_COMPILER "clang++")
         |
         |find_package(Threads)
         |set(THREADS_PREFER_PTHREAD_FLAG ON)
         |add_executable(emulator
         |${allCSourceFiles().map(_.path).mkString("\n")}
         |)
         |target_include_directories(emulator PRIVATE ${(spike.millSourcePath / "riscv").toString})
         |target_include_directories(emulator PRIVATE ${(spike.millSourcePath / "fesvr").toString})
         |target_include_directories(emulator PRIVATE ${(spike.millSourcePath / "softfloat").toString})
         |target_include_directories(emulator PRIVATE ${spike.compile().path.toString})
         |
         |target_include_directories(emulator PUBLIC ${csrcDir().path.toString})
         |
         |target_link_directories(emulator PRIVATE ${spike.compile().path.toString})
         |target_link_libraries(emulator PUBLIC $${CMAKE_THREAD_LIBS_INIT})
         |target_link_libraries(emulator PUBLIC riscv fmt glog)  # note that libargs is header only, nothing to link
         |
         |verilate(emulator
         |  SOURCES
         |  ${elaborate.rtls().map(_.path.toString).mkString("\n")}
         |  ${verilatorConfig().path.toString}
         |  TRACE_FST
         |  TOP_MODULE ${topName()}
         |  PREFIX V${topName()}
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

    def config = T {
      mill.modules.Jvm.runSubprocess(Seq("cmake", "-G", "Ninja", "-S", cmakefileLists().path, "-B", buildDir().path).map(_.toString), Map[String, String](), T.dest)
    }

    def elf = T {
      // either rtl or testbench change should trigger elf rebuild
      elaborate.rtls()
      allCSourceFiles()
      config()
      mill.modules.Jvm.runSubprocess(Seq("ninja", "-C", buildDir().path).map(_.toString), Map[String, String](), T.dest)
      PathRef(T.dest / "emulator")
    }
  }

  object cases extends Module {
    object compilerrt extends Module {
      override def millSourcePath = os.pwd / "dependencies" / "llvm-project" / "compiler-rt"

      // ask make to cache file.
      def compile = T.persistent {
        os.proc("cmake", "-S", millSourcePath,
          "-DCOMPILER_RT_BUILD_LIBFUZZER=OFF",
          "-DCOMPILER_RT_BUILD_SANITIZERS=OFF",
          "-DCOMPILER_RT_BUILD_PROFILE=OFF",
          "-DCOMPILER_RT_BUILD_MEMPROF=OFF",
          "-DCOMPILER_RT_BUILD_ORC=OFF",
          "-DCOMPILER_RT_BUILD_BUILTINS=ON",
          "-DCOMPILER_RT_BAREMETAL_BUILD=ON",
          "-DCOMPILER_RT_INCLUDE_TESTS=OFF",
          "-DCOMPILER_RT_HAS_FPIC_FLAG=OFF",
          "-DCOMPILER_RT_DEFAULT_TARGET_ONLY=On",
          "-DCOMPILER_RT_OS_DIR=riscv32",
          "-DCMAKE_BUILD_TYPE=Release",
          "-DCMAKE_SYSTEM_NAME=Generic",
          "-DCMAKE_SYSTEM_PROCESSOR=riscv32",
          "-DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY",
          "-DCMAKE_SIZEOF_VOID_P=8",
          "-DCMAKE_ASM_COMPILER_TARGET=riscv32-none-elf",
          "-DCMAKE_C_COMPILER_TARGET=riscv32-none-elf",
          "-DCMAKE_C_COMPILER_WORKS=ON",
          "-DCMAKE_CXX_COMPILER_WORKS=ON",
          "-DCMAKE_C_COMPILER=clang",
          "-DCMAKE_CXX_COMPILER=clang++",
          "-DCMAKE_C_FLAGS=-nodefaultlibs -fno-exceptions -mno-relax -Wno-macro-redefined -fPIC",
          "-DCMAKE_INSTALL_PREFIX=/usr",
          "-Wno-dev",
        ).call(T.ctx.dest)
        os.proc("make", "-j", Runtime.getRuntime().availableProcessors()).call(T.ctx.dest)
        PathRef(T.ctx.dest)
      }
    }

    object musl extends Module {
      override def millSourcePath = os.pwd / "dependencies" / "musl"

      // ask make to cache file.
      def libraryResources = T.persistent {
        os.proc("make", s"DESTDIR=${T.ctx.dest}", "install").call(compilerrt.compile().path)
        PathRef(T.ctx.dest)
      }

      def compile = T.persistent {
        val p = libraryResources().path
        os.proc(millSourcePath / "configure", "--target=riscv32-none-elf", "--prefix=/usr").call(
          T.ctx.dest,
          Map(
            "CC" -> "clang",
            "CXX" -> "clang++",
            "AR" -> "llvm-ar",
            "RANLIB" -> "llvm-ranlib",
            "LD" -> "lld",
            "LIBCC" -> "-lclang_rt.builtins-riscv32",
            "CFLAGS" -> "--target=riscv32 -mno-relax -nostdinc",
            "LDFLAGS" -> s"-fuse-ld=lld --target=riscv32 -nostdlib -L${p}/usr/lib/riscv32",
          )
        )
        os.proc("make", "-j", Runtime.getRuntime().availableProcessors()).call(T.ctx.dest)
        PathRef(T.ctx.dest)
      }
    }

    trait Case extends Module {
      def name: T[String] = millSourcePath.last

      def sources = T.sources {
        millSourcePath
      }

      def allSourceFiles = T {
        Lib.findSourceFiles(sources(), Seq("S", "s", "c", "cpp")).map(PathRef(_))
      }

      def linkScript: T[PathRef] = T {
        os.write(T.ctx.dest / "linker.ld",
          s"""
             |SECTIONS
             |{
             |  . = 0x1000;
             |  .text.start : { *(.text.start) }
             |}
             |""".stripMargin)
        PathRef(T.ctx.dest / "linker.ld")
      }

      def compile: T[PathRef] = T {
        os.proc(Seq("clang", "-o", name() + ".elf", "--target=riscv32", "-march=rv32gcv", s"-L${musl.compile().path}/lib", s"-L${compilerrt.compile().path}/lib/riscv32", "-mno-relax", s"-T${linkScript().path}") ++ allSourceFiles().map(_.path.toString)).call(T.ctx.dest)
        os.proc(Seq("llvm-objcopy", "-O", "binary", "--only-section=.text", name() + ".elf", name())).call(T.ctx.dest)
        PathRef(T.ctx.dest / name())
      }
    }

    object smoketest extends Case
  }

  trait Case extends TaskModule {
    override def defaultCommandName() = "run"

    def bin: cases.Case

    def run(args: String*) = T.command {
      val proc = os.proc(Seq(tests.emulator.elf().path.toString, "--bin", bin.compile().path.toString, "--wave", (T.dest / "wave").toString) ++ args)
      T.log.info(s"run test: ${bin.name} with:\n ${proc.command.map(_.value.mkString(" ")).mkString(" ")}")
      proc.call()
      PathRef(T.dest)
    }
  }

  object smoketest extends Case {
    def bin = cases.smoketest
  }
}
