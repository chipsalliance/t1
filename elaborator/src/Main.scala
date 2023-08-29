// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

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
  @main def elaborate(
                       @arg(name = "dir") dir: String,
                       @arg(name = "config") config: String,
                       @arg(name = "tb") tb: Boolean
                     ) = {
    val generator = upickle.default.read[SerializableModuleGenerator[V, VParameter]](ujson.read(os.read(os.Path(config))))
    var topName: String = null
    val annos: AnnotationSeq = Seq(
      new chisel3.stage.phases.Elaborate,
      new chisel3.tests.elaborate.Convert
    ).foldLeft(
      Seq(
        ChiselGeneratorAnnotation(() => if(tb) new TestBench(generator) else generator.module())
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
