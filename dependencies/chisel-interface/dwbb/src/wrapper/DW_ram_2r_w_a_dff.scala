// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_ram_2r_w_a_dff

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_ram_2r_w_a_dff._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_ram_2r_w_a_dff(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "data_width" -> IntParam(p.dataWidth),
          "depth" -> IntParam(p.depth),
          "rst_mode" -> IntParam(if (p.rstMode) 1 else 0)
        )
    )
