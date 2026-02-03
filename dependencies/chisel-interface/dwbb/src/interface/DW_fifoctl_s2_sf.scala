// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_fifoctl_s2_sf

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    depth: Int = 8,
    pushAELvl: Int = 2,
    pushAFLvl: Int = 2,
    popAELvl: Int = 2,
    popAFLvl: Int = 2,
    errMode: Boolean = false,
    pushSync: String = "double_reg",
    popSync: String = "double_reg",
    rstMode: String = "async",
    tstMode: Boolean = false
) extends SerializableModuleParameter {
  require(
    2 <= log2Ceil(depth) && log2Ceil(depth) <= 24,
    "width must be between 4 and 2^24"
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
    Seq("async", "sync")
      .contains(rstMode),
    "rstMode must be one of 'async' and 'sync'"
  )
  val ramAddrWidth: Int = log2Ceil(depth)
  val ramWrdCnt: Int = log2Ceil(depth + 1)
}

class Interface(parameter: Parameter) extends Bundle {
  val clk_push: Clock = Input(Clock())
  val clk_pop: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val push_req_n: Bool = Input(Bool())
  val pop_req_n: Bool = Input(Bool())
  val we_n: Bool = Output(Bool())
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
  val wr_addr: UInt = Output(UInt(parameter.ramAddrWidth.W))
  val rd_addr: UInt = Output(UInt(parameter.ramAddrWidth.W))
  val push_word_count: UInt = Output(UInt(parameter.ramWrdCnt.W))
  val pop_word_count: UInt = Output(UInt(parameter.ramWrdCnt.W))
  val test: Bool = Input(Bool())
}
