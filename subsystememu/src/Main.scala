package verdes

import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.stage.phases.{Convert, Elaborate}
import firrtl.AnnotationSeq
import firrtl.options.TargetDirAnnotation
import mainargs._
import org.chipsalliance.cde.config._
import org.chipsalliance.t1.rocketcore.RISCVOpcodesPath

object Main {
  @main def elaborate(
                       @arg(name = "dir", doc = "output directory") dir: String,
                       @arg(name = "config") config: String,
                       @arg(name = "riscvopcodes") riscvOpcodes: String
                     ) = {
    implicit val p: Parameters = (new VerdesConfig).orElse(new Config((site, here, up) => {
        case T1ConfigPath => os.Path(config)
        case RISCVOpcodesPath => os.Path(riscvOpcodes)
      })
    )
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

