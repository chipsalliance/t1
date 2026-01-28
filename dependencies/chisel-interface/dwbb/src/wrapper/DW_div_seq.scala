// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_div_seq

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_div_seq._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_div_seq(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "a_width" -> IntParam(p.aWidth),
          "b_width" -> IntParam(p.bWidth),
          "tc_mode" -> IntParam(if (p.tcMode) 1 else 0),
          "num_cyc" -> IntParam(p.numCyc),
          "rst_mode" -> IntParam(if (p.rstMode) 1 else 0),
          "input_mode" -> IntParam(if (p.inputMode) 1 else 0),
          "output_mode" -> IntParam(if (p.outputMode) 1 else 0),
          "early_start" -> IntParam(if (p.earlyStart) 1 else 0)
        )
    )
