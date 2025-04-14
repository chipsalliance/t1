// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import chisel3.experimental.util.SerializableModuleElaborator
import mainargs._
import org.chipsalliance.rocketv.{FPUFMAPipe, FPUFMAPipeParameter, FType}

object FPUFMAPipe extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = getClass.getSimpleName.replace("$", "")
  type D = FPUFMAPipe
  type P = FPUFMAPipeParameter
  type M = FPUFMAPipeParameterMain

  @main
  case class FPUFMAPipeParameterMain(
    useAsyncReset: Boolean,
    latency:       Int,
    xLen:          Int,
    fLen:          Int,
    minFLen:       Int,
    t: String) {

    def convert: FPUFMAPipeParameter = FPUFMAPipeParameter(
      useAsyncReset,
      latency,
      xLen,
      fLen,
      minFLen,
      t match {
        case s"e${exp}s${sig}" => FType(exp.toInt, sig.toInt)
        case "h"               => FType(5, 11)
        case "s"               => FType(8, 24)
        case "d"               => FType(11, 53)
      }
    )
  }

  implicit def FPUFMAPipeParameterMainParser: ParserForClass[FPUFMAPipeParameterMain] =
    ParserForClass[FPUFMAPipeParameterMain]

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
