// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_pulseack_sync

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    regEvent: Boolean = true,
    regAck: Boolean = true,
    ackDelay: Boolean = true,
    fSyncType: String = "pos_pos_sync",
    rSyncType: String = "pos_pos_sync",
    tstMode: String = "no_latch",
    verifEn: Int = 1
) extends SerializableModuleParameter {
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
  val event_s: Bool = Input(Bool())
  val busy_s: Bool = Output(Bool())
  val ack_s: Bool = Output(Bool())
  val clk_d: Clock = Input(Clock())
  val rst_d_n: Bool = Input(Bool())
  val init_d_n: Bool = Input(Bool())
  val event_d: Bool = Output(Bool())
  val test: Bool = Input(Bool())
}
