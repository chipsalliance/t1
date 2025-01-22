// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW02_prod_sum1

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    aWidth: Int = 8,
    bWidth: Int = 8,
    sumWidth: Int = 16
) extends SerializableModuleParameter {
  require(aWidth >= 1, "aWidth must be greater than or equal to 1")
  require(bWidth >= 1, "aWidth must be greater than or equal to 1")
  require(sumWidth >= 1, "sumWidth must be greater than or equal to 1")
}

class Interface(parameter: Parameter) extends Bundle {
  val A: UInt = Input(UInt(parameter.aWidth.W))
  val B: UInt = Input(UInt(parameter.bWidth.W))
  val C: UInt = Input(UInt(parameter.sumWidth.W))
  val TC: Bool = Input(Bool())
  val SUM: UInt = Output(UInt(parameter.sumWidth.W))
}
