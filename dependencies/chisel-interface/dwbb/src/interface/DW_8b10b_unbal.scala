// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_8b10b_unbal

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    k28P5Mode: Boolean = false
) extends SerializableModuleParameter {}

class Interface(parameter: Parameter) extends Bundle {
  val k_char: Bool = Input(Bool())
  val data_in: UInt = Input(UInt(8.W))
  val unbal: Bool = Output(Bool())
}
