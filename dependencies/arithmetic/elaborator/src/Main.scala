package elaborator

import firrtl.AnnotationSeq
import firrtl.stage.FirrtlCircuitAnnotation
import chisel3.stage._
import mainargs._

object Main {
  @main
  def elaborate(
           @arg(name = "dir") dir: String,
         ): Unit = {
    val topName = "TestBench"
    val annos: AnnotationSeq = Seq(
      new chisel3.stage.phases.Elaborate,
      new chisel3.stage.phases.Convert
    ).foldLeft(
        Seq(
          ChiselGeneratorAnnotation(() => new TestBench(8, 24))
        ): AnnotationSeq
      ) { case (annos, stage) => stage.transform(annos) }
      .flatMap {
        case FirrtlCircuitAnnotation(circuit) =>
          os.write.over(os.Path(dir) / s"$topName.fir", circuit.serialize)
          None
        case _: chisel3.stage.DesignAnnotation[_] => None
        case _: chisel3.stage.ChiselCircuitAnnotation => None
        case a => Some(a)
      }
    os.write.over(os.Path(dir) / s"$topName.anno.json", firrtl.annotations.JsonProtocol.serialize(annos))
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}