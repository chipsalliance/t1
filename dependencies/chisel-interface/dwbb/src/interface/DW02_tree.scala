// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW02_tree

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    numInputs: Int = 2,
    inputWidth: Int = 2,
    verifEn: Boolean = true
) extends SerializableModuleParameter {
  require(numInputs >= 1, "numInputs must be greater than or equal to 1")
  require(inputWidth >= 1, "inputWidth must be greater than or equal to 1")
}

class Interface(parameter: Parameter) extends Bundle {
  val INPUT: UInt = Input(UInt((parameter.numInputs * parameter.inputWidth).W))
  val OUT0: UInt = Output(UInt(parameter.inputWidth.W))
  val OUT1: UInt = Output(UInt(parameter.inputWidth.W))
}
