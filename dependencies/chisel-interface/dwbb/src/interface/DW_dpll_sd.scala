// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_dpll_sd

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 1,
    divisor: Int = 4,
    gain: Int = 1,
    filter: Int = 2,
    windows: Int = 1
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 16).contains(width),
    "width must be between 1 and 16"
  )
  require(
    Range.inclusive(4, 256).contains(divisor),
    "divisor must be between 4 and 256"
  )
  require(Range.inclusive(1, 2).contains(gain), "gain must be between 1 and 2")
  require(
    Range.inclusive(0, 8).contains(filter),
    "filter must be between 0 and 8"
  )
  require(
    Range.inclusive(1, (divisor + 1) / 2).contains(windows),
    s"windows must be between 1 and ${divisor / 2}"
  )
  val bit_width_windows: Int = log2Ceil(windows)
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val stall: Bool = Input(Bool())
  val squelch: Bool = Input(Bool())
  val window: UInt = Input(UInt(parameter.bit_width_windows.W))
  val data_in: UInt = Input(UInt(parameter.width.W))
  val clk_out: Clock = Output(Clock())
  val bit_ready: Bool = Output(Bool())
  val data_out: UInt = Output(UInt(parameter.width.W))
}
