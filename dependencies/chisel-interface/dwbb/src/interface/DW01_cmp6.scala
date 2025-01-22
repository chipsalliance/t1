// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW01_cmp6

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(width: Int = 32) extends SerializableModuleParameter {
  require(width >= 1, "width must be greater than or equal to 1")
}

class Interface(parameter: Parameter) extends Bundle {
  val A: UInt = Input(UInt(parameter.width.W))
  val B: UInt = Input(UInt(parameter.width.W))
  val TC: Bool = Input(Bool())
  val LT: Bool = Output(Bool())
  val GT: Bool = Output(Bool())
  val EQ: Bool = Output(Bool())
  val LE: Bool = Output(Bool())
  val GE: Bool = Output(Bool())
  val NE: Bool = Output(Bool())
}
