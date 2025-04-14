// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import chisel3.experimental.util.SerializableModuleElaborator
import mainargs._
import org.chipsalliance.rocketv.{PTW, PTWParameter}

object PTW extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = getClass.getSimpleName.replace("$", "")
  type D = PTW
  type P = PTWParameter
  type M = PTWParameterMain

  @main
  case class PTWParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset:       Boolean,
    @arg(name = "hasClockGate") hasClockGate:         Boolean,
    @arg(name = "usingVM") usingVM:                   Boolean,
    @arg(name = "usingHypervisor") usingHypervisor:   Boolean,
    @arg(name = "xLen") xLen:                         Int,
    @arg(name = "fLen") fLen:                         Int,
    @arg(name = "paddrBits") paddrBits:               Int,
    @arg(name = "asidBits") asidBits:                 Int,
    @arg(name = "pgLevels") pgLevels:                 Int,
    @arg(name = "nPTECacheEntries") nPTECacheEntries: Int,
    @arg(name = "nL2TLBWays") nL2TLBWays:             Int,
    @arg(name = "nL2TLBEntries") nL2TLBEntries:       Int,
    @arg(name = "nPMPs") nPMPs: Int) {
    def convert: PTWParameter = PTWParameter(
      useAsyncReset,
      hasClockGate,
      usingVM,
      usingHypervisor,
      xLen,
      fLen,
      paddrBits,
      asidBits,
      pgLevels,
      nPTECacheEntries,
      nL2TLBWays,
      nL2TLBEntries,
      nPMPs
    )
  }

  implicit def PTWParameterMainParser: ParserForClass[PTWParameterMain] = ParserForClass[PTWParameterMain]

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
