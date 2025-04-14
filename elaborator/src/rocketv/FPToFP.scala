// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import chisel3.experimental.util.SerializableModuleElaborator
import mainargs._
import org.chipsalliance.rocketv.{FPToFP, FPToFPParameter}

object FPToFP extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = getClass.getSimpleName.replace("$", "")
  type D = FPToFP
  type P = FPToFPParameter
  type M = FPToFPParameterMain

  @main
  case class FPToFPParameterMain(
    useAsyncReset: Boolean,
    latency:       Int,
    xLen:          Int,
    fLen:          Int,
    minFLen: Int) {
    def convert: FPToFPParameter = FPToFPParameter(
      useAsyncReset,
      latency,
      xLen,
      fLen,
      minFLen
    )
  }

  implicit def FPToFPParameterMainParser: ParserForClass[FPToFPParameterMain] = ParserForClass[FPToFPParameterMain]

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
