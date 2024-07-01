// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{HellaCache, HellaCacheParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object DCache extends Elaborator {
  @main
  case class DCacheParameterMain(
                               @arg(name = "xLen") xLen: Int,
                               @arg(name = "fLen") fLen: Int,
                               @arg(name = "vaddrBitsExtended") vaddrBitsExtended: Int,
                               @arg(name = "vaddrBits") vaddrBits: Int,
                               @arg(name = "paddrBits") paddrBits: Int,
                             ) {
    def convert: HellaCacheParameter = HellaCacheParameter(xLen, fLen, vaddrBitsExtended, vaddrBits, paddrBits)
  }

  implicit def DCacheParameterMainParser: ParserForClass[DCacheParameterMain] = ParserForClass[DCacheParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: DCacheParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[HellaCache, HellaCacheParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
