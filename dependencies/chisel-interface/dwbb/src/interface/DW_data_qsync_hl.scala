// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_data_qsync_hl

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 8,
    clkRatio: Int = 2,
    tstMode: Boolean = false
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 1024).contains(width),
    "width must be between 1 and 1024"
  )
  require(
    Range.inclusive(2, 1024).contains(clkRatio),
    "clkRatio must be between 2 and 1024"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val clk_s: Clock = Input(Clock())
  val rst_s_n: Bool = Input(Bool())
  val init_s_n: Bool = Input(Bool())
  val send_s: Bool = Input(Bool())
  val data_s: UInt = Input(UInt(parameter.width.W))
  val clk_d: Clock = Input(Clock())
  val rst_d_n: Bool = Input(Bool())
  val init_d_n: Bool = Input(Bool())
  val test: Bool = Input(Bool())
  val data_d: UInt = Output(UInt(parameter.width.W))
  val data_valid_d: Bool = Output(Bool())
}
