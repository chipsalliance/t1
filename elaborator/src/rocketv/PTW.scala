// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import mainargs._
import org.chipsalliance.rocketv.{PTW, PTWParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object PTW extends Elaborator {
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
  def config(@arg(name = "parameter") parameter: PTWParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[PTW, PTWParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
