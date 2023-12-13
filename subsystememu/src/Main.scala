package verdes

import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.stage.phases.{Convert, Elaborate}
import firrtl.AnnotationSeq
import firrtl.options.TargetDirAnnotation
import mainargs._
import org.chipsalliance.cde.config._
import org.chipsalliance.t1.rocketcore.RISCVOpcodesPath
import verdes.fpga._

object Main {
  @main def elaborate(
                       @arg(name = "dir", doc = "output directory") dir: String,
                       @arg(name = "config") config: String,
                       @arg(name = "riscvopcodes") riscvOpcodes: String,
                       @arg(name = "fpga") fpga: Boolean
                     ) = {
    val dir_ = os.Path(dir, os.pwd)
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
          TargetDirAnnotation(dir_.toString()),
          ChiselGeneratorAnnotation(() => if (fpga) new FPGAHarness else new TestHarness)
        ): AnnotationSeq
      ) { case (annos, phase) => phase.transform(annos) }
      .flatMap {
        case firrtl.stage.FirrtlCircuitAnnotation(circuit) =>
          topName = circuit.main
          os.write(dir_ / s"${circuit.main}.fir", circuit.serialize)
          None
        case _: chisel3.stage.ChiselCircuitAnnotation => None
        case _: chisel3.stage.DesignAnnotation[_] => None
        case _: freechips.rocketchip.util.ParamsAnnotation  => None
        case a => Some(a)
      }
    os.write(dir_ / s"$topName.anno.json", firrtl.annotations.JsonProtocol.serialize(annos))
    freechips.rocketchip.util.ElaborationArtefacts.files.foreach { case (ext, contents) => os.write.over(dir_ / s"${p.toString}.${ext}", contents()) }
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}

