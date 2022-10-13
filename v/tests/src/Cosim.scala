package v.tests

import chisel3._
import chisel3.aop.Select
import chisel3.aop.injecting.InjectingAspect
import chisel3.experimental.{ChiselAnnotation, annotate}
import chisel3.stage.ChiselGeneratorAnnotation
import firrtl.annotations.Annotation
import firrtl.options.TargetDirAnnotation
import firrtl.stage.{OutputFileAnnotation, RunFirrtlTransformAnnotation}
import firrtl.transforms.TopWiring.TopWiringTransform
import firrtl.{AnnotationSeq, VerilogEmitter}
import os.Path
import utest._
import v.{V, VRF}

object Cosim extends TestSuite {
  val tests = Tests {
    test("cosim") {
      val outputDirectory = os.pwd / "out" / "cosim"
      val generator = () => new v.V(v.VParam())
      var duts: Seq[Path] = null
      val cosim: Path = outputDirectory / "cosim"

      test("emit verilog") {
        val annotations = Seq(new chisel3.stage.ChiselStage).foldLeft(
          Seq(
            TargetDirAnnotation(outputDirectory.toString()),
            ChiselGeneratorAnnotation(generator),
            RunFirrtlTransformAnnotation(new TopWiringTransform),
            InjectingAspect(
              { dut: V =>
                Select.collectDeep(dut) {
                  case vrf: VRF => vrf
                }
              },
              { vrf: VRF => {
                val debug = Wire(chiselTypeOf(vrf.write)).suggestName("_debug")
                debug := vrf.write
                annotate(new ChiselAnnotation {
                  override def toFirrtl: Annotation =
                    firrtl.transforms.TopWiring.TopWiringAnnotation(debug.toTarget, "verilator_debug_")
                })
              }
              }
            ),
            RunFirrtlTransformAnnotation(new VerilogEmitter)
          ): AnnotationSeq
        ) { case (annos, stage) => stage.transform(annos) }
        duts = annotations.collect {
          case OutputFileAnnotation(file) => outputDirectory / s"$file.v"
        }

        test("cosim compile") {
          val cmakefilelist = outputDirectory / "CMakeLists.txt"
          val verilatorArgs = Seq(
            // format: off
            "-Wno-UNOPTTHREADS", "-Wno-STMTDLY", "-Wno-LATCH",
            "--x-assign unique",
            "--output-split 20000",
            "--output-split-cfuncs 20000",
            "--max-num-width 1048576",
            "--trace-fst"
            // format: on
          ).mkString(" ")
          val csrcs = Seq("vbridge.cc")
            .map(resource(_).toString)
            .mkString(" ")
          val vsrcs = duts
            .filter(f => f.ext == "v" | f.ext == "sv")
            .map(_.toString)
            .mkString(" ")
          val cmakefilelistString =
          // format: off
            s"""cmake_minimum_required(VERSION 3.20)
               |project(cosim)
               |include_directories(${resource("usr/include/riscv")})
               |include_directories(${resource("usr/include/fesvr")})
               |include_directories(${resource("usr/include/softfloat")})
               |link_directories(${resource("usr/lib")})
               |include(FetchContent)
               |FetchContent_Declare(args GIT_REPOSITORY https://github.com/Taywee/args GIT_TAG 6.4.0)
               |FetchContent_MakeAvailable(args)
               |
               |find_package(verilator)
               |set(CMAKE_C_COMPILER "clang")
               |set(CMAKE_CXX_COMPILER "clang++")
               |find_package(Threads)
               |set(THREADS_PREFER_PTHREAD_FLAG ON)
               |add_executable(cosim
               |  ${resource("main.cc")}
               |  ${resource("vbridge.cc")}
               |)
               |target_link_libraries(cosim PRIVATE $${CMAKE_THREAD_LIBS_INIT})
               |target_link_libraries(cosim PRIVATE riscv fmt glog args)
               |
               |verilate(cosim
               |  SOURCES $vsrcs
               |  VERILATOR_ARGS $verilatorArgs
               |)
               |""".stripMargin
          // format: on
          os.write.over(
            cmakefilelist,
            cmakefilelistString
          )
          println(s"compiling DUT with CMake:\n" + cmakefilelistString)
          os.proc(
            // format: off
            "cmake",
            "-G", "Ninja",
            outputDirectory.toString
            // format: on
          ).call(outputDirectory)
          println(s"start to compile C++ to emulator")
          os.proc(
            // format: off
            "ninja"
            // format: on
          ).call(outputDirectory)
          test("run smoketest") {
            val proc = os.proc(cosim, "--bin", resource("smoketest"), "--wave", os.pwd / "smoketest", "--cycles", 1000)
            println(s"running ${proc.command.map(_.value.mkString(" ")).mkString(" ")}")
            proc.call(outputDirectory)
          }
        }
      }
    }
  }
}
