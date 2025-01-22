// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_pl_reg

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_pl_reg._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_pl_reg(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "width" -> IntParam(p.width),
          "in_reg" -> IntParam(if (p.inReg) 1 else 0),
          "stages" -> IntParam(p.stages),
          "out_reg" -> IntParam(if (p.outReg) 1 else 0),
          "rst_mode" -> IntParam(p.rstMode match {
            case "async" => 0
            case "sync"  => 1
          })
        )
    )
