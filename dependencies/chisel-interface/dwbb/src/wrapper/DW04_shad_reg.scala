// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW04_shad_reg

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW04_shad_reg._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW04_shad_reg(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "width" -> IntParam(p.width),
          "bld_shad_reg" -> IntParam(if (p.bldShadReg) 1 else 0)
        )
    )
