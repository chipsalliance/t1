// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_arb_rr

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_arb_rr._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_arb_rr(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "n" -> IntParam(p.n),
          "output_mode" -> IntParam(if (p.outputMode) 1 else 0),
          "index_mode" -> IntParam(p.indexMode)
        )
    )
