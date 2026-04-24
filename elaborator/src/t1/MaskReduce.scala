// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.t1

import chisel3.experimental.util.SerializableModuleElaborator
import mainargs._
import org.chipsalliance.t1.rtl.{MaskReduce => MaskReduceRTL, MaskReduceParameter}

object MaskReduce extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = getClass.getSimpleName.replace("$", "")
  type D = MaskReduceRTL
  type P = MaskReduceParameter

  @main
  def config(
    @arg(name = "eLen") eLen: Int,
    @arg(name = "datapathWidth") datapathWidth: Int,
    @arg(name = "laneNumber") laneNumber: Int,
    @arg(name = "fpuEnable") fpuEnable: Boolean,
    @arg(name = "laneScale") laneScale: Int
  ) =
    os.write.over(os.pwd / s"${className}.json", configImpl(MaskReduceParameter(eLen, datapathWidth, laneNumber, fpuEnable, laneScale)))

  @main
  def design(@arg(name = "parameter") parameter: os.Path) = {
    val (firrtl, annos) = designImpl[D, P](os.read.stream(parameter))
    os.write.over(os.pwd / s"$className.fir", firrtl)
    os.write.over(os.pwd / s"$className.json", annos)
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
