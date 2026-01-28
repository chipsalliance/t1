// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_sincos

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_sincos._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_sincos(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "A_width" -> IntParam(p.aWidth),
          "WAVE_width" -> IntParam(p.waveWidth),
          "arch" -> IntParam(p.arch match {
            case "area"  => 0
            case "speed" => 1
          }),
          "err_range" -> IntParam(p.errRange)
        )
    )
