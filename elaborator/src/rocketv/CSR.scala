// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{CSR, CSRParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object CSR extends Elaborator {
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
    @arg(name = "usingNMI") usingNMI:               Boolean) {
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
      usingNMI:        Boolean
    )
  }

  implicit def CSRParameterMainParser: ParserForClass[CSRParameterMain] = ParserForClass[CSRParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: CSRParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[CSR, CSRParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
