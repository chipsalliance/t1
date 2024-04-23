// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: Copyright (c) 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: Copyright (c) 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.Instance

class DebugModuleInterface(parameter: DebugModuleParameter) extends Bundle {
  // clock and reset.
  // AXI Inputs
  // AXI Outputs(System Bus Access)
  // Interrupt to Cores
}

case class DebugModuleParameter() extends SerializableModuleParameter

class DebugModule(val parameter: DebugModuleParameter)
  extends FixedIORawModule(new DebugModuleInterface(parameter))
    with SerializableModule[DebugModuleParameter] {
}

class DebugInnerModuleInterface(parameter: DebugModuleParameter) extends Bundle {
  // clock and reset.
  // AXI Inputs
  // AXI Outputs(System Bus Access)
  // Interrupt to Cores
  // DMI to outer
}

/** DMI <-> System Bus(AXI)
  * DMI -> Cores(Interrupt)
  * under system bus domains
  */
class DebugInnerModule(val parameter: DebugModuleParameter)
  extends FixedIORawModule(new DebugModuleInterface(parameter))
    with SerializableModule[DebugModuleParameter] {
}

class DebugOuterModuleInterface(parameter: DebugModuleParameter) extends Bundle {
  // clock and reset.
  // DMI from inner
}

/** JTAG(for now, add AXI and cJTAG in the future.) <-> DMI
  * under JTAG clock domains.
  */
class DebugOuterModule(val parameter: DebugModuleParameter)
  extends FixedIORawModule(new DebugModuleInterface(parameter))
    with SerializableModule[DebugModuleParameter] {
}
