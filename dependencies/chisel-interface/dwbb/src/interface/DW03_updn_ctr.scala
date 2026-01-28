// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW03_updn_ctr

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 12
) extends SerializableModuleParameter {
  require(width >= 1, "width must be greater than or equal to 1")
}

class Interface(parameter: Parameter) extends Bundle {
  val cen: Bool = Input(Bool())
  val clk: Clock = Input(Clock())
  val reset: Bool = Input(Bool())
  val up_dn: Bool = Input(Bool())
  val load: Bool = Input(Bool())
  val data: UInt = Input(UInt(parameter.width.W))
  val count: UInt = Output(UInt(parameter.width.W))
  val tercnt: Bool = Output(Bool())
}
