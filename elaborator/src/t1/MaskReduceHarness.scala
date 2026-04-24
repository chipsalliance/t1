// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.t1

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.util.SerializableModuleElaborator
import chisel3.util._
import mainargs._
import org.chipsalliance.t1.rtl.{MaskReduce, MaskReduceParameter}

object MaskReduceHarnessParameter {
  implicit def rw: upickle.default.ReadWriter[MaskReduceHarnessParameter] = upickle.default.macroRW
}

case class MaskReduceHarnessParameter(
  eLen:          Int,
  datapathWidth: Int,
  laneNumber:    Int,
  fpuEnable:     Boolean,
  laneScale:     Int
) extends SerializableModuleParameter {
  val maskReduceParameter = MaskReduceParameter(eLen, datapathWidth, laneNumber, fpuEnable, laneScale)
}

@instantiable
class MaskReduceHarness(val parameter: MaskReduceHarnessParameter)
    extends Module
    with SerializableModule[MaskReduceHarnessParameter] {
  val mr: Instance[MaskReduce] = Instantiate(new MaskReduce(parameter.maskReduceParameter))
  mr.io.clock          := clock
  mr.io.reset          := reset
  mr.io.in.valid       := DontCare
  mr.io.in.bits        := DontCare
  mr.io.firstGroup     := DontCare
  mr.io.newInstruction := DontCare
  mr.io.validInst      := DontCare
  mr.io.pop            := DontCare
}

object MaskReduceHarnessElaborator extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = "MaskReduceHarness"
  type D = MaskReduceHarness
  type P = MaskReduceHarnessParameter

  @main
  def config(
    @arg(name = "eLen") eLen:                   Int,
    @arg(name = "datapathWidth") datapathWidth: Int,
    @arg(name = "laneNumber") laneNumber:       Int,
    @arg(name = "fpuEnable") fpuEnable:         Boolean,
    @arg(name = "laneScale") laneScale:         Int
  ) =
    os.write.over(os.pwd / s"${className}.json", configImpl(MaskReduceHarnessParameter(eLen, datapathWidth, laneNumber, fpuEnable, laneScale)))

  @main
  def design(@arg(name = "parameter") parameter: os.Path) = {
    val (firrtl, annos) = designImpl[D, P](os.read.stream(parameter))
    os.write.over(os.pwd / s"$className.fir", firrtl)
    os.write.over(os.pwd / s"$className.json", annos)
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
