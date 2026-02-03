// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW03_reg_s_pl

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 8,
    resetValue: Int = 0
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 31).contains(width),
    "width must be between 1 and 32"
  )
  require(
    Range.inclusive(0, math.pow(2, width).toInt - 1).contains(resetValue),
    s"resetValue must be between 0 and ${math.pow(2, width).toInt - 1}"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val d: UInt = Input(UInt(parameter.width.W))
  val enable: Bool = Input(Bool())
  val reset_N: Bool = Input(Bool())
  val q: UInt = Output(UInt(parameter.width.W))
}
