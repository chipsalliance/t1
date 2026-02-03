// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_fir

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_fir._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_fir(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "data_in_width" -> IntParam(p.dataInWidth),
          "coef_width" -> IntParam(p.coefWidth),
          "data_out_width" -> IntParam(p.dataOutWidth),
          "order" -> IntParam(p.order)
        )
    )
