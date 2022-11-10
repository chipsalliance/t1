package tests.elaborate

import chisel3.stage.{ChiselGeneratorAnnotation, PrintFullStackTraceAnnotation}
import circt.stage.{CIRCTHandover, CIRCTTarget, CIRCTTargetAnnotation, ChiselStage, FirtoolOption}
import firrtl.AnnotationSeq
import firrtl.options.TargetDirAnnotation
import mainargs._

object Main {
  @main def elaborate(@arg(name="dir") dir: String) = {
    val annotations = Seq(new ChiselStage).foldLeft(
      Seq(
        TargetDirAnnotation(dir),
        CIRCTTargetAnnotation(CIRCTTarget.Verilog),
        FirtoolOption(s"""-O=debug"""),
        CIRCTHandover(CIRCTHandover.CHIRRTL),
        ChiselGeneratorAnnotation(() => new TestBench),
      ): AnnotationSeq
    ) { case (annos, stage) => stage.transform(annos) }
  }
  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
