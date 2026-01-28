// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW03_bictr_scnto

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 12,
    countTo: Int = 12
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 30).contains(width),
    "width must be between 1 and 30"
  )
  require(
    Range.inclusive(1, math.pow(2, width - 1).toInt).contains(countTo),
    s"countTo must be between 1 and ${math.pow(2, width - 1).toInt}"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val data: UInt = Input(UInt(parameter.width.W))
  val up_dn: Bool = Input(Bool())
  val load: Bool = Input(Bool())
  val cen: Bool = Input(Bool())
  val clk: Clock = Input(Clock())
  val reset: Bool = Input(Bool())
  val count: UInt = Output(UInt(parameter.width.W))
  val tercnt: Bool = Output(Bool())
}
