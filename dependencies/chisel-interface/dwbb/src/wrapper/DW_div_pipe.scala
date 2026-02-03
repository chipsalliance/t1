// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_div_pipe

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_div_pipe._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_div_pipe(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "a_width" -> IntParam(p.aWidth),
          "b_width" -> IntParam(p.bWidth),
          "tc_mode" -> IntParam(if (p.tcMode) 1 else 0),
          "rem_mode" -> IntParam(if (p.remMode) 1 else 0),
          "num_stages" -> IntParam(p.numStages),
          "stall_mode" -> IntParam(if (p.stallMode) 1 else 0),
          "rst_mode" -> IntParam(p.rstMode match {
            case "none"  => 0
            case "async" => 1
            case "sync"  => 2
          }),
          "op_iso_mode" -> IntParam(p.opIsoMode match {
            case "DW_lp_op_iso_mode" => 0
            case "none"              => 1
            case "and"               => 2
            case "or"                => 3
            case "prefer_and"        => 4
          })
        )
    )
