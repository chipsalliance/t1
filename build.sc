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

  object cases extends Module {
    c =>
    object unittest extends Module {
      u =>
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
            "-configfile", u.millSourcePath / "configs" / s"${name().replace('_', '.')}.toml"
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

    val testCases = Map(
      "smoketest" -> smoketest,
      "mmm" -> mmm,
      "vaadd_vv" -> vaadd_vv,
      "vaadd_vx" -> vaadd_vx,
      "vaaddu_vv" -> vaaddu_vv,
      "vaaddu_vx" -> vaaddu_vx,
      "vadc_vim" -> vadc_vim,
      "vadc_vvm" -> vadc_vvm,
      "vadc_vxm" -> vadc_vxm,
      "vadd_vi" -> vadd_vi,
      "vadd_vv" -> vadd_vv,
      "vadd_vx" -> vadd_vx,
      "vand_vi" -> vand_vi,
      "vand_vv" -> vand_vv,
      "vand_vx" -> vand_vx,
      "vasub_vv" -> vasub_vv,
      "vasub_vx" -> vasub_vx,
      "vasubu_vv" -> vasubu_vv,
      "vasubu_vx" -> vasubu_vx,
      "vcompress_vm" -> vcompress_vm,
      "vcpop_m" -> vcpop_m,
      "vdiv_vv" -> vdiv_vv,
      "vdiv_vx" -> vdiv_vx,
      "vdivu_vv" -> vdivu_vv,
      "vdivu_vx" -> vdivu_vx,
      "vid_v" -> vid_v,
      "viota_m" -> viota_m,
      "vle16_v" -> vle16_v,
      "vle32_v" -> vle32_v,
      "vle64_v" -> vle64_v,
      "vle8_v" -> vle8_v,
      "vlm_v" -> vlm_v,
      "vlse16_v" -> vlse16_v,
      "vlse32_v" -> vlse32_v,
      "vlse64_v" -> vlse64_v,
      "vlse8_v" -> vlse8_v,
      "vmacc_vv" -> vmacc_vv,
      "vmacc_vx" -> vmacc_vx,
      "vmadc_vim" -> vmadc_vim,
      "vmadc_vvm" -> vmadc_vvm,
      "vmadd_vv" -> vmadd_vv,
      "vmadd_vx" -> vmadd_vx,
      "vmand_mm" -> vmand_mm,
      "vmandn_mm" -> vmandn_mm,
      "vmax_vv" -> vmax_vv,
      "vmax_vx" -> vmax_vx,
      "vmaxu_vv" -> vmaxu_vv,
      "vmaxu_vx" -> vmaxu_vx,
      "vmerge_vim" -> vmerge_vim,
      "vmerge_vvm" -> vmerge_vvm,
      "vmin_vv" -> vmin_vv,
      "vmin_vx" -> vmin_vx,
      "vminu_vv" -> vminu_vv,
      "vminu_vx" -> vminu_vx,
      "vmnand_mm" -> vmnand_mm,
      "vmnor_mm" -> vmnor_mm,
      "vmor_mm" -> vmor_mm,
      "vmorn_mm" -> vmorn_mm,
      "vmsbc_vvm" -> vmsbc_vvm,
      "vmsbf_m" -> vmsbf_m,
      "vmseq_vv" -> vmseq_vv,
      "vmseq_vx" -> vmseq_vx,
      "vmsgt_vv" -> vmsgt_vv,
      "vmsgt_vx" -> vmsgt_vx,
      "vmsgtu_vv" -> vmsgtu_vv,
      "vmsgtu_vx" -> vmsgtu_vx,
      "vmsif_m" -> vmsif_m,
      "vmsle_vv" -> vmsle_vv,
      "vmsle_vx" -> vmsle_vx,
      "vmsleu_vv" -> vmsleu_vv,
      "vmsleu_vx" -> vmsleu_vx,
      "vmslt_vv" -> vmslt_vv,
      "vmslt_vx" -> vmslt_vx,
      "vmsltu_vv" -> vmsltu_vv,
      "vmsltu_vx" -> vmsltu_vx,
      "vmsne_vv" -> vmsne_vv,
      "vmsne_vx" -> vmsne_vx,
      "vmsof_m" -> vmsof_m,
      "vmul_vv" -> vmul_vv,
      "vmul_vx" -> vmul_vx,
      "vmulh_vv" -> vmulh_vv,
      "vmulh_vx" -> vmulh_vx,
      "vmulhsu_vv" -> vmulhsu_vv,
      "vmulhsu_vx" -> vmulhsu_vx,
      "vmulhu_vv" -> vmulhu_vv,
      "vmulhu_vx" -> vmulhu_vx,
      "vmv_s_x" -> vmv_s_x,
      "vmv_v_i" -> vmv_v_i,
      "vmv_v_v" -> vmv_v_v,
      "vmv_v_x" -> vmv_v_x,
      "vmv_x_s" -> vmv_x_s,
      "vmv1r_v" -> vmv1r_v,
      "vmv2r_v" -> vmv2r_v,
      "vmv4r_v" -> vmv4r_v,
      "vmv8r_v" -> vmv8r_v,
      "vmxnor_mm" -> vmxnor_mm,
      "vmxor_mm" -> vmxor_mm,
      "vnclip_wv" -> vnclip_wv,
      "vnclipu_wv" -> vnclipu_wv,
      "vnclipu_wx" -> vnclipu_wx,
      "vnmsac_vv" -> vnmsac_vv,
      "vnmsac_vx" -> vnmsac_vx,
      "vnmsub_vv" -> vnmsub_vv,
      "vnmsub_vx" -> vnmsub_vx,
      "vnsra_wv" -> vnsra_wv,
      "vnsra_wx" -> vnsra_wx,
      "vnsrl_wv" -> vnsrl_wv,
      "vnsrl_wx" -> vnsrl_wx,
      "vor_vi" -> vor_vi,
      "vor_vv" -> vor_vv,
      "vor_vx" -> vor_vx,
      "vredand_vs" -> vredand_vs,
      "vredmax_vs" -> vredmax_vs,
      "vredmaxu_vs" -> vredmaxu_vs,
      "vredmin_vs" -> vredmin_vs,
      "vredminu_vs" -> vredminu_vs,
      "vredor_vs" -> vredor_vs,
      "vredsum_vs" -> vredsum_vs,
      "vredxor_vs" -> vredxor_vs,
      "vrem_vv" -> vrem_vv,
      "vrem_vx" -> vrem_vx,
      "vremu_vv" -> vremu_vv,
      "vremu_vx" -> vremu_vx,
      "vrgather_vv" -> vrgather_vv,
      "vrgather_vx" -> vrgather_vx,
      "vrsub_vi" -> vrsub_vi,
      "vrsub_vx" -> vrsub_vx,
      "vsadd_vv" -> vsadd_vv,
      "vsadd_vx" -> vsadd_vx,
      "vsaddu_vv" -> vsaddu_vv,
      "vsaddu_vx" -> vsaddu_vx,
      "vsbc_vvm" -> vsbc_vvm,
      "vsbc_vxm" -> vsbc_vxm,
      "vse16_v" -> vse16_v,
      "vse32_v" -> vse32_v,
      "vse64_v" -> vse64_v,
      "vse8_v" -> vse8_v,
      "vsext_vf2" -> vsext_vf2,
      "vsext_vf4" -> vsext_vf4,
      "vsext_vf8" -> vsext_vf8,
      "vslide1down_vx" -> vslide1down_vx,
      "vslide1up_vx" -> vslide1up_vx,
      "vslidedown_vx" -> vslidedown_vx,
      "vslideup_vx" -> vslideup_vx,
      "vsll_vi" -> vsll_vi,
      "vsll_vv" -> vsll_vv,
      "vsll_vx" -> vsll_vx,
      "vsm_v" -> vsm_v,
      "vsmul_vv" -> vsmul_vv,
      "vsmul_vx" -> vsmul_vx,
      "vsra_vi" -> vsra_vi,
      "vsra_vv" -> vsra_vv,
      "vsra_vx" -> vsra_vx,
      "vsrl_vi" -> vsrl_vi,
      "vsrl_vv" -> vsrl_vv,
      "vsrl_vx" -> vsrl_vx,
      "vsse16_v" -> vsse16_v,
      "vsse32_v" -> vsse32_v,
      "vsse64_v" -> vsse64_v,
      "vsse8_v" -> vsse8_v,
      "vssra_vv" -> vssra_vv,
      "vssra_vx" -> vssra_vx,
      "vssrl_vv" -> vssrl_vv,
      "vssrl_vx" -> vssrl_vx,
      "vssub_vv" -> vssub_vv,
      "vssub_vx" -> vssub_vx,
      "vssubu_vv" -> vssubu_vv,
      "vssubu_vx" -> vssubu_vx,
      "vsub_vv" -> vsub_vv,
      "vsub_vx" -> vsub_vx,
      "vwadd_vv" -> vwadd_vv,
      "vwadd_vx" -> vwadd_vx,
      "vwadd_wv" -> vwadd_wv,
      "vwadd_wx" -> vwadd_wx,
      "vwaddu_vv" -> vwaddu_vv,
      "vwaddu_vx" -> vwaddu_vx,
      "vwaddu_wv" -> vwaddu_wv,
      "vwaddu_wx" -> vwaddu_wx,
      "vwmacc_vv" -> vwmacc_vv,
      "vwmacc_vx" -> vwmacc_vx,
      "vwmaccsu_vv" -> vwmaccsu_vv,
      "vwmaccsu_vx" -> vwmaccsu_vx,
      "vwmaccu_vv" -> vwmaccu_vv,
      "vwmaccu_vx" -> vwmaccu_vx,
      "vwmaccus_vx" -> vwmaccus_vx,
      "vwmul_vv" -> vwmul_vv,
      "vwmul_vx" -> vwmul_vx,
      "vwmulsu_vv" -> vwmulsu_vv,
      "vwmulsu_vx" -> vwmulsu_vx,
      "vwmulu_vv" -> vwmulu_vv,
      "vwmulu_vx" -> vwmulu_vx,
      "vwredsum_vs" -> vwredsum_vs,
      "vwredsumu_vs" -> vwredsumu_vs,
      "vwsub_vv" -> vwsub_vv,
      "vwsub_vx" -> vwsub_vx,
      "vwsub_wv" -> vwsub_wv,
      "vwsub_wx" -> vwsub_wx,
      "vwsubu_vv" -> vwsubu_vv,
      "vwsubu_vx" -> vwsubu_vx,
      "vwsubu_wv" -> vwsubu_wv,
      "vwsubu_wx" -> vwsubu_wx,
      "vxor_vi" -> vxor_vi,
      "vxor_vv" -> vxor_vv,
      "vxor_vx" -> vxor_vx,
      "vzext_vf2" -> vzext_vf2,
      "vzext_vf4" -> vzext_vf4,
      "vzext_vf8" -> vzext_vf8,
    )

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

  def runCase(testCase: cases.Case) = T.task {
    val proc = os.proc(Seq(tests.emulator.elf().path.toString, "--bin", testCase.bin().path.toString, "--wave", (T.dest / "wave").toString))
    T.log.info(s"run test: ${testCase.name()} with:\n ${proc.command.map(_.value.mkString(" ")).mkString(" ")}")
    proc.call()
    PathRef(T.dest)
  }

  def run(args: String*) = T.command {
    T.sequence(args.map(c => runCase(cases.testCases(c))))()
  }
}
