// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import chisel3.experimental.util.SerializableModuleElaborator
import mainargs._
import org.chipsalliance.rocketv.{CSR, CSRParameter}

object CSR extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = getClass.getSimpleName.replace("$", "")
  type D = CSR
  type P = CSRParameter
  type M = CSRParameterMain

  @main
  case class CSRParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset:     Boolean,
    @arg(name = "vLen") vLen:                       Int,
    @arg(name = "xLen") xLen:                       Int,
    @arg(name = "fLen") fLen:                       Int,
    @arg(name = "hartIdLen") hartIdLen:             Int,
    @arg(name = "mcontextWidth") mcontextWidth:     Int,
    @arg(name = "scontextWidth") scontextWidth:     Int,
    @arg(name = "asidBits") asidBits:               Int,
    @arg(name = "vmidBits") vmidBits:               Int,
    @arg(name = "nPMPs") nPMPs:                     Int,
    @arg(name = "nPerfCounters") nPerfCounters:     Int,
    @arg(name = "paddrBits") paddrBits:             Int,
    @arg(name = "nBreakpoints") nBreakpoints:       Int,
    @arg(name = "usingSupervisor") usingSupervisor: Boolean,
    @arg(name = "usingFPU") usingFPU:               Boolean,
    @arg(name = "usingUser") usingUser:             Boolean,
    @arg(name = "usingVM") usingVM:                 Boolean,
    @arg(name = "usingCompressed") usingCompressed: Boolean,
    @arg(name = "usingAtomics") usingAtomics:       Boolean,
    @arg(name = "usingDebug") usingDebug:           Boolean,
    @arg(name = "usingMulDiv") usingMulDiv:         Boolean,
    @arg(name = "usingVector") usingVector:         Boolean,
    @arg(name = "usingZVMA") usingZVMA: Boolean) {
    def convert: CSRParameter = CSRParameter(
      useAsyncReset:   Boolean,
      vLen:            Int,
      xLen:            Int,
      fLen:            Int,
      hartIdLen:       Int,
      mcontextWidth:   Int,
      scontextWidth:   Int,
      asidBits:        Int,
      vmidBits:        Int,
      nPMPs:           Int,
      nPerfCounters:   Int,
      paddrBits:       Int,
      nBreakpoints:    Int,
      usingSupervisor: Boolean,
      usingFPU:        Boolean,
      usingUser:       Boolean,
      usingVM:         Boolean,
      usingCompressed: Boolean,
      usingAtomics:    Boolean,
      usingDebug:      Boolean,
      usingMulDiv:     Boolean,
      usingVector:     Boolean,
      usingZVMA:       Boolean
    )
  }

  implicit def CSRParameterMainParser: ParserForClass[CSRParameterMain] = ParserForClass[CSRParameterMain]

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
