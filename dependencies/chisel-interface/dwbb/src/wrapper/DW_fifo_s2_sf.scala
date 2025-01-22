// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_fifo_s2_sf

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_fifo_s2_sf._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_fifo_s2_sf(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "width" -> IntParam(p.width),
          "depth" -> IntParam(p.depth),
          "push_ae_lvl" -> IntParam(p.pushAELvl),
          "push_af_lvl" -> IntParam(p.pushAFLvl),
          "pop_ae_lvl" -> IntParam(p.popAELvl),
          "pop_af_lvl" -> IntParam(p.popAFLvl),
          "err_mode" -> IntParam(if (p.errMode) 1 else 0),
          "push_sync" -> IntParam(p.pushSync match {
            case "single_reg" => 1
            case "double_reg" => 2
            case "triple_reg" => 3
          }),
          "pop_sync" -> IntParam(p.popSync match {
            case "single_reg" => 1
            case "double_reg" => 2
            case "triple_reg" => 3
          }),
          "rst_mode" -> IntParam(p.rstMode match {
            case "async_with_mem" => 0
            case "sync_with_mem"  => 1
            case "async_wo_mem"   => 2
            case "sync_wo_mem"    => 3
          })
        )
    )
