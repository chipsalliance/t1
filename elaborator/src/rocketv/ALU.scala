// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{ALU, ALUParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object ALU extends Elaborator {
  @main
  case class ALUParameterMain(
    @arg(name = "xLen") xLen: Int) {
    def convert: ALUParameter = ALUParameter(xLen)
  }

  implicit def ALUParameterMainParser: ParserForClass[ALUParameterMain] = ParserForClass[ALUParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: ALUParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[ALU, ALUParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
