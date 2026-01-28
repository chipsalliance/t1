// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_stack

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 8,
    depth: Int = 16,
    errMode: String = "hold_until_reset",
    rstMode: String = "async_with_mem"
) extends SerializableModuleParameter {
  require(width >= 1, "width must be greater than or equal to 1")
  require(depth >= 1, "depth must be greater than or equal to 1")
  require(
    Seq("hold_until_reset", "hold_until_next_clock").contains(errMode),
    "errMode must be one of 'hold_until_reset', 'hold_until_next_clock'"
  )
  require(
    Seq("async_with_mem", "sync_with_mem", "async_wo_mem", "sync_wo_mem")
      .contains(rstMode),
    "rstMode must be one of 'async_with_mem', 'sync_with_mem', 'async_wo_mem', 'sync_wo_mem'"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val push_req_n: Bool = Input(Bool())
  val pop_req_n: Bool = Input(Bool())
  val data_in: UInt = Input(UInt(parameter.width.W))
  val empty: Bool = Output(Bool())
  val full: Bool = Output(Bool())
  val error: Bool = Output(Bool())
  val data_out: UInt = Output(UInt(parameter.width.W))
}
