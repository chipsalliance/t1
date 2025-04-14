// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import chisel3.experimental.util.SerializableModuleElaborator
import mainargs._
import org.chipsalliance.rocketv.{FPU, FPUParameter}

object FPU extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = getClass.getSimpleName.replace("$", "")
  type D = FPU
  type P = FPUParameter
  type M = FPUParameterMain

  @main
  case class FPUParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset:   Boolean,
    @arg(name = "useClockGating") useClockGating: Boolean,
    @arg(name = "xLen") xLen:                     Int,
    @arg(name = "fLen") fLen:                     Int,
    @arg(name = "minFLen") minFLen:               Int,
    @arg(name = "sfmaLatency") sfmaLatency:       Int,
    @arg(name = "dfmaLatency") dfmaLatency:       Int,
    @arg(name = "divSqrt") divSqrt:               Boolean,
    @arg(name = "hartIdLen") hartIdLen: Int) {
    def convert: FPUParameter = FPUParameter(
      useAsyncReset,
      useClockGating,
      xLen,
      fLen,
      minFLen,
      sfmaLatency,
      dfmaLatency,
      divSqrt,
      hartIdLen
    )
  }

  implicit def FPUParameterMainParser: ParserForClass[FPUParameterMain] = ParserForClass[FPUParameterMain]

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
