// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{FPToInt, FPToIntParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object FPToInt extends Elaborator {
  @main
  case class FPToIntParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset: Boolean,
    @arg(name = "xLen") xLen:                   Int,
    @arg(name = "fLen") fLen:                   Int,
    @arg(name = "minFLen") minFLen: Int) {
    def convert: FPToIntParameter = FPToIntParameter(
      useAsyncReset,
      xLen,
      fLen,
      minFLen
    )
  }

  implicit def FPToIntParameterMainParser: ParserForClass[FPToIntParameterMain] = ParserForClass[FPToIntParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: FPToIntParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[FPToInt, FPToIntParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
