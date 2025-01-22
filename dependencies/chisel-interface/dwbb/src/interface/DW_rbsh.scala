// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_rbsh

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(aWidth: Int = 12, shWidth: Int = 8)
    extends SerializableModuleParameter {
  require(aWidth >= 1, "aWidth must be greater than or equal to 1")
  require(
    Range.inclusive(1, log2Ceil(aWidth)).contains(shWidth),
    s"shWidth must be between 1 and ${log2Ceil(aWidth)}"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val A: UInt = Input(UInt(parameter.aWidth.W))
  val SH: UInt = Input(UInt(parameter.shWidth.W))
  val SH_TC: Bool = Input(Bool())
  val B: UInt = Output(UInt(parameter.aWidth.W))
}
