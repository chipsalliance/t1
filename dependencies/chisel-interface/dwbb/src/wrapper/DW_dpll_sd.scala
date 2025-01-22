// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_dpll_sd

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_dpll_sd._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_dpll_sd(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "width" -> IntParam(p.width),
          "divisor" -> IntParam(p.divisor),
          "gain" -> IntParam(p.gain),
          "filter" -> IntParam(p.filter),
          "windows" -> IntParam(p.windows)
        )
    )
