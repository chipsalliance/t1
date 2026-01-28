// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_data_sync

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 8,
    pendMode: Boolean = true,
    ackDelay: Boolean = false,
    fSyncType: String = "pos_pos_sync",
    rSyncType: String = "pos_pos_sync",
    tstMode: Boolean = false,
    verifEn: Int = 1,
    sendMode: String = "single_src_domain_pulse"
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 1024).contains(width),
    "width must be between 1 and 1024"
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
    Range.inclusive(0, 4).contains(verifEn),
    "verifEn must be between 0 and 4"
  )
  require(
    Seq(
      "single_src_domain_pulse",
      "rising_dest_domain_pulse",
      "falling_dest_domain_pulse",
      "rising_falling_dest_domain_pulse"
    ).contains(sendMode),
    "sendMode must be one of 'single_src_domain_pulse', 'rising_dest_domain_pulse', 'falling_dest_domain_pulse', 'rising_falling_dest_domain_pulse'"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val clk_s: Clock = Input(Clock())
  val rst_s_n: Bool = Input(Bool())
  val init_s_n: Bool = Input(Bool())
  val send_s: Bool = Input(Bool())
  val data_s: UInt = Input(UInt(parameter.width.W))
  val empty_s: Bool = Output(Bool())
  val full_s: Bool = Output(Bool())
  val done_s: Bool = Output(Bool())
  val clk_d: Clock = Input(Clock())
  val rst_d_n: Bool = Input(Bool())
  val init_d_n: Bool = Input(Bool())
  val data_avail_d: Bool = Output(Bool())
  val data_d: UInt = Output(UInt(parameter.width.W))
  val test: Bool = Input(Bool())
}
