// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import chisel3.experimental.util.SerializableModuleElaborator
import mainargs._
import org.chipsalliance.rocketv.{MulAddRecFNPipe, MulAddRecFNPipeParameter}

object MulAddRecFNPipe extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = getClass.getSimpleName.replace("$", "")
  type D = MulAddRecFNPipe
  type P = MulAddRecFNPipeParameter
  type M = MulAddRecFNPipeParameterMain

  @main
  case class MulAddRecFNPipeParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset: Boolean,
    @arg(name = "latency") latency:             Int,
    @arg(name = "expWidth") expWidth:           Int,
    @arg(name = "sigWidth") sigWidth: Int) {
    def convert: MulAddRecFNPipeParameter = MulAddRecFNPipeParameter(useAsyncReset, latency, expWidth, sigWidth)
  }

  implicit def MulAddRecFNPipeParameterMainParser: ParserForClass[MulAddRecFNPipeParameterMain] =
    ParserForClass[MulAddRecFNPipeParameterMain]

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
