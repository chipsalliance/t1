// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_gray_sync

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_gray_sync._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_gray_sync(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "width" -> IntParam(p.width),
          "offset" -> IntParam(p.offset),
          "reg_count_d" -> IntParam(if (p.regCountD) 1 else 0),
          "f_sync_type" -> IntParam(p.fSyncType match {
            case "single_clock" => 0
            case "neg_pos_sync" => 1
            case "pos_pos_sync" => 2
            case "3_pos_sync"   => 3
            case "4_pos_sync"   => 4
          }),
          "tst_mode" -> IntParam(p.tstMode match {
            case "no_latch" => 0
            case "ff"       => 1
            case "latch"    => 2
          }),
          "verif_en" -> IntParam(p.verifEn),
          "pipe_delay" -> IntParam(p.pipeDelay),
          "reg_count_s" -> IntParam(if (p.regCountS) 1 else 0),
          "reg_offset_count_s" -> IntParam(if (p.regOffsetCountS) 1 else 0)
        )
    )
