// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_asymfifoctl_2c_df

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_asymfifoctl_2c_df._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_asymfifoctl_2c_df(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "data_s_width" -> IntParam(p.dataSWidth),
          "data_d_width" -> IntParam(p.dataDWidth),
          "ram_depth" -> IntParam(p.ramDepth),
          "mem_mode" -> IntParam(p.memMode),
          "arch_type" -> IntParam(if (p.archType == "pipeline") 0 else 1),
          "f_sync_type" -> IntParam(
            p.fSyncType match {
              case "single_clock" => 0
              case "neg_pos_sync" => 1
              case "pos_pos_sync" => 2
              case "3_pos_sync"   => 3
              case "4_pos_sync"   => 4
            }
          ),
          "r_sync_type" -> IntParam(
            p.rSyncType match {
              case "single_clock" => 0
              case "neg_pos_sync" => 1
              case "pos_pos_sync" => 2
              case "3_pos_sync"   => 3
              case "4_pos_sync"   => 4
            }
          ),
          "byte_order" -> IntParam(p.byteOrder match {
            case "msb" => 0
            case "lsb" => 1
          }),
          "flush_value" -> IntParam(p.flushValue),
          "clk_ratio" -> IntParam(p.clkRatio),
          "ram_re_ext" -> IntParam(p.ramReExt match {
            case "single_pulse"     => 0
            case "extend_assertion" => 1
          }),
          "err_mode" -> IntParam(
            p.errMode match {
              case "sticky_error_flag"  => 0
              case "dynamic_error_flag" => 1
            }
          ),
          "tst_mode" -> IntParam(
            p.tstMode match {
              case "no_latch"        => 0
              case "latch"           => 1
              case "no_latch_no_err" => 2
              case "latch_no_err"    => 3
            }
          ),
          "verif_en" -> IntParam(p.verifEn)
        )
    )
