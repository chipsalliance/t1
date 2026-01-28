// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_inv_sqrt

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_inv_sqrt._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_inv_sqrt(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "a_width" -> IntParam(p.aWidth),
          "prec_control" -> IntParam(p.precControl)
        )
    )
