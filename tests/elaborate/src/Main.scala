package tests.elaborate

import chisel3.stage.{ChiselGeneratorAnnotation, NoRunFirrtlCompilerAnnotation}
import circt.stage.{CIRCTHandover, CIRCTTarget, CIRCTTargetAnnotation}
import firrtl.AnnotationSeq
import firrtl.options.TargetDirAnnotation
import mainargs._

object Main {
  @main def elaborate(@arg(name="dir") dir: String) = {
    val annotations = Seq(new chisel3.stage.ChiselStage).foldLeft(
      Seq(
        TargetDirAnnotation(dir),
        CIRCTTargetAnnotation(CIRCTTarget.Verilog),
        CIRCTHandover(CIRCTHandover.CHIRRTL),
        ChiselGeneratorAnnotation(() => new TestBench),
        NoRunFirrtlCompilerAnnotation,
      ): AnnotationSeq
    ) { case (annos, stage) => stage.transform(annos) }
  }
  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
