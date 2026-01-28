// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_asymfifoctl_s2_sf

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
    dataOutWidth: Int = 24,
    depth: Int = 8,
    pushAeLevel: Int = 2,
    pushAfLevel: Int = 28,
    popAeLevel: Int = 2,
    popAfLevel: Int = 2,
    errMode: String = "latched",
    pushSync: String = "double_reg",
    popSync: String = "double_reg",
    rstMode: String = "sync",
    byteOrder: String = "msb"
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 4096).contains(dataInWidth),
    "dataInWidth must be between 1 and 4096"
  )
  require(
    Range.inclusive(1, 256).contains(dataOutWidth),
    "dataOutWidth must be between 1 and 256"
  )
  require(
    2 <= log2Ceil(depth) && log2Ceil(depth) <= 24,
    "depth must be between 4 and 2^24"
  )
  require(
    Range.inclusive(1, depth - 1).contains(pushAeLevel),
    s"pushAeLevel must be between 1 and ${depth - 1}"
  )
  require(
    Range.inclusive(1, depth - 1).contains(pushAfLevel),
    s"pushAfLevel must be between 1 and ${depth - 1}"
  )
  require(
    Range.inclusive(1, depth - 1).contains(popAeLevel),
    s"popAeLevel must be between 1 and ${depth - 1}"
  )
  require(
    Range.inclusive(1, depth - 1).contains(popAfLevel),
    s"popAfLevel must be between 1 and ${depth - 1}"
  )
  require(
    Seq("latched", "unlatched").contains(errMode),
    "errMode must be one of 'latched', 'unlatched'"
  )
  require(
    Seq("single_reg", "double_reg", "triple_reg").contains(pushSync),
    "pushSync must be one of 'single_reg', 'double_reg', 'triple_reg'"
  )
  require(
    Seq("single_reg", "double_reg", "triple_reg").contains(popSync),
    "popSync must be one of 'single_reg', 'double_reg', 'triple_reg'"
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
  val clk_push: Clock = Input(Clock())
  val clk_pop: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val push_req_n: Bool = Input(Bool())
  val flush_n: Bool = Input(Bool())
  val pop_req_n: Bool = Input(Bool())
  val data_in: UInt = Input(UInt(parameter.dataInWidth.W))
  val rd_data: UInt = Input(UInt(parameter.dataWidth.W))
  val we_n: Bool = Output(Bool())
  val push_empty: Bool = Output(Bool())
  val push_ae: Bool = Output(Bool())
  val push_hf: Bool = Output(Bool())
  val push_af: Bool = Output(Bool())
  val push_full: Bool = Output(Bool())
  val ram_full: Bool = Output(Bool())
  val part_wd: Bool = Output(Bool())
  val push_error: Bool = Output(Bool())
  val pop_empty: Bool = Output(Bool())
  val pop_ae: Bool = Output(Bool())
  val pop_hf: Bool = Output(Bool())
  val pop_af: Bool = Output(Bool())
  val pop_full: Bool = Output(Bool())
  val pop_error: Bool = Output(Bool())
  val wr_data: UInt = Output(UInt(parameter.dataWidth.W))
  val wr_addr: UInt = Output(UInt(parameter.bitWidth.W))
  val rd_addr: UInt = Output(UInt(parameter.bitWidth.W))
  val data_out: UInt = Output(UInt(parameter.dataOutWidth.W))
}
