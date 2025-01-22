// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_asymfifo_s1_sf

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    dataInWidth: Int = 8,
    dataOutWidth: Int = 16,
    depth: Int = 64,
    aeLevel: Int = 2,
    afLevel: Int = 2,
    errMode: String = "latched",
    rstMode: String = "sync_with_mem",
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
    Range.inclusive(2, 256).contains(depth),
    "depth must be between 2 and 256"
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
  require(
    Seq("msb", "lsb").contains(byteOrder),
    "byteOrder must be one of 'msb', 'lsb'"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val push_req_n: Bool = Input(Bool())
  val flush_n: Bool = Input(Bool())
  val pop_req_n: Bool = Input(Bool())
  val diag_n: Bool = Input(Bool())
  val data_in: UInt = Input(UInt(parameter.dataInWidth.W))
  val empty: Bool = Output(Bool())
  val almost_empty: Bool = Output(Bool())
  val half_full: Bool = Output(Bool())
  val almost_full: Bool = Output(Bool())
  val full: Bool = Output(Bool())
  val ram_full: Bool = Output(Bool())
  val error: Bool = Output(Bool())
  val part_wd: Bool = Output(Bool())
  val data_out: UInt = Output(UInt(parameter.dataOutWidth.W))
}
