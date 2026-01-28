// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW01_ash

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(AWidth: Int = 16, SHWidth: Int = 4)
    extends SerializableModuleParameter {
  require(AWidth >= 2, "AWidth must be greater than or equal to 2")
  require(SHWidth >= 1, "SHWidth must be greater than or equal to 1")
}

class Interface(parameter: Parameter) extends Bundle {
  val A: UInt = Input(UInt(parameter.AWidth.W))
  val SH: UInt = Input(UInt(parameter.SHWidth.W))
  val DATA_TC: Bool = Input(Bool())
  val SH_TC: Bool = Input(Bool())
  val B: UInt = Output(UInt(parameter.AWidth.W))
}
