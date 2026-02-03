// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW01_bsh

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW01_bsh._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW01_bsh(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "A_width" -> IntParam(p.AWidth),
          "SH_width" -> IntParam(p.SHWidth)
        )
    )
