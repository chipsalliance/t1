// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW01_bsh

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(AWidth: Int = 8, SHWidth: Int = 3)
    extends SerializableModuleParameter {
  require(AWidth >= 2, "AWidth must be greater than or equal to 2")
  require(
    Range.inclusive(1, log2Ceil(AWidth)).contains(SHWidth),
    s"SHWidth must be between 1 and ${log2Ceil(AWidth)}"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val A: UInt = Input(UInt(parameter.AWidth.W))
  val SH: UInt = Input(UInt(parameter.SHWidth.W))
  val B: UInt = Output(UInt(parameter.AWidth.W))
}
