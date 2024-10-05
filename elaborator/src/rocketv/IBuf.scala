// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{IBuf, IBufParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object IBuf extends Elaborator {
  @main
  case class IBufParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset:         Boolean,
    @arg(name = "xLen") xLen:                           Int,
    @arg(name = "usingCompressed") usingCompressed:     Boolean,
    @arg(name = "vaddrBits") vaddrBits:                 Int,
    @arg(name = "entries") entries:                     Int,
    @arg(name = "vaddrBitsExtended") vaddrBitsExtended: Int,
    @arg(name = "bhtHistoryLength") bhtHistoryLength:   Option[Int],
    @arg(name = "bhtCounterLength") bhtCounterLength:   Option[Int],
    @arg(name = "fetchWidth") fetchWidth: Int) {
    def convert: IBufParameter = IBufParameter(
      useAsyncReset,
      xLen,
      usingCompressed,
      vaddrBits,
      entries,
      vaddrBitsExtended,
      bhtHistoryLength,
      bhtCounterLength,
      fetchWidth
    )
  }

  implicit def IBufParameterMainParser: ParserForClass[IBufParameterMain] = ParserForClass[IBufParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: IBufParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[IBuf, IBufParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
