// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_sync

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 8,
    fSyncType: String = "pos_pos_sync",
    tstMode: String = "no_latch",
    verifEn: Int = 1
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
    Seq("no_latch", "ff", "latch").contains(tstMode),
    "tstMode must be one of 'no_latch', 'ff', 'latch'"
  )
  require(
    Range.inclusive(0, 4).contains(verifEn),
    "verifEn must be between 0 and 4"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val data_s: UInt = Input(UInt(parameter.width.W))
  val clk_d: Clock = Input(Clock())
  val rst_d_n: Bool = Input(Bool())
  val init_d_n: Bool = Input(Bool())
  val test: Bool = Input(Bool())
  val data_d: UInt = Output(UInt(parameter.width.W))
}
