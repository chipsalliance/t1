// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_asymfifoctl_s1_df

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    dataInWidth: Int = 8,
    dataOutWidth: Int = 16,
    depth: Int = 8,
    errMode: Int = 1,
    rstMode: String = "sync",
    byteOrder: Boolean = false
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 256).contains(dataInWidth),
    "dataInWidth must be between 1 and 256"
  )
  require(
    Range.inclusive(1, 256).contains(dataOutWidth),
    "dataOutWidth must be between 1 and 256"
  )
  require(
    1 <= log2Ceil(depth) && log2Ceil(depth) <= 24,
    "depth must be between 2 and 2^24"
  )
  require(
    Range.inclusive(0, 2).contains(errMode),
    "errMode must be between 0 and 2"
  )
  require(
    Seq("sync", "async").contains(rstMode),
    "rstMode must be one of 'sync', 'async'"
  )
  val dataWidth: Int = math.max(dataInWidth, dataOutWidth)
  val bitWidth: Int = log2Ceil(depth)
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val push_req_n: Bool = Input(Bool())
  val flush_n: Bool = Input(Bool())
  val pop_req_n: Bool = Input(Bool())
  val diag_n: Bool = Input(Bool())
  val data_in: UInt = Input(UInt(parameter.dataInWidth.W))
  val rd_data: UInt = Input(UInt(parameter.dataWidth.W))
  val ae_level: UInt = Input(UInt(parameter.bitWidth.W))
  val af_thresh: UInt = Input(UInt(parameter.bitWidth.W))
  val we_n: Bool = Output(Bool())
  val empty: Bool = Output(Bool())
  val almost_empty: Bool = Output(Bool())
  val half_full: Bool = Output(Bool())
  val almost_full: Bool = Output(Bool())
  val full: Bool = Output(Bool())
  val ram_full: Bool = Output(Bool())
  val error: Bool = Output(Bool())
  val part_wd: Bool = Output(Bool())
  val wr_data: UInt = Output(UInt(parameter.dataWidth.W))
  val wr_addr: UInt = Output(UInt(parameter.bitWidth.W))
  val rd_addr: UInt = Output(UInt(parameter.bitWidth.W))
  val data_out: UInt = Output(UInt(parameter.dataOutWidth.W))
}
