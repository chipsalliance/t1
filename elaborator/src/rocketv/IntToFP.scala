// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{IntToFP, IntToFPParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object IntToFP extends Elaborator {
  @main
  case class IntToFPParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset: Boolean,
    @arg(name = "latency") latency:             Int,
    @arg(name = "fLen") fLen:                   Int,
    @arg(name = "xLen") xLen:                   Int,
    @arg(name = "minFLen") minFLen: Int) {
    def convert: IntToFPParameter = IntToFPParameter(
      useAsyncReset,
      latency,
      fLen,
      xLen,
      minFLen
    )
  }

  implicit def IntToFPParameterMainParser: ParserForClass[IntToFPParameterMain] = ParserForClass[IntToFPParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: IntToFPParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[IntToFP, IntToFPParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
