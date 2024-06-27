// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{BreakpointUnit, BreakpointUnitParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object BreakpointUnit extends Elaborator {
  @main
  case class BreakpointUnitParameterMain(
    @arg(name = "nBreakpoints") nBreakpoints:   Int,
    @arg(name = "xLen") xLen:                   Int,
    @arg(name = "useBPWatch") useBPWatch:       Boolean,
    @arg(name = "vaddrBits") vaddrBits:         Int,
    @arg(name = "mcontextWidth") mcontextWidth: Int,
    @arg(name = "scontextWidth") scontextWidth: Int) {
    def convert: BreakpointUnitParameter =
      BreakpointUnitParameter(nBreakpoints, xLen, useBPWatch, vaddrBits, mcontextWidth, scontextWidth)
  }

  implicit def BreakpointUnitParameterMainParser: ParserForClass[BreakpointUnitParameterMain] =
    ParserForClass[BreakpointUnitParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: BreakpointUnitParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[BreakpointUnit, BreakpointUnitParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
