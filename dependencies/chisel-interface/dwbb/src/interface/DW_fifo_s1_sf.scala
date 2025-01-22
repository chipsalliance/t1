// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_fifo_s1_sf

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 8,
    depth: Int = 4,
    aeLevel: Int = 1,
    afLevel: Int = 1,
    errMode: String = "pointer_latched",
    rstMode: String = "async_with_mem"
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 2048).contains(width),
    "width must be between 1 and 256"
  )
  require(
    Range.inclusive(2, 1024).contains(depth),
    "depth must be between 1 and 256"
  )
  require(
    Range.inclusive(1, depth - 1).contains(aeLevel),
    "aeLevel must be between 1 and depth-1"
  )
  require(
    Range.inclusive(1, depth - 1).contains(afLevel),
    "afLevel must be between 1 and depth-1"
  )
  require(
    Seq("pointer_latched", "latched", "unlatched").contains(errMode),
    "errMode must be one of 'pointer_latched', 'latched', 'unlatched'"
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
  val diag_n: Bool = Input(Bool())
  val data_in: UInt = Input(UInt(parameter.width.W))
  val empty: Bool = Output(Bool())
  val almost_empty: Bool = Output(Bool())
  val half_full: Bool = Output(Bool())
  val almost_full: Bool = Output(Bool())
  val full: Bool = Output(Bool())
  val error: Bool = Output(Bool())
  val data_out: UInt = Output(UInt(parameter.width.W))
}
