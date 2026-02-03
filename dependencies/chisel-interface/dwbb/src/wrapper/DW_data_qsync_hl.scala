// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_data_qsync_hl

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_data_qsync_hl._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_data_qsync_hl(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "width" -> IntParam(p.width),
          "clk_ratio" -> IntParam(p.clkRatio),
          "tst_mode" -> IntParam(if (p.tstMode) 1 else 0)
        )
    )
