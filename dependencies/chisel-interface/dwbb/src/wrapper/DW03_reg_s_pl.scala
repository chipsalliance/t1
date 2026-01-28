// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW03_reg_s_pl

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW03_reg_s_pl._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW03_reg_s_pl(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "width" -> IntParam(p.width),
          "reset_value" -> IntParam(p.resetValue)
        )
    )
