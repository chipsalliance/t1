// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_ram_r_w_s_lat

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_ram_r_w_s_lat._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_ram_r_w_s_lat(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "data_width" -> IntParam(p.dataWidth),
          "depth" -> IntParam(p.depth)
        )
    )
