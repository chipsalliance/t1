// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW01_mux_any

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW01_mux_any._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW01_mux_any(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "A_width" -> IntParam(p.aWidth),
          "SEL_width" -> IntParam(p.selWidth),
          "MUX_width" -> IntParam(p.muxWidth),
          "BAL_STR" -> IntParam(if (p.balStr) 1 else 0)
        )
    )
