package v.elaborate

import mainargs._

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
import v.{V, VRF}

object Main {
  @main def elaborate(@arg(name="dir") dir: String) = {
    val annotations = Seq(new chisel3.stage.ChiselStage).foldLeft(
      Seq(
        TargetDirAnnotation(dir),
        ChiselGeneratorAnnotation(() => new v.V(v.VParam())),
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
  }
  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}