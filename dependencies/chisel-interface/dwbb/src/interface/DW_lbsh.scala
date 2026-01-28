// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_lbsh

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(aWidth: Int = 49, shWidth: Int = 6)
    extends SerializableModuleParameter {
  require(aWidth >= 2, "aWidth must be greater than or equal to 2")
  require(
    Range.inclusive(0, log2Ceil(aWidth)).contains(shWidth),
    s"shWidth must be between 0 and ${log2Ceil(aWidth)}."
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val A: UInt = Input(UInt(parameter.aWidth.W))
  val SH: UInt = Input(UInt(parameter.shWidth.W))
  val SH_TC: Bool = Input(Bool())
  val B: UInt = Output(UInt(parameter.aWidth.W))
}
