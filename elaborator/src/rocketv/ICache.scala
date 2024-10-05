// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{ICache, ICacheParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object ICache extends Elaborator {
  @main
  case class ICacheParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset: Boolean,
    @arg(name = "prefetch") prefetch:           Boolean,
    @arg(name = "nSets") nSets:                 Int,
    @arg(name = "nWays") nWays:                 Int,
    @arg(name = "blockBytes") blockBytes:       Int,
    @arg(name = "usingVM") usingVM:             Boolean,
    @arg(name = "vaddrBits") vaddrBits:         Int,
    @arg(name = "paddrBits") paddrBits: Int) {
    def convert: ICacheParameter = ICacheParameter(
      useAsyncReset,
      prefetch,
      nSets,
      nWays,
      blockBytes,
      usingVM,
      vaddrBits,
      paddrBits
    )
  }

  implicit def ICacheParameterMainParser: ParserForClass[ICacheParameterMain] = ParserForClass[ICacheParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: ICacheParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[ICache, ICacheParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
