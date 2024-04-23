// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.Instance

case class FPUParameter() extends SerializableModuleParameter
class FPUInterface(parameter: FPUParameter) extends Bundle {

}

// TODO: all hardfloat module can be replaced by DWBB?
class FPU(val parameter: FPUParameter)
  extends FixedIORawModule(new FPUInterface(parameter))
    with SerializableModule[FPUParameter] {

}
