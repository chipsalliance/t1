// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import chisel3.experimental.util.SerializableModuleElaborator
import mainargs._
import org.chipsalliance.rocketv.{Rocket, RocketParameter}

object Rocket extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = getClass.getSimpleName.replace("$", "")
  type D = Rocket
  type P = RocketParameter
  type M = RocketParameterMain

  @main
  case class RocketParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset:                   Boolean,
    @arg(name = "clockGate") clockGate:                           Boolean,
    @arg(name = "instructionSets") instructionSets:               Set[String],
    @arg(name = "vLen") vLen:                                     Int,
    @arg(name = "usingUser") usingUser:                           Boolean,
    @arg(name = "hartIdLen") hartIdLen:                           Int,
    @arg(name = "nPMPs") nPMPs:                                   Int,
    @arg(name = "asidBits") asidBits:                             Int,
    @arg(name = "nBreakpoints") nBreakpoints:                     Int,
    @arg(name = "usingBTB") usingBTB:                             Boolean,
    @arg(name = "useBPWatch") useBPWatch:                         Boolean,
    @arg(name = "mcontextWidth") mcontextWidth:                   Int,
    @arg(name = "scontextWidth") scontextWidth:                   Int,
    @arg(name = "mulDivLantency") mulDivLantency:                 Int,
    @arg(name = "divUnroll") divUnroll:                           Int,
    @arg(name = "divEarlyOut") divEarlyOut:                       Boolean,
    @arg(name = "divEarlyOutGranularity") divEarlyOutGranularity: Int,
    @arg(name = "mulUnroll") mulUnroll:                           Int,
    @arg(name = "mulEarlyOut") mulEarlyOut:                       Boolean,
    @arg(name = "paddrBits") paddrBits:                           Int,
    @arg(name = "cacheBlockBytes") cacheBlockBytes:               Int,
    @arg(name = "hasBeu") hasBeu:                                 Boolean,
    @arg(name = "fastLoadByte") fastLoadByte:                     Boolean,
    @arg(name = "fastLoadWord") fastLoadWord:                     Boolean,
    @arg(name = "dcacheNSets") dcacheNSets:                       Int,
    @arg(name = "flushOnFenceI") flushOnFenceI:                   Boolean,
    @arg(name = "usingT1") usingT1: Boolean) {
    def convert: RocketParameter = RocketParameter(
      useAsyncReset,
      clockGate,
      instructionSets,
      vLen,
      usingUser,
      hartIdLen,
      nPMPs,
      asidBits,
      nBreakpoints,
      usingBTB,
      useBPWatch,
      mcontextWidth,
      scontextWidth,
      mulDivLantency,
      divUnroll,
      divEarlyOut,
      divEarlyOutGranularity,
      mulUnroll,
      mulEarlyOut,
      paddrBits,
      cacheBlockBytes,
      hasBeu,
      fastLoadByte,
      fastLoadWord,
      dcacheNSets,
      flushOnFenceI,
      usingT1
    )
  }

  implicit def RocketParameterMainParser: ParserForClass[RocketParameterMain] = ParserForClass[RocketParameterMain]

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
