// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_fifo_s2_sf

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 8,
    depth: Int = 8,
    pushAELvl: Int = 2,
    pushAFLvl: Int = 2,
    popAELvl: Int = 2,
    popAFLvl: Int = 2,
    errMode: Boolean = false,
    pushSync: String = "double_reg",
    popSync: String = "double_reg",
    rstMode: String = "async_with_mem"
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 1024).contains(width),
    "width must be between 1 and 1024"
  )
  require(
    Range.inclusive(4, 1024).contains(depth),
    "width must be between 4 and 1024"
  )
  require(
    Range.inclusive(1, depth - 1).contains(pushAELvl),
    s"pushAELvl must be between 1 and ${depth - 1}"
  )
  require(
    Range.inclusive(1, depth - 1).contains(pushAFLvl),
    s"pushAFLvl must be between 1 and ${depth - 1}"
  )
  require(
    Range.inclusive(1, depth - 1).contains(popAELvl),
    s"popAELvl must be between 1 and ${depth - 1}"
  )
  require(
    Range.inclusive(1, depth - 1).contains(popAFLvl),
    s"popAFLvl must be between 1 and ${depth - 1}"
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
}

class Interface(parameter: Parameter) extends Bundle {
  val clk_push: Clock = Input(Clock())
  val clk_pop: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val push_req_n: Bool = Input(Bool())
  val pop_req_n: Bool = Input(Bool())
  val data_in: UInt = Input(UInt(parameter.width.W))
  val push_empty: Bool = Output(Bool())
  val push_ae: Bool = Output(Bool())
  val push_hf: Bool = Output(Bool())
  val push_af: Bool = Output(Bool())
  val push_full: Bool = Output(Bool())
  val push_error: Bool = Output(Bool())
  val pop_empty: Bool = Output(Bool())
  val pop_ae: Bool = Output(Bool())
  val pop_hf: Bool = Output(Bool())
  val pop_af: Bool = Output(Bool())
  val pop_full: Bool = Output(Bool())
  val pop_error: Bool = Output(Bool())
  val data_out: UInt = Output(UInt(parameter.width.W))
}
