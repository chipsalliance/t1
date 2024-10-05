// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{FPToFP, FPToFPParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object FPToFP extends Elaborator {
  @main
  case class FPToFPParameterMain(
    useAsyncReset: Boolean,
    latency:       Int,
    xLen:          Int,
    fLen:          Int,
    minFLen: Int) {
    def convert: FPToFPParameter = FPToFPParameter(
      useAsyncReset,
      latency,
      xLen,
      fLen,
      minFLen
    )
  }

  implicit def FPToFPParameterMainParser: ParserForClass[FPToFPParameterMain] = ParserForClass[FPToFPParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: FPToFPParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[FPToFP, FPToFPParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
