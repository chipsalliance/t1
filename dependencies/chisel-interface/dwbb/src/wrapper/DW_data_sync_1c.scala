// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_data_sync_1c

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_data_sync_1c._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_data_sync_1c(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "width" -> IntParam(p.width),
          "f_sync_type" -> IntParam(p.fSyncType match {
            case "single_clock" => 0
            case "neg_pos_sync" => 1
            case "pos_pos_sync" => 2
            case "3_pos_sync"   => 3
            case "4_pos_sync"   => 4
          }),
          "filt_size" -> IntParam(p.filtSize),
          "tst_mode" -> IntParam(if (p.tstMode) 1 else 0),
          "verif_en" -> IntParam(p.verifEn)
        )
    )
