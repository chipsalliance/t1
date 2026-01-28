// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW04_par_gen

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW04_par_gen._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW04_par_gen(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "width" -> IntParam(p.width),
          "par_type" -> IntParam(if (p.parType) 1 else 0)
        )
    )
