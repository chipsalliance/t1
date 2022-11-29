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
      // class path for `moduleDeps` is only a directory, not a jar, which breaks the cache.
      // so we need to manually add the class files of `moduleDeps` here.
      upstreamCompileOutput()
      mill.modules.Jvm.runLocal(
        finalMainClass(),
        runClasspath().map(_.path),
        Seq(
          "--dir", T.dest.toString,
        ),
      )
      PathRef(T.dest)
    }

    def chiselAnno = T {
      os.walk(elaborate().path).collectFirst { case p if p.last.endsWith("anno.json") => p }.map(PathRef(_)).get
    }

    def chirrtl = T {
      os.walk(elaborate().path).collectFirst { case p if p.last.endsWith("fir") => p }.map(PathRef(_)).get
    }

    def topName = T {
      chirrtl().path.last.split('.').head
    }

  }

  object mfccompile extends Module {

    def compile = T {
      os.proc("firtool",
        elaborate.chirrtl().path,
        s"--annotation-file=${elaborate.chiselAnno().path}",
        "-disable-infer-rw",
        "-dedup",
        "-O=debug",
        "--split-verilog",
        "--preserve-values=named",
        "--output-annotation-file=mfc.anno.json",
        s"-o=${T.dest}"
      ).call(T.dest)
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

    def annotations = T {
      os.walk(compile().path).filter(p => p.last.endsWith("mfc.anno.json")).map(PathRef(_))
    }
  }

  object emulator extends Module {

    def csrcDir = T.source {
      PathRef(millSourcePath / "src")
    }

    def allCHeaderFiles = T.sources {
      os.walk(csrcDir().path).filter(_.ext == "h").map(PathRef(_))
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
          ujson.read(mfccompile.annotations().collectFirst(f => os.read(f.path)).get).arr.flatMap {
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
         |set(CMAKE_CXX_STANDARD 17)
         |
         |find_package(args REQUIRED)
         |find_package(glog REQUIRED)
         |find_package(fmt REQUIRED)
         |find_package(libspike REQUIRED)
         |find_package(verilator REQUIRED)
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
         |target_link_libraries(emulator PUBLIC libspike fmt glog)  # note that libargs is header only, nothing to link
         |
         |verilate(emulator
         |  SOURCES
         |  ${mfccompile.rtls().map(_.path.toString).mkString("\n")}
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
      mfccompile.rtls()
      allCSourceFiles()
      allCHeaderFiles()
      config()
      mill.modules.Jvm.runSubprocess(Seq("ninja", "-C", buildDir().path).map(_.toString), Map[String, String](), buildDir().path)
      PathRef(buildDir().path / "emulator")
    }
  }

  object cases extends Module { c =>
    object unittest extends Module { u =>
      override def millSourcePath = os.pwd / "dependencies" / "riscv-vector-tests"

      def allGoSources = T.sources {
        os.walk(millSourcePath).filter(f => f.ext == "go" || f.last == "go.mod").map(PathRef(_))
      }

      def asmGenerator = T {
        // depends on GO
        allGoSources()
        val elf = T.dest / "generator"
        os.proc("go", "build", "-o", elf, "single/single.go").call(cwd = millSourcePath, env = Map("GOCACHE" -> s"${T.dest / "cache"}"))
        PathRef(elf)
      }

      trait Case extends c.Case {
        override def includeDir = T {
          Seq(
            u.millSourcePath / "env" / "sequencer-vector",
            u.millSourcePath / "macros" / "sequencer-vector"
          ).map(PathRef(_))
        }

        override def linkOpts = T {
          Seq("-mno-relax", "-static", "-mcmodel=medany", "-fvisibility=hidden", "-nostdlib")
        }

        override def linkScript: T[PathRef] = T.source {
          PathRef(u.millSourcePath / "env" / "sequencer-vector" / "link.ld")
        }

        override def allSourceFiles: T[Seq[PathRef]] = T {
          val f = T.dest / s"${name().replace('_', '.')}.S"
          os.proc(
            asmGenerator().path,
            "-VLEN", 1024,
            "-XLEN", 32,
            "-outputfile", f,
            "-configfile",  u.millSourcePath / "configs" / s"${name().replace('_', '.')}.toml"
          ).call(T.dest)
          Seq(PathRef(f))
        }

        override def bin: T[PathRef] = T {
          os.proc(Seq("llvm-objcopy", "-O", "binary", elf().path.toString, name())).call(T.ctx.dest)
          PathRef(T.ctx.dest / name())
        }
      }
   }

    trait Case extends Module {
      def name: T[String] = millOuterCtx.segment.pathSegments.last

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
        Seq("-mno-relax")
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

      def elf: T[PathRef] = T {
        os.proc(Seq("clang-rv32", "-o", name() + ".elf", "-mabi=ilp32f", "-march=rv32gcv", s"-T${linkScript().path}") ++ linkOpts() ++ includeDir().map(p => s"-I${p.path}") ++ allSourceFiles().map(_.path.toString)).call(T.ctx.dest)
        PathRef(T.ctx.dest / (name() + ".elf"))
      }

      def bin: T[PathRef] = T {
        os.proc(Seq("llvm-objcopy", "-O", "binary", "--only-section=.text", elf().path.toString, name())).call(T.ctx.dest)
        PathRef(T.ctx.dest / name())
      }
    }

    object smoketest extends Case

    object mmm extends Case

    object vaadd_vv extends unittest.Case
    object vaadd_vx extends unittest.Case
    object vaaddu_vv extends unittest.Case
    object vaaddu_vx extends unittest.Case
    object vadc_vim extends unittest.Case
    object vadc_vvm extends unittest.Case
    object vadc_vxm extends unittest.Case
    object vadd_vi extends unittest.Case
    object vadd_vv extends unittest.Case
    object vadd_vx extends unittest.Case
    object vand_vi extends unittest.Case
    object vand_vv extends unittest.Case
    object vand_vx extends unittest.Case
    object vasub_vv extends unittest.Case
    object vasub_vx extends unittest.Case
    object vasubu_vv extends unittest.Case
    object vasubu_vx extends unittest.Case
    object vcompress_vm extends unittest.Case
    object vcpop_m extends unittest.Case
    object vdiv_vv extends unittest.Case
    object vdiv_vx extends unittest.Case
    object vdivu_vv extends unittest.Case
    object vdivu_vx extends unittest.Case
    object vid_v extends unittest.Case
    object viota_m extends unittest.Case
    object vle16_v extends unittest.Case
    object vle32_v extends unittest.Case
    object vle64_v extends unittest.Case
    object vle8_v extends unittest.Case
    object vlm_v extends unittest.Case
    object vlse16_v extends unittest.Case
    object vlse32_v extends unittest.Case
    object vlse64_v extends unittest.Case
    object vlse8_v extends unittest.Case
    object vmacc_vv extends unittest.Case
    object vmacc_vx extends unittest.Case
    object vmadc_vim extends unittest.Case
    object vmadc_vvm extends unittest.Case
    object vmadd_vv extends unittest.Case
    object vmadd_vx extends unittest.Case
    object vmand_mm extends unittest.Case
    object vmandn_mm extends unittest.Case
    object vmax_vv extends unittest.Case
    object vmax_vx extends unittest.Case
    object vmaxu_vv extends unittest.Case
    object vmaxu_vx extends unittest.Case
    object vmerge_vim extends unittest.Case
    object vmerge_vvm extends unittest.Case
    object vmin_vv extends unittest.Case
    object vmin_vx extends unittest.Case
    object vminu_vv extends unittest.Case
    object vminu_vx extends unittest.Case
    object vmnand_mm extends unittest.Case
    object vmnor_mm extends unittest.Case
    object vmor_mm extends unittest.Case
    object vmorn_mm extends unittest.Case
    object vmsbc_vvm extends unittest.Case
    object vmsbf_m extends unittest.Case
    object vmseq_vv extends unittest.Case
    object vmseq_vx extends unittest.Case
    object vmsgt_vv extends unittest.Case
    object vmsgt_vx extends unittest.Case
    object vmsgtu_vv extends unittest.Case
    object vmsgtu_vx extends unittest.Case
    object vmsif_m extends unittest.Case
    object vmsle_vv extends unittest.Case
    object vmsle_vx extends unittest.Case
    object vmsleu_vv extends unittest.Case
    object vmsleu_vx extends unittest.Case
    object vmslt_vv extends unittest.Case
    object vmslt_vx extends unittest.Case
    object vmsltu_vv extends unittest.Case
    object vmsltu_vx extends unittest.Case
    object vmsne_vv extends unittest.Case
    object vmsne_vx extends unittest.Case
    object vmsof_m extends unittest.Case
    object vmul_vv extends unittest.Case
    object vmul_vx extends unittest.Case
    object vmulh_vv extends unittest.Case
    object vmulh_vx extends unittest.Case
    object vmulhsu_vv extends unittest.Case
    object vmulhsu_vx extends unittest.Case
    object vmulhu_vv extends unittest.Case
    object vmulhu_vx extends unittest.Case
    object vmv_s_x extends unittest.Case
    object vmv_v_i extends unittest.Case
    object vmv_v_v extends unittest.Case
    object vmv_v_x extends unittest.Case
    object vmv_x_s extends unittest.Case
    object vmv1r_v extends unittest.Case
    object vmv2r_v extends unittest.Case
    object vmv4r_v extends unittest.Case
    object vmv8r_v extends unittest.Case
    object vmxnor_mm extends unittest.Case
    object vmxor_mm extends unittest.Case
    object vnclip_wv extends unittest.Case
    object vnclipu_wv extends unittest.Case
    object vnclipu_wx extends unittest.Case
    object vnmsac_vv extends unittest.Case
    object vnmsac_vx extends unittest.Case
    object vnmsub_vv extends unittest.Case
    object vnmsub_vx extends unittest.Case
    object vnsra_wv extends unittest.Case
    object vnsra_wx extends unittest.Case
    object vnsrl_wv extends unittest.Case
    object vnsrl_wx extends unittest.Case
    object vor_vi extends unittest.Case
    object vor_vv extends unittest.Case
    object vor_vx extends unittest.Case
    object vredand_vs extends unittest.Case
    object vredmax_vs extends unittest.Case
    object vredmaxu_vs extends unittest.Case
    object vredmin_vs extends unittest.Case
    object vredminu_vs extends unittest.Case
    object vredor_vs extends unittest.Case
    object vredsum_vs extends unittest.Case
    object vredxor_vs extends unittest.Case
    object vrem_vv extends unittest.Case
    object vrem_vx extends unittest.Case
    object vremu_vv extends unittest.Case
    object vremu_vx extends unittest.Case
    object vrgather_vv extends unittest.Case
    object vrgather_vx extends unittest.Case
    object vrsub_vi extends unittest.Case
    object vrsub_vx extends unittest.Case
    object vsadd_vv extends unittest.Case
    object vsadd_vx extends unittest.Case
    object vsaddu_vv extends unittest.Case
    object vsaddu_vx extends unittest.Case
    object vsbc_vvm extends unittest.Case
    object vsbc_vxm extends unittest.Case
    object vse16_v extends unittest.Case
    object vse32_v extends unittest.Case
    object vse64_v extends unittest.Case
    object vse8_v extends unittest.Case
    object vsext_vf2 extends unittest.Case
    object vsext_vf4 extends unittest.Case
    object vsext_vf8 extends unittest.Case
    object vslide1down_vx extends unittest.Case
    object vslide1up_vx extends unittest.Case
    object vslidedown_vx extends unittest.Case
    object vslideup_vx extends unittest.Case
    object vsll_vi extends unittest.Case
    object vsll_vv extends unittest.Case
    object vsll_vx extends unittest.Case
    object vsm_v extends unittest.Case
    object vsmul_vv extends unittest.Case
    object vsmul_vx extends unittest.Case
    object vsra_vi extends unittest.Case
    object vsra_vv extends unittest.Case
    object vsra_vx extends unittest.Case
    object vsrl_vi extends unittest.Case
    object vsrl_vv extends unittest.Case
    object vsrl_vx extends unittest.Case
    object vsse16_v extends unittest.Case
    object vsse32_v extends unittest.Case
    object vsse64_v extends unittest.Case
    object vsse8_v extends unittest.Case
    object vssra_vv extends unittest.Case
    object vssra_vx extends unittest.Case
    object vssrl_vv extends unittest.Case
    object vssrl_vx extends unittest.Case
    object vssub_vv extends unittest.Case
    object vssub_vx extends unittest.Case
    object vssubu_vv extends unittest.Case
    object vssubu_vx extends unittest.Case
    object vsub_vv extends unittest.Case
    object vsub_vx extends unittest.Case
    object vwadd_vv extends unittest.Case
    object vwadd_vx extends unittest.Case
    object vwadd_wv extends unittest.Case
    object vwadd_wx extends unittest.Case
    object vwaddu_vv extends unittest.Case
    object vwaddu_vx extends unittest.Case
    object vwaddu_wv extends unittest.Case
    object vwaddu_wx extends unittest.Case
    object vwmacc_vv extends unittest.Case
    object vwmacc_vx extends unittest.Case
    object vwmaccsu_vv extends unittest.Case
    object vwmaccsu_vx extends unittest.Case
    object vwmaccu_vv extends unittest.Case
    object vwmaccu_vx extends unittest.Case
    object vwmaccus_vx extends unittest.Case
    object vwmul_vv extends unittest.Case
    object vwmul_vx extends unittest.Case
    object vwmulsu_vv extends unittest.Case
    object vwmulsu_vx extends unittest.Case
    object vwmulu_vv extends unittest.Case
    object vwmulu_vx extends unittest.Case
    object vwredsum_vs extends unittest.Case
    object vwredsumu_vs extends unittest.Case
    object vwsub_vv extends unittest.Case
    object vwsub_vx extends unittest.Case
    object vwsub_wv extends unittest.Case
    object vwsub_wx extends unittest.Case
    object vwsubu_vv extends unittest.Case
    object vwsubu_vx extends unittest.Case
    object vwsubu_wv extends unittest.Case
    object vwsubu_wx extends unittest.Case
    object vxor_vi extends unittest.Case
    object vxor_vv extends unittest.Case
    object vxor_vx extends unittest.Case
    object vzext_vf2 extends unittest.Case
    object vzext_vf4 extends unittest.Case
    object vzext_vf8 extends unittest.Case
  }

  trait Case extends TaskModule {
    override def defaultCommandName() = "run"

    def bin: cases.Case

    def run(args: String*) = T.command {
      val proc = os.proc(Seq(tests.emulator.elf().path.toString, "--bin", bin.bin().path.toString, "--wave", (T.dest / "wave").toString) ++ args)
      T.log.info(s"run test: ${bin.name} with:\n ${proc.command.map(_.value.mkString(" ")).mkString(" ")}")
      proc.call()
      PathRef(T.dest)
    }
  }

  object smoketest extends Case {
    def bin = cases.smoketest
  }

  object mmm extends Case {
    def bin = cases.mmm
  }

  object vaadd_vv extends Case {
    def bin = cases.vaadd_vv
  }

  object vaadd_vx extends Case {
    def bin = cases.vaadd_vx
  }

  object vaaddu_vv extends Case {
    def bin = cases.vaaddu_vv
  }

  object vaaddu_vx extends Case {
    def bin = cases.vaaddu_vx
  }

  object vadc_vim extends Case {
    def bin = cases.vadc_vim
  }

  object vadc_vvm extends Case {
    def bin = cases.vadc_vvm
  }

  object vadc_vxm extends Case {
    def bin = cases.vadc_vxm
  }

  object vadd_vi extends Case {
    def bin = cases.vadd_vi
  }

  object vadd_vv extends Case {
    def bin = cases.vadd_vv
  }

  object vadd_vx extends Case {
    def bin = cases.vadd_vx
  }

  object vand_vi extends Case {
    def bin = cases.vand_vi
  }

  object vand_vv extends Case {
    def bin = cases.vand_vv
  }

  object vand_vx extends Case {
    def bin = cases.vand_vx
  }

  object vasub_vv extends Case {
    def bin = cases.vasub_vv
  }

  object vasub_vx extends Case {
    def bin = cases.vasub_vx
  }

  object vasubu_vv extends Case {
    def bin = cases.vasubu_vv
  }

  object vasubu_vx extends Case {
    def bin = cases.vasubu_vx
  }

  object vcompress_vm extends Case {
    def bin = cases.vcompress_vm
  }

  object vcpop_m extends Case {
    def bin = cases.vcpop_m
  }

  object vdiv_vv extends Case {
    def bin = cases.vdiv_vv
  }

  object vdiv_vx extends Case {
    def bin = cases.vdiv_vx
  }

  object vdivu_vv extends Case {
    def bin = cases.vdivu_vv
  }

  object vdivu_vx extends Case {
    def bin = cases.vdivu_vx
  }

  object vid_v extends Case {
    def bin = cases.vid_v
  }

  object viota_m extends Case {
    def bin = cases.viota_m
  }

  object vle16_v extends Case {
    def bin = cases.vle16_v
  }

  object vle32_v extends Case {
    def bin = cases.vle32_v
  }

  object vle64_v extends Case {
    def bin = cases.vle64_v
  }

  object vle8_v extends Case {
    def bin = cases.vle8_v
  }

  object vlm_v extends Case {
    def bin = cases.vlm_v
  }

  object vlse16_v extends Case {
    def bin = cases.vlse16_v
  }

  object vlse32_v extends Case {
    def bin = cases.vlse32_v
  }

  object vlse64_v extends Case {
    def bin = cases.vlse64_v
  }

  object vlse8_v extends Case {
    def bin = cases.vlse8_v
  }

  object vmacc_vv extends Case {
    def bin = cases.vmacc_vv
  }

  object vmacc_vx extends Case {
    def bin = cases.vmacc_vx
  }

  object vmadc_vim extends Case {
    def bin = cases.vmadc_vim
  }

  object vmadc_vvm extends Case {
    def bin = cases.vmadc_vvm
  }

  object vmadd_vv extends Case {
    def bin = cases.vmadd_vv
  }

  object vmadd_vx extends Case {
    def bin = cases.vmadd_vx
  }

  object vmand_mm extends Case {
    def bin = cases.vmand_mm
  }

  object vmandn_mm extends Case {
    def bin = cases.vmandn_mm
  }

  object vmax_vv extends Case {
    def bin = cases.vmax_vv
  }

  object vmax_vx extends Case {
    def bin = cases.vmax_vx
  }

  object vmaxu_vv extends Case {
    def bin = cases.vmaxu_vv
  }

  object vmaxu_vx extends Case {
    def bin = cases.vmaxu_vx
  }

  object vmerge_vim extends Case {
    def bin = cases.vmerge_vim
  }

  object vmerge_vvm extends Case {
    def bin = cases.vmerge_vvm
  }

  object vmin_vv extends Case {
    def bin = cases.vmin_vv
  }

  object vmin_vx extends Case {
    def bin = cases.vmin_vx
  }

  object vminu_vv extends Case {
    def bin = cases.vminu_vv
  }

  object vminu_vx extends Case {
    def bin = cases.vminu_vx
  }

  object vmnand_mm extends Case {
    def bin = cases.vmnand_mm
  }

  object vmnor_mm extends Case {
    def bin = cases.vmnor_mm
  }

  object vmor_mm extends Case {
    def bin = cases.vmor_mm
  }

  object vmorn_mm extends Case {
    def bin = cases.vmorn_mm
  }

  object vmsbc_vvm extends Case {
    def bin = cases.vmsbc_vvm
  }

  object vmsbf_m extends Case {
    def bin = cases.vmsbf_m
  }

  object vmseq_vv extends Case {
    def bin = cases.vmseq_vv
  }

  object vmseq_vx extends Case {
    def bin = cases.vmseq_vx
  }

  object vmsgt_vv extends Case {
    def bin = cases.vmsgt_vv
  }

  object vmsgt_vx extends Case {
    def bin = cases.vmsgt_vx
  }

  object vmsgtu_vv extends Case {
    def bin = cases.vmsgtu_vv
  }

  object vmsgtu_vx extends Case {
    def bin = cases.vmsgtu_vx
  }

  object vmsif_m extends Case {
    def bin = cases.vmsif_m
  }

  object vmsle_vv extends Case {
    def bin = cases.vmsle_vv
  }

  object vmsle_vx extends Case {
    def bin = cases.vmsle_vx
  }

  object vmsleu_vv extends Case {
    def bin = cases.vmsleu_vv
  }

  object vmsleu_vx extends Case {
    def bin = cases.vmsleu_vx
  }

  object vmslt_vv extends Case {
    def bin = cases.vmslt_vv
  }

  object vmslt_vx extends Case {
    def bin = cases.vmslt_vx
  }

  object vmsltu_vv extends Case {
    def bin = cases.vmsltu_vv
  }

  object vmsltu_vx extends Case {
    def bin = cases.vmsltu_vx
  }

  object vmsne_vv extends Case {
    def bin = cases.vmsne_vv
  }

  object vmsne_vx extends Case {
    def bin = cases.vmsne_vx
  }

  object vmsof_m extends Case {
    def bin = cases.vmsof_m
  }

  object vmul_vv extends Case {
    def bin = cases.vmul_vv
  }

  object vmul_vx extends Case {
    def bin = cases.vmul_vx
  }

  object vmulh_vv extends Case {
    def bin = cases.vmulh_vv
  }

  object vmulh_vx extends Case {
    def bin = cases.vmulh_vx
  }

  object vmulhsu_vv extends Case {
    def bin = cases.vmulhsu_vv
  }

  object vmulhsu_vx extends Case {
    def bin = cases.vmulhsu_vx
  }

  object vmulhu_vv extends Case {
    def bin = cases.vmulhu_vv
  }

  object vmulhu_vx extends Case {
    def bin = cases.vmulhu_vx
  }

  object vmv_s_x extends Case {
    def bin = cases.vmv_s_x
  }

  object vmv_v_i extends Case {
    def bin = cases.vmv_v_i
  }

  object vmv_v_v extends Case {
    def bin = cases.vmv_v_v
  }

  object vmv_v_x extends Case {
    def bin = cases.vmv_v_x
  }

  object vmv_x_s extends Case {
    def bin = cases.vmv_x_s
  }

  object vmv1r_v extends Case {
    def bin = cases.vmv1r_v
  }

  object vmv2r_v extends Case {
    def bin = cases.vmv2r_v
  }

  object vmv4r_v extends Case {
    def bin = cases.vmv4r_v
  }

  object vmv8r_v extends Case {
    def bin = cases.vmv8r_v
  }

  object vmxnor_mm extends Case {
    def bin = cases.vmxnor_mm
  }

  object vmxor_mm extends Case {
    def bin = cases.vmxor_mm
  }

  object vnclip_wv extends Case {
    def bin = cases.vnclip_wv
  }

  object vnclipu_wv extends Case {
    def bin = cases.vnclipu_wv
  }

  object vnclipu_wx extends Case {
    def bin = cases.vnclipu_wx
  }

  object vnmsac_vv extends Case {
    def bin = cases.vnmsac_vv
  }

  object vnmsac_vx extends Case {
    def bin = cases.vnmsac_vx
  }

  object vnmsub_vv extends Case {
    def bin = cases.vnmsub_vv
  }

  object vnmsub_vx extends Case {
    def bin = cases.vnmsub_vx
  }

  object vnsra_wv extends Case {
    def bin = cases.vnsra_wv
  }

  object vnsra_wx extends Case {
    def bin = cases.vnsra_wx
  }

  object vnsrl_wv extends Case {
    def bin = cases.vnsrl_wv
  }

  object vnsrl_wx extends Case {
    def bin = cases.vnsrl_wx
  }

  object vor_vi extends Case {
    def bin = cases.vor_vi
  }

  object vor_vv extends Case {
    def bin = cases.vor_vv
  }

  object vor_vx extends Case {
    def bin = cases.vor_vx
  }

  object vredand_vs extends Case {
    def bin = cases.vredand_vs
  }

  object vredmax_vs extends Case {
    def bin = cases.vredmax_vs
  }

  object vredmaxu_vs extends Case {
    def bin = cases.vredmaxu_vs
  }

  object vredmin_vs extends Case {
    def bin = cases.vredmin_vs
  }

  object vredminu_vs extends Case {
    def bin = cases.vredminu_vs
  }

  object vredor_vs extends Case {
    def bin = cases.vredor_vs
  }

  object vredsum_vs extends Case {
    def bin = cases.vredsum_vs
  }

  object vredxor_vs extends Case {
    def bin = cases.vredxor_vs
  }

  object vrem_vv extends Case {
    def bin = cases.vrem_vv
  }

  object vrem_vx extends Case {
    def bin = cases.vrem_vx
  }

  object vremu_vv extends Case {
    def bin = cases.vremu_vv
  }

  object vremu_vx extends Case {
    def bin = cases.vremu_vx
  }

  object vrgather_vv extends Case {
    def bin = cases.vrgather_vv
  }

  object vrgather_vx extends Case {
    def bin = cases.vrgather_vx
  }

  object vrsub_vi extends Case {
    def bin = cases.vrsub_vi
  }

  object vrsub_vx extends Case {
    def bin = cases.vrsub_vx
  }

  object vsadd_vv extends Case {
    def bin = cases.vsadd_vv
  }

  object vsadd_vx extends Case {
    def bin = cases.vsadd_vx
  }

  object vsaddu_vv extends Case {
    def bin = cases.vsaddu_vv
  }

  object vsaddu_vx extends Case {
    def bin = cases.vsaddu_vx
  }

  object vsbc_vvm extends Case {
    def bin = cases.vsbc_vvm
  }

  object vsbc_vxm extends Case {
    def bin = cases.vsbc_vxm
  }

  object vse16_v extends Case {
    def bin = cases.vse16_v
  }

  object vse32_v extends Case {
    def bin = cases.vse32_v
  }

  object vse64_v extends Case {
    def bin = cases.vse64_v
  }

  object vse8_v extends Case {
    def bin = cases.vse8_v
  }

  object vsext_vf2 extends Case {
    def bin = cases.vsext_vf2
  }

  object vsext_vf4 extends Case {
    def bin = cases.vsext_vf4
  }

  object vsext_vf8 extends Case {
    def bin = cases.vsext_vf8
  }

  object vslide1down_vx extends Case {
    def bin = cases.vslide1down_vx
  }

  object vslide1up_vx extends Case {
    def bin = cases.vslide1up_vx
  }

  object vslidedown_vx extends Case {
    def bin = cases.vslidedown_vx
  }

  object vslideup_vx extends Case {
    def bin = cases.vslideup_vx
  }

  object vsll_vi extends Case {
    def bin = cases.vsll_vi
  }

  object vsll_vv extends Case {
    def bin = cases.vsll_vv
  }

  object vsll_vx extends Case {
    def bin = cases.vsll_vx
  }

  object vsm_v extends Case {
    def bin = cases.vsm_v
  }

  object vsmul_vv extends Case {
    def bin = cases.vsmul_vv
  }

  object vsmul_vx extends Case {
    def bin = cases.vsmul_vx
  }

  object vsra_vi extends Case {
    def bin = cases.vsra_vi
  }

  object vsra_vv extends Case {
    def bin = cases.vsra_vv
  }

  object vsra_vx extends Case {
    def bin = cases.vsra_vx
  }

  object vsrl_vi extends Case {
    def bin = cases.vsrl_vi
  }

  object vsrl_vv extends Case {
    def bin = cases.vsrl_vv
  }

  object vsrl_vx extends Case {
    def bin = cases.vsrl_vx
  }

  object vsse16_v extends Case {
    def bin = cases.vsse16_v
  }

  object vsse32_v extends Case {
    def bin = cases.vsse32_v
  }

  object vsse64_v extends Case {
    def bin = cases.vsse64_v
  }

  object vsse8_v extends Case {
    def bin = cases.vsse8_v
  }

  object vssra_vv extends Case {
    def bin = cases.vssra_vv
  }

  object vssra_vx extends Case {
    def bin = cases.vssra_vx
  }

  object vssrl_vv extends Case {
    def bin = cases.vssrl_vv
  }

  object vssrl_vx extends Case {
    def bin = cases.vssrl_vx
  }

  object vssub_vv extends Case {
    def bin = cases.vssub_vv
  }

  object vssub_vx extends Case {
    def bin = cases.vssub_vx
  }

  object vssubu_vv extends Case {
    def bin = cases.vssubu_vv
  }

  object vssubu_vx extends Case {
    def bin = cases.vssubu_vx
  }

  object vsub_vv extends Case {
    def bin = cases.vsub_vv
  }

  object vsub_vx extends Case {
    def bin = cases.vsub_vx
  }

  object vwadd_vv extends Case {
    def bin = cases.vwadd_vv
  }

  object vwadd_vx extends Case {
    def bin = cases.vwadd_vx
  }

  object vwadd_wv extends Case {
    def bin = cases.vwadd_wv
  }

  object vwadd_wx extends Case {
    def bin = cases.vwadd_wx
  }

  object vwaddu_vv extends Case {
    def bin = cases.vwaddu_vv
  }

  object vwaddu_vx extends Case {
    def bin = cases.vwaddu_vx
  }

  object vwaddu_wv extends Case {
    def bin = cases.vwaddu_wv
  }

  object vwaddu_wx extends Case {
    def bin = cases.vwaddu_wx
  }

  object vwmacc_vv extends Case {
    def bin = cases.vwmacc_vv
  }

  object vwmacc_vx extends Case {
    def bin = cases.vwmacc_vx
  }

  object vwmaccsu_vv extends Case {
    def bin = cases.vwmaccsu_vv
  }

  object vwmaccsu_vx extends Case {
    def bin = cases.vwmaccsu_vx
  }

  object vwmaccu_vv extends Case {
    def bin = cases.vwmaccu_vv
  }

  object vwmaccu_vx extends Case {
    def bin = cases.vwmaccu_vx
  }

  object vwmaccus_vx extends Case {
    def bin = cases.vwmaccus_vx
  }

  object vwmul_vv extends Case {
    def bin = cases.vwmul_vv
  }

  object vwmul_vx extends Case {
    def bin = cases.vwmul_vx
  }

  object vwmulsu_vv extends Case {
    def bin = cases.vwmulsu_vv
  }

  object vwmulsu_vx extends Case {
    def bin = cases.vwmulsu_vx
  }

  object vwmulu_vv extends Case {
    def bin = cases.vwmulu_vv
  }

  object vwmulu_vx extends Case {
    def bin = cases.vwmulu_vx
  }

  object vwredsum_vs extends Case {
    def bin = cases.vwredsum_vs
  }

  object vwredsumu_vs extends Case {
    def bin = cases.vwredsumu_vs
  }

  object vwsub_vv extends Case {
    def bin = cases.vwsub_vv
  }

  object vwsub_vx extends Case {
    def bin = cases.vwsub_vx
  }

  object vwsub_wv extends Case {
    def bin = cases.vwsub_wv
  }

  object vwsub_wx extends Case {
    def bin = cases.vwsub_wx
  }

  object vwsubu_vv extends Case {
    def bin = cases.vwsubu_vv
  }

  object vwsubu_vx extends Case {
    def bin = cases.vwsubu_vx
  }

  object vwsubu_wv extends Case {
    def bin = cases.vwsubu_wv
  }

  object vwsubu_wx extends Case {
    def bin = cases.vwsubu_wx
  }

  object vxor_vi extends Case {
    def bin = cases.vxor_vi
  }

  object vxor_vv extends Case {
    def bin = cases.vxor_vv
  }

  object vxor_vx extends Case {
    def bin = cases.vxor_vx
  }

  object vzext_vf2 extends Case {
    def bin = cases.vzext_vf2
  }

  object vzext_vf4 extends Case {
    def bin = cases.vzext_vf4
  }

  object vzext_vf8 extends Case {
    def bin = cases.vzext_vf8
  }
}
