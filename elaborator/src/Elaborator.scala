// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator

import chisel3.RawModule
import chisel3.experimental.{SerializableModule, SerializableModuleGenerator, SerializableModuleParameter}
import mainargs.TokensReader

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe.{runtimeMirror, typeOf}

// TODO: this will be upstreamed to Chisel
trait Elaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  def configImpl[P <: SerializableModuleParameter: universe.TypeTag](
    parameter:    P
  )(
    implicit rwP: upickle.default.Writer[P]
  ) = os.write.over(
    os.pwd / s"${getClass.getSimpleName.replace("$", "")}.json",
    upickle.default.write(parameter)
  )

  def designImpl[M <: SerializableModule[P]: universe.TypeTag, P <: SerializableModuleParameter: universe.TypeTag](
    parameter:  os.Path,
    runFirtool: Boolean
  )(
    implicit
    rwP:        upickle.default.Reader[P]
  ) = {
    var fir: firrtl.ir.Circuit = null
    val annos        = Seq(
      new chisel3.stage.phases.Elaborate,
      new chisel3.stage.phases.Convert
    ).foldLeft(
      Seq(
        chisel3.stage.ChiselGeneratorAnnotation(() =>
          SerializableModuleGenerator(
            runtimeMirror(getClass.getClassLoader)
              .runtimeClass(typeOf[M].typeSymbol.asClass)
              .asInstanceOf[Class[M]],
            upickle.default.read[P](os.read(parameter))
          ).module().asInstanceOf[RawModule]
        )
      ): firrtl.AnnotationSeq
    ) { case (annos, stage) => stage.transform(annos) }
      .flatMap {
        case firrtl.stage.FirrtlCircuitAnnotation(circuit) =>
          fir = circuit
          None
        case _: chisel3.stage.DesignAnnotation[_]     => None
        case _: chisel3.stage.ChiselCircuitAnnotation => None
        case a => Some(a)
      }
    val annoJsonFile = os.pwd / s"${fir.main}.anno.json"
    val firFile      = os.pwd / s"${fir.main}.fir"
    val svFile       = os.pwd / s"${fir.main}.sv"
    os.write.over(firFile, fir.serialize)
    os.write.over(
      annoJsonFile,
      firrtl.annotations.JsonProtocol.serializeRecover(annos)
    )
    if (runFirtool) {
      os.proc(
        "firtool",
        s"--annotation-file=${annoJsonFile}",
        s"${firFile}",
        s"-o",
        s"${svFile}",
        "--strip-debug-info",
        "--verification-flavor=sva",
        "--extract-test-code"
      ).call(os.pwd)
    }
  }
}
