// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_bc_1

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
  val mode: Bool = Input(Bool())
  val si: Bool = Input(Bool())
  val data_in: Bool = Input(Bool())
  val data_out: Bool = Output(Bool())
  val so: Bool = Output(Bool())
}
