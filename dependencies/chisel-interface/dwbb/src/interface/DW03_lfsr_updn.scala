// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW03_lfsr_updn

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 15
) extends SerializableModuleParameter {
  require(
    Range.inclusive(2, 50).contains(width),
    "width must be between 2 and 50"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val cen: Bool = Input(Bool())
  val updn: Bool = Input(Bool())
  val clk: Clock = Input(Clock())
  val reset: Bool = Input(Bool())
  val count: UInt = Output(UInt(parameter.width.W))
  val tercnt: Bool = Output(Bool())
}
