// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_fp_i2flt

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_fp_i2flt._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_fp_i2flt(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "sig_width" -> IntParam(p.sigWidth),
          "exp_width" -> IntParam(p.expWidth),
          "isize" -> IntParam(p.iSize),
          "isign" -> IntParam(if (p.iSign) 1 else 0)
        )
    )