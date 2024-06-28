// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{CSR, CSRParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object CSR extends Elaborator {
  @main
  case class CSRParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset:     Boolean,
    @arg(name = "vLen") vLen:                       Int,
    @arg(name = "xLen") xLen:                       Int,
    @arg(name = "fLen") fLen:                       Int,
    @arg(name = "usingSupervisor") usingSupervisor: Boolean,
    @arg(name = "usingFPU") usingFPU:               Boolean,
    @arg(name = "usingUser") usingUser:             Boolean,
    @arg(name = "usingVM") usingVM:                 Boolean,
    @arg(name = "pgLevels") pgLevels:               Int,
    @arg(name = "hartIdLen") hartIdLen:             Int,
    @arg(name = "usingCompressed") usingCompressed: Boolean,
    @arg(name = "usingAtomics") usingAtomics:       Boolean,
    @arg(name = "usingDebug") usingDebug:           Boolean,
    @arg(name = "usingMulDiv") usingMulDiv:         Boolean,
    @arg(name = "usingVector") usingVector:         Boolean) {
    def convert: CSRParameter = CSRParameter(
      useAsyncReset,
      vLen,
      xLen,
      fLen,
      usingSupervisor,
      usingFPU,
      usingUser,
      usingVM,
      pgLevels,
      hartIdLen,
      usingCompressed,
      usingAtomics,
      usingDebug,
      usingMulDiv,
      usingVector
    )
  }

  implicit def CSRParameterMainParser: ParserForClass[CSRParameterMain] = ParserForClass[CSRParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: CSRParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[CSR, CSRParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
