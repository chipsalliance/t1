// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_div_sat

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_div_sat._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_div_sat(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "a_width" -> IntParam(p.aWidth),
          "b_width" -> IntParam(p.bWidth),
          "q_width" -> IntParam(p.qWidth),
          "tc_mode" -> IntParam(if (p.tcMode) 1 else 0)
        )
    )
