// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_sqrt_seq

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 8,
    tcMode: Boolean = false,
    numCyc: Int = 3,
    rstMode: Boolean = false,
    inputMode: Boolean = true,
    outputMode: Boolean = true,
    earlyStart: Boolean = false
) extends SerializableModuleParameter {
  require(width >= 6, "width must be greater than or equal to 6")
  val rootWidth: Int = (width + 1) / 2
  require(
    Range.inclusive(3, rootWidth).contains(numCyc),
    s"numCyc must be between 3 and $rootWidth"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val hold: Bool = Input(Bool())
  val start: Bool = Input(Bool())
  val a: UInt = Input(UInt(parameter.width.W))
  val complete: Bool = Output(Bool())
  val root: UInt = Output(UInt(parameter.rootWidth.W))
}
