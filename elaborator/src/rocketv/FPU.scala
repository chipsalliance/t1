// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{FPU, FPUParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object FPU extends Elaborator {
  @main
  case class FPUParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset:   Boolean,
    @arg(name = "useClockGating") useClockGating: Boolean,
    @arg(name = "xLen") xLen:                     Int,
    @arg(name = "fLen") fLen:                     Int,
    @arg(name = "minFLen") minFLen:               Int,
    @arg(name = "sfmaLatency") sfmaLatency:       Int,
    @arg(name = "dfmaLatency") dfmaLatency:       Int,
    @arg(name = "divSqrt") divSqrt:               Boolean,
    @arg(name = "hartIdLen") hartIdLen: Int) {
    def convert: FPUParameter = FPUParameter(
      useAsyncReset,
      useClockGating,
      xLen,
      fLen,
      minFLen,
      sfmaLatency,
      dfmaLatency,
      divSqrt,
      hartIdLen
    )
  }

  implicit def FPUParameterMainParser: ParserForClass[FPUParameterMain] = ParserForClass[FPUParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: FPUParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[FPU, FPUParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
