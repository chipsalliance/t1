// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_stackctl

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_stackctl._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_stackctl(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "depth" -> IntParam(p.depth),
          "err_mode" -> IntParam(p.errMode match {
            case "hold_until_reset"      => 0
            case "hold_until_next_clock" => 1
          }),
          "rst_mode" -> IntParam(p.rstMode match {
            case "async" => 0
            case "sync"  => 1
          })
        )
    )
