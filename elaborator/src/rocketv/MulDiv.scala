// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{MulDiv, MulDivParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object MulDiv extends Elaborator {
  @main
  case class MulDivParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset:                   Boolean,
    @arg(name = "latency") latency:                               Int,
    @arg(name = "width") width:                                   Int,
    @arg(name = "divUnroll") divUnroll:                           Int,
    @arg(name = "divEarlyOut") divEarlyOut:                       Boolean,
    @arg(name = "divEarlyOutGranularity") divEarlyOutGranularity: Int,
    @arg(name = "mulUnroll") mulUnroll:                           Int,
    @arg(name = "mulEarlyOut") mulEarlyOut:                       Boolean) {
    def convert: MulDivParameter = MulDivParameter(
      useAsyncReset,
      latency,
      width,
      divUnroll,
      divEarlyOut,
      divEarlyOutGranularity,
      mulUnroll,
      mulEarlyOut
    )
  }

  implicit def MulDivParameterMainParser: ParserForClass[MulDivParameterMain] = ParserForClass[MulDivParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: MulDivParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[MulDiv, MulDivParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
