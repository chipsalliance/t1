// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_thermdec

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(width: Int = 8) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 16).contains(width),
    "width must be between 1 and 16"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val en: Bool = Input(Bool())
  val a: UInt = Input(UInt(parameter.width.W))
  val b: UInt = Output(UInt(parameter.width.W))
}
