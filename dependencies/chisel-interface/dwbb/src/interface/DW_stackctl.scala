// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_stackctl

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    depth: Int = 16,
    errMode: String = "hold_until_reset",
    rstMode: String = "async"
) extends SerializableModuleParameter {
  require(
    1 <= log2Ceil(depth) && log2Ceil(depth) <= 24,
    "depth must be in [2, 2^24]"
  )
  require(Seq("hold_until_reset", "hold_until_next_clock").contains(errMode))
  require(
    Seq("async", "sync").contains(rstMode),
    "rstMode must be one of 'async' and 'sync'"
  )
  val addrWidth: Int = log2Ceil(depth)
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val push_req_n: Bool = Input(Bool())
  val pop_req_n: Bool = Input(Bool())
  val we_n: Bool = Output(Bool())
  val empty: Bool = Output(Bool())
  val full: Bool = Output(Bool())
  val error: Bool = Output(Bool())
  val wr_addr: UInt = Output(UInt(parameter.addrWidth.W))
  val rd_addr: UInt = Output(UInt(parameter.addrWidth.W))
}
