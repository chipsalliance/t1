// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{PipelinedMultiplier, PipelinedMultiplierParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object PipelinedMultiplier extends Elaborator {
  @main
  case class PipelinedMultiplierParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset: Boolean,
    @arg(name = "latency") latency:             Int,
    @arg(name = "width") width: Int) {
    def convert: PipelinedMultiplierParameter = PipelinedMultiplierParameter(
      useAsyncReset: Boolean,
      latency:       Int,
      width:         Int
    )
  }

  implicit def PipelinedMultiplierParameterMainParser: ParserForClass[PipelinedMultiplierParameterMain] =
    ParserForClass[PipelinedMultiplierParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: PipelinedMultiplierParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[PipelinedMultiplier, PipelinedMultiplierParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
