// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_fp_exp

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_fp_exp._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_fp_exp(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "sig_width" -> IntParam(p.sigWidth),
          "exp_width" -> IntParam(p.expWidth),
          "ieee_compliance" -> IntParam(if (p.ieeeCompliance) 1 else 0),
          "arch" -> IntParam(
            p.arch match {
              case "area"     => 0
              case "speed"    => 1
              case "obsolete" => 2
            }
          )
        )
    )