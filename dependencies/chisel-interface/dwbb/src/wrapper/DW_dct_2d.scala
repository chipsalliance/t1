// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_dct_2d

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_dct_2d._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_dct_2d(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "n" -> IntParam(p.n),
          "bpp" -> IntParam(p.bpp),
          "reg_out" -> IntParam(if (p.regOut) 1 else 0),
          "tc_mode" -> IntParam(if (p.tcMode) 1 else 0),
          "rt_mode" -> IntParam(if (p.rtMode) 1 else 0),
          "idct_mode" -> IntParam(if (p.idctMode) 1 else 0),
          "co_a" -> IntParam(p.coA),
          "co_b" -> IntParam(p.coB),
          "co_c" -> IntParam(p.coC),
          "co_d" -> IntParam(p.coD),
          "co_e" -> IntParam(p.coE),
          "co_f" -> IntParam(p.coF),
          "co_g" -> IntParam(p.coG),
          "co_h" -> IntParam(p.coH),
          "co_i" -> IntParam(p.coI),
          "co_j" -> IntParam(p.coJ),
          "co_k" -> IntParam(p.coK),
          "co_l" -> IntParam(p.coL),
          "co_m" -> IntParam(p.coM),
          "co_n" -> IntParam(p.coN),
          "co_o" -> IntParam(p.coO),
          "co_p" -> IntParam(p.coP)
        )
    )
