// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_asymfifoctl_s2_sf

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_asymfifoctl_s2_sf._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_asymfifoctl_s2_sf(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "data_in_width" -> IntParam(p.dataInWidth),
          "data_out_width" -> IntParam(p.dataOutWidth),
          "depth" -> IntParam(p.depth),
          "push_ae_level" -> IntParam(p.pushAeLevel),
          "push_af_level" -> IntParam(p.pushAfLevel),
          "pop_ae_level" -> IntParam(p.popAeLevel),
          "pop_af_level" -> IntParam(p.popAfLevel),
          "err_mode" -> IntParam(p.errMode match {
            case "latched"   => 0
            case "unlatched" => 1
          }),
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
            case "async" => 0
            case "sync"  => 1
          }),
          "byte_order" -> IntParam(p.byteOrder match {
            case "msb" => 0
            case "lsb" => 1
          })
        )
    )
