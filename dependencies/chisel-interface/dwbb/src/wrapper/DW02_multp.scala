// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW02_multp

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW02_multp._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW02_multp(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "a_width" -> IntParam(p.aWidth),
          "b_width" -> IntParam(p.bWidth),
          "out_width" -> IntParam(p.outWidth),
          "verif_en" -> IntParam(p.verifEn)
        )
    )
