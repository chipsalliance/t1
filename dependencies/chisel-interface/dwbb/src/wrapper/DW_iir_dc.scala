// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_iir_dc

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_iir_dc._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_iir_dc(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "data_in_width" -> IntParam(p.dataInWidth),
          "data_out_width" -> IntParam(p.dataOutWidth),
          "frac_data_out_width" -> IntParam(p.fracDataOutWidth),
          "feedback_width" -> IntParam(p.feedbackWidth),
          "max_coef_width" -> IntParam(p.maxCoefWidth),
          "frac_coef_width" -> IntParam(p.fracCoefWidth),
          "saturation_mode" -> IntParam(if (p.saturationMode) 1 else 0),
          "out_reg" -> IntParam(if (p.outReg) 1 else 0)
        )
    )
