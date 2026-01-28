// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_asymfifoctl_s1_sf

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    dataInWidth: Int = 4,
    dataOutWidth: Int = 16,
    depth: Int = 10,
    aeLevel: Int = 1,
    afLevel: Int = 9,
    errMode: String = "latched",
    rstMode: String = "sync",
    byteOrder: String = "msb"
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
    Range.inclusive(1, depth - 1).contains(aeLevel),
    s"aeLevel must be between 1 and ${depth - 1}"
  )
  require(
    Range.inclusive(1, depth - 1).contains(afLevel),
    s"afLevel must be between 1 and ${depth - 1}"
  )
  require(
    Seq("pointer_latched", "latched", "unlatched").contains(errMode),
    "errMode must be one of 'pointer_latched', 'latched', 'unlatched'"
  )
  require(
    Seq("sync", "async").contains(rstMode),
    "rstMode must be one of 'sync', 'async'"
  )
  require(
    Seq("msb", "lsb").contains(byteOrder),
    "byteOrder must be one of 'msb', 'lsb'"
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
  val wr_addr: UInt = Output(UInt(log2Ceil(parameter.depth).W))
  val rd_addr: UInt = Output(UInt(log2Ceil(parameter.depth).W))
  val data_out: UInt = Output(UInt(parameter.dataOutWidth.W))
}
