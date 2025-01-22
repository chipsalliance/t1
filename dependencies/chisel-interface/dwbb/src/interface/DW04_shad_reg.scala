// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW04_shad_reg

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 8,
    bldShadReg: Boolean = true
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 512).contains(width),
    "width must be between 1 and 512"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val datain: UInt = Input(UInt(parameter.width.W))
  val sys_clk: Clock = Input(Clock())
  val shad_clk: Clock = Input(Clock())
  val reset: Bool = Input(Bool())
  val SI: Bool = Input(Bool())
  val SE: Bool = Input(Bool())
  val sys_out: UInt = Output(UInt(parameter.width.W))
  val shad_out: UInt = Output(UInt(parameter.width.W))
  val SO: Bool = Output(Bool())
}
