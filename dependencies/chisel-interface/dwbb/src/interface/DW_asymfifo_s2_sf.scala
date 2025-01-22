// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_asymfifo_s2_sf

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    dataInWidth: Int = 16,
    dataOutWidth: Int = 8,
    depth: Int = 16,
    pushAeLvl: Int = 2,
    pushAfLvl: Int = 2,
    popAeLvl: Int = 2,
    popAfLvl: Int = 2,
    errMode: String = "latched",
    pushSync: String = "double_reg",
    popSync: String = "double_reg",
    rstMode: String = "sync_with_mem",
    byteOrder: String = "msb"
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 2048).contains(dataInWidth),
    "dataInWidth must be between 1 and 2048"
  )
  require(
    Range.inclusive(1, 2048).contains(dataOutWidth),
    "dataOutWidth must be between 1 and 2048"
  )
  require(
    Range.inclusive(4, 1024).contains(depth),
    "depth must be between 4 and 1024"
  )
  require(
    Range.inclusive(1, depth - 1).contains(pushAeLvl),
    s"pushAeLvl must be between 1 and ${depth - 1}"
  )
  require(
    Range.inclusive(1, depth - 1).contains(pushAfLvl),
    s"pushAfLvl must be between 1 and ${depth - 1}"
  )
  require(
    Range.inclusive(1, depth - 1).contains(popAeLvl),
    s"popAeLvl must be between 1 and ${depth - 1}"
  )
  require(
    Range.inclusive(1, depth - 1).contains(popAfLvl),
    s"popAfLvl must be between 1 and ${depth - 1}"
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
    Seq("async_with_mem", "sync_with_mem", "async_wo_mem", "sync_wo_mem")
      .contains(rstMode),
    "rstMode must be one of 'async_with_mem', 'sync_with_mem', 'async_wo_mem', 'sync_wo_mem'"
  )
  require(
    Seq("msb", "lsb").contains(byteOrder),
    "byteOrder must be one of 'msb', 'lsb'"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val clk_push: Clock = Input(Clock())
  val clk_pop: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val push_req_n: Bool = Input(Bool())
  val flush_n: Bool = Input(Bool())
  val pop_req_n: Bool = Input(Bool())
  val data_in: UInt = Input(UInt(parameter.dataInWidth.W))
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
  val data_out: UInt = Output(UInt(parameter.dataOutWidth.W))
}
