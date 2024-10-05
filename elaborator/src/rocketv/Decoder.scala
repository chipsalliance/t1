// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{Decoder, DecoderParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object Decoder extends Elaborator {
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
  def config(@arg(name = "parameter") parameter: DecoderParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[Decoder, DecoderParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
