// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_8b10b_enc

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    bytes: Int = 2,
    k28P5Only: Boolean = false,
    enMode: Boolean = false,
    initMode: Boolean = true,
    rstMode: Boolean = false,
    opIsoMode: String = "DW_lp_op_iso_mode"
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 16).contains(bytes),
    "bytes must be in the range 1 to 16"
  )
  require(
    Seq("DW_lp_op_iso_mode", "none", "and", "or", "prefer_and").contains(
      opIsoMode
    ),
    "opIsoMode must be one of 'DW_lp_op_iso_mode', 'none', 'and', 'or', 'prefer_and'"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val init_rd_n: Bool = Input(Bool())
  val init_rd_val: Bool = Input(Bool())
  val k_char: UInt = Output(UInt(parameter.bytes.W))
  val data_in: UInt = Input(UInt((parameter.bytes * 10).W))
  val rd: Bool = Output(Bool())
  val data_out: UInt = Output(UInt((parameter.bytes * 8).W))
  val enable: Bool = Input(Bool())
}
