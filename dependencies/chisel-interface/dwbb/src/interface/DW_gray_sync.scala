// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_gray_sync

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 8,
    offset: Int = 0,
    regCountD: Boolean = true,
    fSyncType: String = "2_stage_syn_ps",
    tstMode: String = "no_latch",
    verifEn: Int = 2,
    pipeDelay: Int = 0,
    regCountS: Boolean = true,
    regOffsetCountS: Boolean = true
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 1024).contains(width),
    "width must be between 1 and 1024"
  )
  require(
    Range.inclusive(0, math.pow(2, (width - 1)).toInt - 1).contains(offset),
    "offset must be between 0 and 2^(width - 1) - 1"
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
    Seq("no_latch", "ff", "latch").contains(tstMode),
    "tstMode must be one of 'no_latch', 'ff', 'latch'"
  )
  require(
    Range.inclusive(0, 4).contains(verifEn),
    "verifEn must be between 0 and 4"
  )
  require(
    Range.inclusive(0, 2).contains(pipeDelay),
    "pipeDelay must be between 0 and 2"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val clk_s: Clock = Input(Clock())
  val rst_s_n: Bool = Input(Bool())
  val init_s_n: Bool = Input(Bool())
  val en_s: Bool = Input(Bool())
  val count_s: UInt = Output(UInt(parameter.width.W))
  val offset_count_s: UInt = Output(UInt(parameter.width.W))
  val clk_d: Clock = Input(Clock())
  val rst_d_n: Bool = Input(Bool())
  val init_d_n: Bool = Input(Bool())
  val count_d: UInt = Output(UInt(parameter.width.W))
  val test: Bool = Input(Bool())
}
