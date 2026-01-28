// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW03_pipe_reg

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW03_pipe_reg._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW03_pipe_reg(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "depth" -> IntParam(p.depth),
          "width" -> IntParam(p.width)
        )
    )
