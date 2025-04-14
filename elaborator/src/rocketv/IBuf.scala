// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import chisel3.experimental.util.SerializableModuleElaborator
import mainargs._
import org.chipsalliance.rocketv.{IBuf, IBufParameter}

object IBuf extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = getClass.getSimpleName.replace("$", "")
  type D = IBuf
  type P = IBufParameter
  type M = IBufParameterMain

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
  def config(@arg(name = "parameter") parameter: M) =
    os.write.over(os.pwd / s"${className}.json", configImpl(parameter.convert))

  @main
  def design(@arg(name = "parameter") parameter: os.Path) = {
    val (firrtl, annos) = designImpl[D, P](os.read.stream(parameter))
    os.write.over(os.pwd / s"$className.fir", firrtl)
    os.write.over(os.pwd / s"$className.json", annos)
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
