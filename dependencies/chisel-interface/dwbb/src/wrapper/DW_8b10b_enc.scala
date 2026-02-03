// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_8b10b_enc

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_8b10b_enc._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_8b10b_enc(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "bytes" -> IntParam(p.bytes),
          "k28_5_only" -> IntParam(if (p.k28P5Only) 1 else 0),
          "en_mode" -> IntParam(if (p.enMode) 1 else 0),
          "init_mode" -> IntParam(if (p.initMode) 1 else 0),
          "rst_mode" -> IntParam(if (p.rstMode) 1 else 0),
          "op_iso_mode" -> IntParam(p.opIsoMode match {
            case "DW_lp_op_iso_mode" => 0
            case "none"              => 1
            case "and"               => 2
            case "or"                => 3
            case "prefer_and"        => 4
          })
        )
    )
