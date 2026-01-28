// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_8b10b_unbal

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_8b10b_unbal._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_8b10b_unbal(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "k28_5_mode" -> IntParam(if (p.k28P5Mode) 1 else 0)
        )
    )
