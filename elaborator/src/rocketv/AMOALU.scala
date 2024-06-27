// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{AMOALU, AMOALUParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object AMOALU extends Elaborator {
  @main
  case class AMOALUParameterMain(
    @arg(name = "operandBits") operandBits: Int) {
    def convert: AMOALUParameter = AMOALUParameter(operandBits)
  }

  implicit def AMOALUParameterMainParser: ParserForClass[AMOALUParameterMain] = ParserForClass[AMOALUParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: AMOALUParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[AMOALU, AMOALUParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
