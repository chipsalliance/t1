// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW02_mult_3_stage

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(aWidth: Int = 16, bWidth: Int = 16)
    extends SerializableModuleParameter {
  require(aWidth >= 1, "aWidth must be greater than or equal to 1")
  require(bWidth >= 1, "bWidth must be greater than or equal to 1")
}

class Interface(parameter: Parameter) extends Bundle {
  val A: UInt = Input(UInt(parameter.aWidth.W))
  val B: UInt = Input(UInt(parameter.bWidth.W))
  val TC: Bool = Input(Bool())
  val CLK: Clock = Input(Clock())
  val PRODUCT: UInt = Output(UInt((parameter.aWidth + parameter.bWidth).W))
}
