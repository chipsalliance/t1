// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{RVCExpander, RVCExpanderParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object RVCExpander extends Elaborator {
  @main
  case class RVCExpanderParameterMain(
    @arg(name = "xLen") xLen: Int,
    @arg(name = "usingCompressed") usingCompressed: Boolean) {
    def convert: RVCExpanderParameter = RVCExpanderParameter(
      xLen,
      usingCompressed
    )
  }

  implicit def RVCExpanderParameterMainParser: ParserForClass[RVCExpanderParameterMain] =
    ParserForClass[RVCExpanderParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: RVCExpanderParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[RVCExpander, RVCExpanderParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
