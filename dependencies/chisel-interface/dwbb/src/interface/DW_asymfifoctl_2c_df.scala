// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_asymfifoctl_2c_df

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    dataSWidth: Int = 16,
    dataDWidth: Int = 8,
    ramDepth: Int = 8,
    memMode: Int = 3,
    archType: String = "pipeline",
    fSyncType: String = "pos_pos_sync",
    rSyncType: String = "pos_pos_sync",
    byteOrder: String = "msb",
    flushValue: Int = 0,
    clkRatio: Int = 1,
    ramReExt: String = "single_pulse",
    errMode: String = "sticky_error_flag",
    tstMode: String = "no_latch",
    verifEn: Int = 1
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 1024).contains(dataSWidth),
    "dataSWidth must be between 1 and 1024"
  )
  require(
    Range.inclusive(1, 1024).contains(dataDWidth),
    "dataDWidth must be between 1 and 1024"
  )
  require(
    Range.inclusive(4, 1024).contains(ramDepth),
    "ramDepth must be between 4 and 1024"
  )
  require(
    Range.inclusive(0, 7).contains(memMode),
    "memMode must be between 0 and 7"
  )
  require(
    Seq("pipeline", "regfile").contains(archType),
    "archType must be one of 'pipeline', 'regfile'"
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
    Seq("msb", "lsb").contains(byteOrder),
    "byteOrder must be one of 'msb', 'lsb'"
  )
  require(Seq(0, 1).contains(flushValue), "flushValue must be 0 or 1")
  require(
    Seq("single_pulse", "extend_assertion").contains(ramReExt),
    "ramReExt must be one of 'single_pulse', 'extend_assertion'"
  )
  require(
    Range.inclusive(-7, 7).contains(clkRatio),
    "clkRatio must be between -7 and 7"
  )
  require(
    Seq("sticky_error_flag", "dynamic_error_flag").contains(errMode),
    "errMode must be one of 'sticky_error_flag', 'dynamic_error_flag'"
  )
  require(
    Seq("no_latch", "ff", "latch").contains(tstMode),
    "tstMode must be one of 'no_latch', 'ff', 'latch'"
  )
  require(
    Range.inclusive(0, 4).contains(verifEn),
    "verifEn must be between 0 and 4"
  )
  val lclAddrWidth: Int = log2Ceil(ramDepth)
  val lclCntWidth: Int = log2Ceil(ramDepth + 1)
  val effDepth: Int = if (memMode == 0 || memMode == 4) {
    ramDepth + 1
  } else if (memMode == 3 || memMode == 7) {
    ramDepth + 3
  } else {
    ramDepth + 2
  }
  val lclFIFOCntWidth: Int = log2Ceil(effDepth + 1)
  val lclRamWidth: Int = math.max(dataSWidth, dataDWidth)
}

class Interface(parameter: Parameter) extends Bundle {
  val clk_s: Clock = Input(Clock())
  val rst_s_n: Bool = Input(Bool())
  val init_s_n: Bool = Input(Bool())
  val clr_s: Bool = Input(Bool())
  val ae_level_s: UInt = Input(UInt(parameter.lclCntWidth.W))
  val af_level_s: UInt = Input(UInt(parameter.lclCntWidth.W))
  val push_s_n: Bool = Input(Bool())
  val flush_s_n: Bool = Input(Bool())
  val data_s: UInt = Input(UInt(parameter.dataSWidth.W))
  val clr_sync_s: Bool = Output(Bool())
  val clr_in_prog_s: Bool = Output(Bool())
  val clr_cmplt_s: Bool = Output(Bool())
  val wr_en_s_n: Bool = Output(Bool())
  val wr_addr_s: UInt = Output(UInt(parameter.lclAddrWidth.W))
  val wr_data_s: UInt = Output(UInt(parameter.lclRamWidth.W))
  val inbuf_part_wd_s: Bool = Output(Bool())
  val inbuf_full_s: Bool = Output(Bool())
  val fifo_word_cnt_s: UInt = Output(UInt(parameter.lclFIFOCntWidth.W))
  val word_cnt_s: UInt = Output(UInt(parameter.lclCntWidth.W))
  val fifo_empty_s: Bool = Output(Bool())
  val empty_s: Bool = Output(Bool())
  val almost_empty_s: Bool = Output(Bool())
  val half_full_s: Bool = Output(Bool())
  val almost_full_s: Bool = Output(Bool())
  val ram_full_s: Bool = Output(Bool())
  val push_error_s: Bool = Output(Bool())
  val clk_d: Clock = Input(Clock())
  val rst_d_n: Bool = Input(Bool())
  val init_d_n: Bool = Input(Bool())
  val clr_d: Bool = Input(Bool())
  val ae_level_d: UInt = Input(UInt(parameter.lclCntWidth.W))
  val af_level_d: UInt = Input(UInt(parameter.lclCntWidth.W))
  val pop_d_n: Bool = Input(Bool())
  val rd_data_d: UInt = Input(UInt(parameter.lclRamWidth.W))
  val clr_sync_d: Bool = Output(Bool())
  val clr_in_prog_d: Bool = Output(Bool())
  val clr_cmplt_d: Bool = Output(Bool())
  val ram_re_d_n: Bool = Output(Bool())
  val rd_addr_d: UInt = Output(UInt(parameter.lclAddrWidth.W))
  val data_d: UInt = Output(UInt(parameter.dataDWidth.W))
  val outbuf_part_wd_d: Bool = Output(Bool())
  val word_cnt_d: UInt = Output(UInt(parameter.lclFIFOCntWidth.W))
  val ram_word_cnt_d: UInt = Output(UInt(parameter.lclCntWidth.W))
  val empty_d: Bool = Output(Bool())
  val almost_empty_d: Bool = Output(Bool())
  val half_full_d: Bool = Output(Bool())
  val almost_full_d: Bool = Output(Bool())
  val full_d: Bool = Output(Bool())
  val pop_error_d: Bool = Output(Bool())
  val test: Bool = Input(Bool())
}
