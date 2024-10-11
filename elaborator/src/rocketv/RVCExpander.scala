// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import chisel3.experimental.util.SerializableModuleElaborator
import mainargs._
import org.chipsalliance.rocketv.{RVCExpander, RVCExpanderParameter}

object RVCExpander extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = getClass.getSimpleName.replace("$", "")
  type D = RVCExpander
  type P = RVCExpanderParameter
  type M = RVCExpanderParameterMain

  @main
  case class RVCExpanderParameterMain(
    @arg(name = "xLen") xLen: Int,
    @arg(name = "usingCompressed") usingCompressed: Boolean) {
    def convert: RVCExpanderParameter = RVCExpanderParameter(
      xLen,
      usingCompressed
    )
  }

  implicit def RVCExpanderParameterMainParser: ParserForClass[RVCExpanderParameterMain] =
    ParserForClass[RVCExpanderParameterMain]

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
