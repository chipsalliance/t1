// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_asymfifoctl_s1_df

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_asymfifoctl_s1_df._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_asymfifoctl_s1_df(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "data_in_width" -> IntParam(p.dataInWidth),
          "data_out_width" -> IntParam(p.dataOutWidth),
          "depth" -> IntParam(p.depth),
          "err_mode" -> IntParam(p.errMode),
          "rst_mode" -> IntParam(if (p.rstMode == "sync") 1 else 0),
          "byte_order" -> IntParam(if (p.byteOrder) 1 else 0)
        )
    )
