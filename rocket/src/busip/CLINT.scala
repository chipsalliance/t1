// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: Copyright (c) 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: Copyright (c) 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.Instance

class CLINTInterface(parameter: CLINTParameter) extends Bundle {
  // AXI Inputs
  // Interrupt to cores(width: how many cores it goes to)
}

case class CLINTParameter() extends SerializableModuleParameter

class CLINT(val parameter: CLINTParameter)
  extends FixedIORawModule(new CLINTInterface(parameter))
    with SerializableModule[CLINTParameter] {
}
