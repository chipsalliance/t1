// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{Instance, Instantiate}

case class RocketTileParameter(frontendParameter: FrontendParameter) extends SerializableModuleParameter {
  val rocketParameter: RocketParameter = ???
  val hellaCacheParameter: HellaCacheParameter = ???
  val fpuParameter: FPUParameter = ???
}

class RocketTileInterface(parameter: RocketTileParameter) extends Bundle {
  val hartid = ???
  val interrupts = ???
  // Inward AXI interface accessing ITIM
  val itimSideband = ???
  // Inward AXI interface accessing DTIM
  val dtimSideband = ???
  // Outward AXI interface from ICache
  val imem = ???
  // Outward AXI interface from DCache
  val dmem = ???
}

class RocketTile(val parameter: RocketTileParameter)
  extends FixedIORawModule(new RocketTileInterface(parameter))
    with SerializableModule[RocketTileParameter] {
  val rocket: Instance[Rocket] = Instantiate(new Rocket(parameter.rocketParameter))
  val frontend: Instance[Frontend] = Instantiate(new Frontend(parameter.frontendParameter))
  val hellaCache: Instance[HellaCache] = Instantiate(new HellaCache(parameter.hellaCacheParameter))
  val fpu: Instance[FPU] = Instantiate(new FPU(parameter.fpuParameter))
}
