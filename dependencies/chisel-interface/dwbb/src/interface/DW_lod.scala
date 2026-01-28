// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_lod

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    aWidth: Int = 8
) extends SerializableModuleParameter {
  require(aWidth >= 1, "aWidth must be greater than or equal to 1")
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt(parameter.aWidth.W))
  val enc: UInt = Output(UInt((log2Ceil(parameter.aWidth) + 1).W))
  val dec: UInt = Output(UInt(parameter.aWidth.W))
}
