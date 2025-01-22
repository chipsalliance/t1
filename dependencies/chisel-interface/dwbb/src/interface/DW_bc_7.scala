// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_bc_7

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter() extends SerializableModuleParameter

class Interface(parameter: Parameter) extends Bundle {
  val capture_clk: Clock = Input(Clock())
  val update_clk: Clock = Input(Clock())
  val capture_en: Bool = Input(Bool())
  val update_en: Bool = Input(Bool())
  val shift_dr: Bool = Input(Bool())
  val mode1: Bool = Input(Bool())
  val mode2: Bool = Input(Bool())
  val si: Bool = Input(Bool())
  val pin_input: Bool = Input(Bool())
  val control_out: Bool = Input(Bool())
  val output_data: Bool = Input(Bool())
  val ic_input: Bool = Output(Bool())
  val data_out: Bool = Output(Bool())
  val so: Bool = Output(Bool())
}
