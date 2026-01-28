// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW01_prienc

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    aWidth: Int = 8,
    indexWidth: Int = 4
) extends SerializableModuleParameter {
  require(
    aWidth >= 1,
    "aWidth must be greater than or equal to 1"
  )
  require(
    indexWidth >= log2Ceil(aWidth),
    s"indexWidth must be greater than or equal to ${log2Ceil(aWidth)}"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val A: UInt = Input(UInt(parameter.aWidth.W))
  val INDEX: UInt = Output(UInt(parameter.indexWidth.W))
}
