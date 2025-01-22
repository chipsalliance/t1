// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW01_binenc

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    aWidth: Int = 32,
    addrWidth: Int = 6
) extends SerializableModuleParameter {
  require(
    aWidth >= 1,
    "aWidth must be greater than or equal to 1"
  )
  require(
    addrWidth >= log2Ceil(aWidth),
    s"addrWidth must be greater than or equal to ${log2Ceil(aWidth)}"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val A: UInt = Input(UInt(parameter.aWidth.W))
  val ADDR: UInt = Output(UInt(parameter.addrWidth.W))
}
