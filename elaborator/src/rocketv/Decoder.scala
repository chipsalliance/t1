// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import chisel3.experimental.util.SerializableModuleElaborator
import mainargs._
import org.chipsalliance.rocketv.{Decoder, DecoderParameter}

object Decoder extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = getClass.getSimpleName.replace("$", "")
  type D = Decoder
  type P = DecoderParameter
  type M = DecoderParameterMain

  @main
  case class DecoderParameterMain(
    @arg(name = "instructionSets") instructionSets: Set[String],
    @arg(name = "pipelinedMul") pipelinedMul:       Boolean,
    @arg(name = "fenceIFlushDCache") fenceIFlushDCache: Boolean) {
    def convert: DecoderParameter = DecoderParameter(
      instructionSets,
      pipelinedMul,
      fenceIFlushDCache
    )
  }

  implicit def DecoderParameterMainParser: ParserForClass[DecoderParameterMain] = ParserForClass[DecoderParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: M) =
    os.write.over(os.pwd / s"${className}.json", configImpl(parameter.convert))

  @main
  def design(@arg(name = "parameter") parameter: os.Path) = {
    val (firrtl, annos) = designImpl[D, P](os.read.stream(parameter))
    os.write.over(os.pwd / s"$className.fir", firrtl)
    os.write.over(os.pwd / s"$className.json", annos)
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
