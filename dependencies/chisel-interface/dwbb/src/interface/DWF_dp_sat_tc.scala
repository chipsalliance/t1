// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DWF_dp_sat_tc

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 16,
    size: Int = 8
) extends SerializableModuleParameter {
  require(width >= 1, "width must be greater than or equal to 1")
  require(
    size > 1 && size < width,
    "size must be greater than 1 and less than width"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt(parameter.width.W))
  val DWF_dp_sat: UInt = Output(UInt(parameter.size.W))
}
