// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_exp2

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_exp2._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_exp2(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "op_width" -> IntParam(p.opWidth),
          "arch" -> IntParam(p.arch match {
            case "area"     => 0
            case "speed"    => 1
            case "obsolete" => 2
          }),
          "err_range" -> IntParam(p.errRange)
        )
    )
