// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{MulAddRecFNPipe, MulAddRecFNPipeParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object MulAddRecFNPipe extends Elaborator {
  @main
  case class MulAddRecFNPipeParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset: Boolean,
    @arg(name = "latency") latency:             Int,
    @arg(name = "expWidth") expWidth:           Int,
    @arg(name = "sigWidth") sigWidth: Int) {
    def convert: MulAddRecFNPipeParameter = MulAddRecFNPipeParameter(useAsyncReset, latency, expWidth, sigWidth)
  }

  implicit def MulAddRecFNPipeParameterMainParser: ParserForClass[MulAddRecFNPipeParameterMain] =
    ParserForClass[MulAddRecFNPipeParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: MulAddRecFNPipeParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[MulAddRecFNPipe, MulAddRecFNPipeParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
