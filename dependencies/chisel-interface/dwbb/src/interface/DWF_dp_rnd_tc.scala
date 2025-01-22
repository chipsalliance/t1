// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DWF_dp_rnd_tc

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 16,
    lsb: Int = 8
) extends SerializableModuleParameter {
  require(width >= 1, "width must be greater than or equal to 1")
  require(
    Range.inclusive(1, width - 1).contains(lsb),
    "lsb must be in the range of 1 to width - 1"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt(parameter.width.W))
  val mode: UInt = Input(UInt(4.W))
  val DWF_dp_rnd: UInt = Output(UInt((parameter.width - parameter.lsb).W))
}
