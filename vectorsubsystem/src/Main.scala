package verdes

import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.stage.phases.{Convert, Elaborate}
import firrtl.AnnotationSeq
import firrtl.options.TargetDirAnnotation
import freechips.rocketchip.diplomacy.LazyModule
import mainargs._

object Main {
  @main def elaborate(@arg(name = "dir", doc = "output directory") dir: String) = {
    implicit val p = new VerdesConfig
    var topName: String = null
    val annos = Seq(
      new Elaborate,
      new Convert
    ).foldLeft(
        Seq(
          TargetDirAnnotation(dir),
          ChiselGeneratorAnnotation(() => new TestHarness)
        ): AnnotationSeq
      ) { case (annos, phase) => phase.transform(annos) }
      .flatMap {
        case firrtl.stage.FirrtlCircuitAnnotation(circuit) =>
          topName = circuit.main
          os.write(os.Path(dir) / s"${circuit.main}.fir", circuit.serialize)
          None
        case _: chisel3.stage.ChiselCircuitAnnotation => None
        case _: chisel3.stage.DesignAnnotation[_] => None
        case a => Some(a)
      }
    os.write(os.Path(dir) / s"$topName.anno.json", firrtl.annotations.JsonProtocol.serialize(annos))
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}

