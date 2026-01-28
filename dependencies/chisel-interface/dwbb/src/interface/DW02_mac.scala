// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW02_mac

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(AWidth: Int = 16, BWidth: Int = 16)
    extends SerializableModuleParameter {
  require(AWidth >= 1, "AWidth must be greater than or equal to 1")
  require(BWidth >= 1, "BWidth must be greater than or equal to 1")
}

class Interface(parameter: Parameter) extends Bundle {
  val A: UInt = Input(UInt(parameter.AWidth.W))
  val B: UInt = Input(UInt(parameter.BWidth.W))
  val C: UInt = Input(UInt((parameter.AWidth + parameter.BWidth).W))
  val TC: Bool = Input(Bool())
  val MAC: UInt = Output(UInt((parameter.AWidth + parameter.BWidth).W))
}
