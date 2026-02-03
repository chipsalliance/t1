// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_fifoctl_2c_df

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
                      width: Int = 8,
                      ramDepth: Int = 8,
                      retimingDataOut: Boolean = false,
                      retimingReadAddr: Boolean = false,
                      retimingWriteIF: Boolean = false,
                      fSyncType: String = "pos_pos_sync",
                      rSyncType: String = "pos_pos_sync",
                      clkRatio: Int = 1,
                      ramReExt: Boolean = false,
                      errMode: Boolean = false,
                      tstMode: String = "no_latch",
                      verifEn: Int = 1,
                      clrDualDomain: Boolean = true,
                      archType: String = "rtl"
                    ) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 1024).contains(width),
    "width must be between 1 and 1024"
  )
  require(
    Range.inclusive(4, 1024).contains(ramDepth),
    "width must be between 4 and 1024"
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
    Range.inclusive(-7, 7).contains(clkRatio),
    "width must be between -7 and 7"
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
    Seq("rtl", "lpwr").contains(archType),
    "archType must be one of 'rtl', 'lpwr'"
  )
  val memMode: Int =
    Seq(retimingDataOut, retimingReadAddr, retimingWriteIF) match {
      case Seq(false, false, false) => 0
      case Seq(true, false, false)  => 1
      case Seq(false, true, false)  => 2
      case Seq(true, true, false)   => 3
      case Seq(false, false, true)  => 4
      case Seq(true, false, true)   => 5
      case Seq(false, true, true)   => 6
      case Seq(true, true, true)    => 7
    }
  require(
    (clkRatio == 1) && (memMode == 0 || memMode == 1),
    "clkRatio must be the default value 1 when memMode is 0 or 1"
  )
  val effDepth: Int = memMode match {
    case 0 => ramDepth + 1
    case 1 => ramDepth + 2
    case 2 => ramDepth + 2
    case 3 => ramDepth + 3
    case 4 => ramDepth + 1
    case 5 => ramDepth + 2
    case 6 => ramDepth + 2
    case 7 => ramDepth + 3
    case _ => throw new IllegalArgumentException(s"Invalid memMode: $memMode")
  }
  val fifoWrdCnt: Int = log2Ceil(effDepth + 1)
  val ramAddrWidth: Int = log2Ceil(ramDepth)
  val ramWrdCnt: Int = ramAddrWidth + 1
}

class Interface(parameter: Parameter) extends Bundle {
  val clk_s: Clock = Input(Clock())
  val rst_s_n: Bool = Input(Bool())
  val init_s_n: Bool = Input(Bool())
  val clr_s: Bool = Input(Bool())
  val ae_level_s: UInt = Input(UInt(parameter.ramWrdCnt.W))
  val af_level_s: UInt = Input(UInt(parameter.ramWrdCnt.W))
  val push_s_n: Bool = Input(Bool())
  val clr_sync_s: Bool = Output(Bool())
  val clr_in_prog_s: Bool = Output(Bool())
  val clr_cmplt_s: Bool = Output(Bool())
  val wr_en_s_n: Bool = Output(Bool())
  val wr_addr_s: UInt = Output(UInt(parameter.ramAddrWidth.W))
  val fifo_word_cnt_s: UInt = Output(UInt(parameter.fifoWrdCnt.W))
  val word_cnt_s: UInt = Output(UInt(parameter.ramWrdCnt.W))
  val fifo_empty_s: Bool = Output(Bool())
  val empty_s: Bool = Output(Bool())
  val almost_empty_s: Bool = Output(Bool())
  val half_full_s: Bool = Output(Bool())
  val almost_full_s: Bool = Output(Bool())
  val full_s: Bool = Output(Bool())
  val error_s: Bool = Output(Bool())
  val clk_d: Clock = Input(Clock())
  val rst_d_n: Bool = Input(Bool())
  val init_d_n: Bool = Input(Bool())
  val clr_d: Bool = Input(Bool())
  val ae_level_d: UInt = Input(UInt(parameter.ramWrdCnt.W))
  val af_level_d: UInt = Input(UInt(parameter.ramWrdCnt.W))
  val pop_d_n: Bool = Input(Bool())
  val rd_data_d: UInt = Input(UInt(parameter.width.W))
  val clr_sync_d: Bool = Output(Bool())
  val clr_in_prog_d: Bool = Output(Bool())
  val clr_cmplt_d: Bool = Output(Bool())
  val ram_re_d_n: Bool = Output(Bool())
  val rd_addr_d: UInt = Output(UInt(parameter.ramAddrWidth.W))
  val data_d: UInt = Output(UInt(parameter.width.W))
  val word_cnt_d: UInt = Output(UInt(parameter.fifoWrdCnt.W))
  val ram_word_cnt_d: UInt = Output(UInt(parameter.ramWrdCnt.W))
  val empty_d: Bool = Output(Bool())
  val almost_empty_d: Bool = Output(Bool())
  val half_full_d: Bool = Output(Bool())
  val almost_full_d: Bool = Output(Bool())
  val full_d: Bool = Output(Bool())
  val error_d: Bool = Output(Bool())
  val test: UInt = Output(UInt(parameter.width.W))
}
