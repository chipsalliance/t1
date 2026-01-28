// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_arb_fcfs

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_arb_fcfs._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_arb_fcfs(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "n" -> IntParam(p.n),
          "park_mode" -> IntParam(if (p.parkMode) 1 else 0),
          "park_index" -> IntParam(p.parkIndex),
          "output_mode" -> IntParam(if (p.outputMode) 1 else 0)
        )
    )
