// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_asymdata_inbuf

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_asymdata_inbuf._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_asymdata_inbuf(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "in_width" -> IntParam(p.inWidth),
          "out_width" -> IntParam(p.outWidth),
          "err_mode" -> IntParam(p.errMode match {
            case "sticky_error_flag"  => 0
            case "dynamic_error_flag" => 1
          }),
          "byte_order" -> IntParam(p.byteOrder match {
            case "msb" => 0
            case "lsb" => 1
          }),
          "flush_value" -> IntParam(p.flushValue)
        )
    )
