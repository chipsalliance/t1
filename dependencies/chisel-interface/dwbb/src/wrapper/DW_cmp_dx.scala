// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_cmp_dx

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_cmp_dx._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_cmp_dx(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap("width" -> IntParam(p.width), "p1_width" -> IntParam(p.p1Width))
    )
