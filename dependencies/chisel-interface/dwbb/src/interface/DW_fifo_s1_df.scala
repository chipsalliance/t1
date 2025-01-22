// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_fifo_s1_df

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 8,
    depth: Int = 4,
    errMode: String = "pointer_latched",
    rstMode: String = "async_with_mem"
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 2048).contains(width),
    "width must be between 1 and 2048"
  )
  require(
    Range.inclusive(2, 1024).contains(depth),
    "width must be between 2 and 1024"
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
  val bitWidth: Int = log2Ceil(depth)
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val push_req_n: Bool = Input(Bool())
  val pop_req_n: Bool = Input(Bool())
  val diag_n: Bool = Input(Bool())
  val ae_level: UInt = Input(UInt(parameter.bitWidth.W))
  val af_thresh: UInt = Input(UInt(parameter.bitWidth.W))
  val data_in: UInt = Input(UInt(parameter.width.W))
  val empty: Bool = Output(Bool())
  val almost_empty: Bool = Output(Bool())
  val half_full: Bool = Output(Bool())
  val almost_full: Bool = Output(Bool())
  val full: Bool = Output(Bool())
  val error: Bool = Output(Bool())
  val data_out: UInt = Output(UInt(parameter.width.W))
}
