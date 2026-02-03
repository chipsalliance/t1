// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_fifo_2c_df

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_fifo_2c_df._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_fifo_2c_df(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "width" -> IntParam(p.width),
          "ram_depth" -> IntParam(p.ramDepth),
          "mem_mode" -> IntParam(p.memMode),
          "f_sync_type" -> IntParam(p.fSyncType match {
            case "single_clock" => 0
            case "neg_pos_sync" => 1
            case "pos_pos_sync" => 2
            case "3_pos_sync"   => 3
            case "4_pos_sync"   => 4
          }),
          "r_sync_type" -> IntParam(p.rSyncType match {
            case "single_clock" => 0
            case "neg_pos_sync" => 1
            case "pos_pos_sync" => 2
            case "3_pos_sync"   => 3
            case "4_pos_sync"   => 4
          }),
          "clk_ratio" -> IntParam(p.clkRatio),
          "rst_mode" -> IntParam(if (p.rstMode) 1 else 0),
          "err_mode" -> IntParam(if (p.errMode) 1 else 0),
          "tst_mode" -> IntParam(p.tstMode match {
            case "no_latch" => 0
            case "ff"       => 1
            case "latch"    => 2
          }),
          "verif_en" -> IntParam(p.verifEn),
          "clr_dual_domain" -> IntParam(if (p.clrDualDomain) 1 else 0),
          "arch_type" -> IntParam(p.archType match {
            case "rtl"  => 0
            case "lpwr" => 1
          })
        )
    )
