package v.elaborate

import chisel3._
import chisel3.aop.Select
import chisel3.aop.injecting.InjectingAspect
import chisel3.stage.ChiselGeneratorAnnotation
import firrtl.AnnotationSeq
import firrtl.options.TargetDirAnnotation
import mainargs._
import v.{LSU, RegFile, V, VRF}

object Main {
  @main def elaborate(@arg(name="dir") dir: String) = {
    val annotations = Seq(new chisel3.stage.ChiselStage).foldLeft(
      Seq(
        TargetDirAnnotation(dir),
        ChiselGeneratorAnnotation(() => new v.V(v.VParam())),
        InjectingAspect(
          { dut: V =>
            Select.collectDeep(dut) {
              case v: V => v
            }
          },
          { v: V => chisel3.experimental.Trace.traceName(v.instCount) }
        ),
        InjectingAspect(
          { dut: V =>
            Select.collectDeep(dut) {
              case vrf: VRF => vrf
            }
          },
          { vrf: VRF => chisel3.experimental.Trace.traceName(vrf.write) }
        ),
        InjectingAspect(
          { dut: V =>
            Select.collectDeep(dut) {
              case lsu: LSU => lsu
            }
          },
          { lsu: LSU =>
            lsu.writeQueueVec.map(queue => chisel3.experimental.Trace.traceName(queue.io.enq))
          }
        ),
        InjectingAspect(
          { dut: V =>
            Select.collectDeep(dut) {
              case lsu: LSU => lsu
            }
          },
          { lsu: LSU =>
            val reqEnqDBG = RegNext(lsu.reqEnq).suggestName("reqEnq_debug")
            chisel3.dontTouch(reqEnqDBG)
            chisel3.experimental.Trace.traceName(reqEnqDBG) }
        ),
        InjectingAspect(
          { dut: V => Select.collectDeep(dut) { case regFile: RegFile => regFile } },
          { regFile: RegFile => chisel3.experimental.Trace.traceName(regFile.writePort) }
        ),
      ): AnnotationSeq
    ) { case (annos, stage) => stage.transform(annos) }
  }
  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}