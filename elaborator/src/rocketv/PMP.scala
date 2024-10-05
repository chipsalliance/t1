// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{PMPChecker, PMPCheckerParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object PMPChecker extends Elaborator {
  @main
  case class PMPCheckerParameterMain(
    @arg(name = "nPMPs") nPMPs:         Int,
    @arg(name = "paddrBits") paddrBits: Int,
    @arg(name = "lgMaxSize") lgMaxSize: Int,
    @arg(name = "pmpGranularity") pmpGranularity: Int) {
    def convert: PMPCheckerParameter = PMPCheckerParameter(
      nPMPs:          Int,
      paddrBits:      Int,
      lgMaxSize:      Int,
      pmpGranularity: Int
    )
  }

  implicit def PMPCheckerParameterMainParser: ParserForClass[PMPCheckerParameterMain] =
    ParserForClass[PMPCheckerParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: PMPCheckerParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[PMPChecker, PMPCheckerParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
