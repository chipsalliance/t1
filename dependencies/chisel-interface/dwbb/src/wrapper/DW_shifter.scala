// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_shifter

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_shifter._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_shifter(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "data_width" -> IntParam(p.dataWidth),
          "sh_width" -> IntParam(p.shWidth),
          "inv_mode" -> IntParam(p.invMode match {
            case "input_normal_output_0"   => 0
            case "input_normal_output_1"   => 1
            case "input_inverted_output_0" => 2
            case "input_inverted_output_1" => 3
          })
        )
    )
