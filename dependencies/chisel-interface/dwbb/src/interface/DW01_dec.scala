// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW01_dec

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(width: Int = 16) extends SerializableModuleParameter {
  require(width >= 1, "width must be greater than or equal to 1")
}

class Interface(parameter: Parameter) extends Bundle {
  val A: UInt = Input(UInt(parameter.width.W))
  val SUM: UInt = Output(UInt(parameter.width.W))
}
