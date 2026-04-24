// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.t1

import chisel3.experimental.util.SerializableModuleElaborator
import mainargs._
import org.chipsalliance.t1.rtl.{LaneShifter => LaneShifterRTL, LaneShifterParameter}

object LaneShifter extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = getClass.getSimpleName.replace("$", "")
  type D = LaneShifterRTL
  type P = LaneShifterParameter

  @main
  def config(@arg(name = "datapathWidth") datapathWidth: Int, @arg(name = "latency") latency: Int) =
    os.write.over(os.pwd / s"${className}.json", configImpl(LaneShifterParameter(datapathWidth, latency)))

  @main
  def design(@arg(name = "parameter") parameter: os.Path) = {
    val (firrtl, annos) = designImpl[D, P](os.read.stream(parameter))
    os.write.over(os.pwd / s"$className.fir", firrtl)
    os.write.over(os.pwd / s"$className.json", annos)
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
