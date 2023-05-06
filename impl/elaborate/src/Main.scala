package tests.elaborate

import chisel3._
import chisel3.aop.Select
import chisel3.aop.injecting.InjectingAspect
import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.experimental.SerializableModuleGenerator
import v.{V, VParameter}
import firrtl.AnnotationSeq
import firrtl.stage.FirrtlCircuitAnnotation
import mainargs._

object Main {
  @main def elaborate(@arg(name = "dir") dir: String) = {
    var topName: String = null
    val annos: AnnotationSeq = Seq(
      new chisel3.stage.phases.Elaborate,
      new chisel3.tests.elaborate.Convert
    ).foldLeft(
      Seq(
        ChiselGeneratorAnnotation(() => SerializableModuleGenerator(
          classOf[V],
          VParameter(
            xLen = 32,
            vLen = 1024,
            datapathWidth = 32,
            laneNumber = 8,
            physicalAddressWidth = 32,
            chainingSize = 4,
            vrfWriteQueueSize = 4
          )
        ).module())
      ): AnnotationSeq
    ) { case (annos, stage) => stage.transform(annos) }
      .flatMap {
        case FirrtlCircuitAnnotation(circuit) =>
          topName = circuit.main
          os.write(os.Path(dir) / s"$topName.fir", circuit.serialize)
          None
        case _: chisel3.stage.DesignAnnotation[_] => None
        case a => Some(a)
      }
    os.write(os.Path(dir) / s"$topName.anno.json", firrtl.annotations.JsonProtocol.serialize(annos))
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
