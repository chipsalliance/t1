// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_piped_mac

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_piped_mac._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_piped_mac(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "a_width" -> IntParam(p.aWidth),
          "b_width" -> IntParam(p.bWidth),
          "acc_width" -> IntParam(p.accWidth),
          "tc" -> IntParam(if (p.tc) 1 else 0),
          "pipe_reg" -> IntParam(p.pipeReg match {
            case "no_pipe"         => 0
            case "pipe_stage0"     => 1
            case "pipe_stage1"     => 2
            case "pipe_stage0_1"   => 3
            case "pipe_stage2"     => 4
            case "pipe_stage0_2"   => 5
            case "pipe_stage1_2"   => 6
            case "pipe_stage0_1_2" => 7
          }),
          "id_width" -> IntParam(p.idWidth),
          "no_pm" -> IntParam(if (p.noPm) 1 else 0),
          "op_iso_mode" -> IntParam(p.opIsoMode match {
            case "DW_lp_op_iso_mode" => 0
            case "none"              => 1
            case "and"               => 2
            case "or"                => 3
            case "prefer_and"        => 4
          })
        )
    )
