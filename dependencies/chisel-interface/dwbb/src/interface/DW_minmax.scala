// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_minmax

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(width: Int = 4, numInputs: Int = 8)
    extends SerializableModuleParameter {
  require(width >= 1, "width must be greater than or equal to 1")
  require(numInputs >= 2, "numInputs must be greater than or equal to 2")
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt((parameter.numInputs * parameter.width).W))
  val tc: Bool = Input(Bool())
  val min_max: Bool = Input(Bool())
  val value: UInt = Output(UInt(parameter.width.W))
  val index: UInt = Output(UInt(log2Ceil(parameter.width).W))
}
