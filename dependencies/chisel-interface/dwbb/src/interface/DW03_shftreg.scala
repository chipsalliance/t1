// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW03_shftreg

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    length: Int = 6
) extends SerializableModuleParameter {
  require(length >= 1, "length must be greater than or equal to 1")
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val s_in: Bool = Input(Bool())
  val p_in: UInt = Input(UInt(parameter.length.W))
  val shift_n: Bool = Input(Bool())
  val load_n: Bool = Input(Bool())
  val p_out: UInt = Output(UInt(parameter.length.W))
}
