// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_sla

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(aWidth: Int = 8, shWidth: Int = 3)
    extends SerializableModuleParameter {
  require(aWidth >= 2, "aWidth must be greater than or equal to 2")
  require(shWidth >= 1, "shWidth must be greater than or equal to 1")
}

class Interface(parameter: Parameter) extends Bundle {
  val A: UInt = Input(UInt(parameter.aWidth.W))
  val SH: UInt = Input(UInt(parameter.shWidth.W))
  val SH_TC: Bool = Input(Bool())
  val B: UInt = Output(UInt(parameter.aWidth.W))
}
