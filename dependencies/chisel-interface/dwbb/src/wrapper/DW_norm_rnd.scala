// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_norm_rnd

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_norm_rnd._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_norm_rnd(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "a_width" -> IntParam(p.aWidth),
          "srch_wind" -> IntParam(p.srchWind),
          "exp_width" -> IntParam(p.expWidth),
          "b_width" -> IntParam(p.bWidth),
          "exp_ctr" -> IntParam(if (p.expCtr) 1 else 0)
        )
    )
