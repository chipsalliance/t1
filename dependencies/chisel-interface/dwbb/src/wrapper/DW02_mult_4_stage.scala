// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW02_mult_4_stage

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW02_mult_4_stage._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW02_mult_4_stage(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap("A_width" -> IntParam(p.aWidth), "B_width" -> IntParam(p.bWidth))
    )
