// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_asymfifo_s2_sf

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_asymfifo_s2_sf._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_asymfifo_s2_sf(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "data_in_width" -> IntParam(p.dataInWidth),
          "data_out_width" -> IntParam(p.dataOutWidth),
          "depth" -> IntParam(p.depth),
          "push_ae_lvl" -> IntParam(p.pushAeLvl),
          "push_af_lvl" -> IntParam(p.pushAfLvl),
          "pop_ae_lvl" -> IntParam(p.popAeLvl),
          "pop_af_lvl" -> IntParam(p.popAfLvl),
          "err_mode" -> IntParam(p.errMode match {
            case "latched"   => 0
            case "unlatched" => 1
          }),
          "push_sync" -> IntParam(p.pushSync match {
            case "single_reg" => 0
            case "double_reg" => 1
            case "triple_reg" => 2
          }),
          "pop_sync" -> IntParam(p.popSync match {
            case "single_reg" => 0
            case "double_reg" => 1
            case "triple_reg" => 2
          }),
          "rst_mode" -> IntParam(p.rstMode match {
            case "async_with_mem" => 0
            case "sync_with_mem"  => 1
            case "async_wo_mem"   => 2
            case "sync_wo_mem"    => 3
          }),
          "byte_order" -> IntParam(p.byteOrder match {
            case "msb" => 0
            case "lsb" => 1
          })
        )
    )
