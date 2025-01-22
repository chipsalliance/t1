// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW02_prod_sum

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW02_prod_sum._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW02_prod_sum(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "A_width" -> IntParam(p.aWidth),
          "B_width" -> IntParam(p.bWidth),
          "num_inputs" -> IntParam(p.numInputs),
          "SUM_width" -> IntParam(p.sumWidth)
        )
    )
