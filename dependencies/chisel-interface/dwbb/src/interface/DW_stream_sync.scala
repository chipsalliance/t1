// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_stream_sync

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
    prefillLvl: Int = 0,
    fSyncType: String = "pos_pos_sync",
    regStat: Boolean = true,
    tstMode: String = "no_latch",
    verifEn: Int = 1,
    rSyncType: String = "pos_pos_sync",
    regInProg: Boolean = true
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 1024).contains(width),
    "width must be between 1 and 1024"
  )
  require(
    Range.inclusive(2, 256).contains(depth),
    "depth must be between 1 and 256"
  )
  require(
    Range.inclusive(0, depth - 1).contains(prefillLvl),
    s"prefillLvl must be between 0 and ${depth - 1}"
  )
  require(
    Seq(
      "single_clock",
      "neg_pos_sync",
      "pos_pos_sync",
      "3_pos_sync",
      "4_pos_sync"
    ).contains(fSyncType),
    "fSyncType must be one of 'single_clock', 'neg_pos_sync', 'pos_pos_sync', '3_pos_sync', '4_pos_sync'"
  )
  require(
    Seq(
      "single_clock",
      "neg_pos_sync",
      "pos_pos_sync",
      "3_pos_sync",
      "4_pos_sync"
    ).contains(rSyncType),
    "rSyncType must be one of 'single_clock', 'neg_pos_sync', 'pos_pos_sync', '3_pos_sync', '4_pos_sync'"
  )
  require(
    Seq("no_latch", "ff", "latch").contains(tstMode),
    "tstMode must be one of 'no_latch', 'ff', 'latch'"
  )
  require(
    Range.inclusive(0, 4).contains(verifEn),
    "verifEn must be between 0 and 4"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val clk_s: Clock = Input(Clock())
  val rst_s_n: Bool = Input(Bool())
  val init_s_n: Bool = Input(Bool())
  val clr_s: Bool = Input(Bool())
  val send_s: Bool = Input(Bool())
  val data_s: UInt = Input(UInt(parameter.width.W))
  val clr_sync_s: Bool = Output(Bool())
  val clr_in_prog_s: Bool = Output(Bool())
  val clr_cmplt_s: Bool = Output(Bool())
  val clk_d: Clock = Input(Clock())
  val rst_d_n: Bool = Input(Bool())
  val init_d_n: Bool = Input(Bool())
  val clr_d: Bool = Input(Bool())
  val prefill_d: Bool = Input(Bool())
  val clr_in_prog_d: Bool = Output(Bool())
  val clr_sync_d: Bool = Output(Bool())
  val clr_cmplt_d: Bool = Output(Bool())
  val data_avail_d: Bool = Output(Bool())
  val data_d: UInt = Output(UInt(parameter.width.W))
  val prefilling_d: Bool = Output(Bool())
  val test: Bool = Input(Bool())
}
