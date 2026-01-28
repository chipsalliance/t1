// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW02_prod_sum

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    aWidth: Int = 4,
    bWidth: Int = 5,
    numInputs: Int = 4,
    sumWidth: Int = 12
) extends SerializableModuleParameter {
  require(aWidth >= 1, "aWidth must be greater than or equal to 1")
  require(bWidth >= 1, "bWidth must be greater than or equal to 1")
  require(numInputs >= 1, "numInputs must be greater than or equal to 1")
  require(sumWidth >= 1, "sumWidth must be greater than or equal to 1")
}

class Interface(parameter: Parameter) extends Bundle {
  val A: UInt = Input(UInt((parameter.aWidth * parameter.numInputs).W))
  val B: UInt = Input(UInt((parameter.bWidth * parameter.numInputs).W))
  val TC: Bool = Input(Bool())
  val SUM: UInt = Output(UInt(parameter.sumWidth.W))
}
