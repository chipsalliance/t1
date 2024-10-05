// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{BHTParameter, BTB, BTBParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object BTB extends Elaborator {
  @main
  case class BHTParameterMain(
    @arg(name = "nEntries") nEntries:           Int,
    @arg(name = "counterLength") counterLength: Int,
    @arg(name = "historyLength") historyLength: Int,
    @arg(name = "historyBits") historyBits: Int) {
    def convert: BHTParameter = BHTParameter(
      nEntries,
      counterLength,
      historyLength,
      historyBits
    )
  }
  implicit def BHTParameterMainParser: ParserForClass[BHTParameterMain] = ParserForClass[BHTParameterMain]

  @main
  case class BTBParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset:         Boolean,
    @arg(name = "fetchBytes") fetchBytes:               Int,
    @arg(name = "vaddrBits") vaddrBits:                 Int,
    @arg(name = "entries") entries:                     Int,
    @arg(name = "nMatchBits") nMatchBits:               Int,
    @arg(name = "nPages") nPages:                       Int,
    @arg(name = "nRAS") nRAS:                           Int,
    @arg(name = "cacheBlockBytes") cacheBlockBytes:     Int,
    @arg(name = "iCacheSet") iCacheSet:                 Int,
    @arg(name = "useCompressed") useCompressed:         Boolean,
    @arg(name = "updatesOutOfOrder") updatesOutOfOrder: Boolean,
    @arg(name = "bht-nEntries") nEntries:               Option[Int],
    @arg(name = "bht-counterLength") counterLength:     Option[Int],
    @arg(name = "bht-historyLength") historyLength:     Option[Int],
    @arg(name = "bht-historyBits") historyBits:         Option[Int],
    @arg(name = "fetchWidth") fetchWidth: Int) {
    def convert: BTBParameter = BTBParameter(
      useAsyncReset,
      fetchBytes,
      vaddrBits,
      entries,
      nMatchBits,
      nPages,
      nRAS,
      cacheBlockBytes,
      iCacheSet,
      useCompressed,
      updatesOutOfOrder,
      fetchWidth,
      (nEntries
        .lazyZip(counterLength)
        .lazyZip(historyLength)
        .lazyZip(historyBits))
        .map { case (nEntries, counterLength, historyLength, historyBits) =>
          BHTParameter(nEntries, counterLength, historyLength, historyBits)
        }
        .headOption
    )
  }

  implicit def BTBParameterMainParser: ParserForClass[BTBParameterMain] = ParserForClass[BTBParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: BTBParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[BTB, BTBParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
