// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{FPUFMAPipe, FPUFMAPipeParameter, FType}
import org.chipsalliance.t1.elaborator.Elaborator

object FPUFMAPipe extends Elaborator {
  @main
  case class FPUFMAPipeParameterMain(
    useAsyncReset: Boolean,
    latency:       Int,
    xLen:          Int,
    fLen:          Int,
    minFLen:       Int,
    t: String) {

    def convert: FPUFMAPipeParameter = FPUFMAPipeParameter(
      useAsyncReset,
      latency,
      xLen,
      fLen,
      minFLen,
      t match {
        case s"e${exp}s${sig}" => FType(exp.toInt, sig.toInt)
        case "h"               => FType(5, 11)
        case "s"               => FType(8, 24)
        case "d"               => FType(11, 53)
      }
    )
  }

  implicit def FPUFMAPipeParameterMainParser: ParserForClass[FPUFMAPipeParameterMain] =
    ParserForClass[FPUFMAPipeParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: FPUFMAPipeParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[FPUFMAPipe, FPUFMAPipeParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
