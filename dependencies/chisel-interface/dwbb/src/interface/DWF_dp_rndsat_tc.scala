// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DWF_dp_rndsat_tc

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 16,
    msb: Int = 11,
    lsb: Int = 4
) extends SerializableModuleParameter {
  require(width >= 1, "width must be greater than or equal to 1")
  require(
    msb > lsb && msb <= width - 1,
    "msb must be greater than lsb and less than or equal to width - 1"
  )
  require(
    lsb >= 1 && lsb < msb,
    "lsb must be greater than or equal to 1 and less than msb"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt(parameter.width.W))
  val mode: UInt = Input(UInt(4.W))
  val DWF_dp_rndsat: UInt = Output(UInt((parameter.msb - parameter.lsb + 1).W))
}
