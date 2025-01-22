// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_div_sat

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    aWidth: Int = 32,
    bWidth: Int = 16,
    qWidth: Int = 32,
    tcMode: Boolean = false
) extends SerializableModuleParameter {
  require(aWidth >= 2, "aWidth must be greater than or equal to 2")
  require(bWidth >= 2, "bWidth must be greater than or equal to 2")
  require(
    Range.inclusive(2, aWidth).contains(qWidth),
    "qWidth must be between 2 and aWidth"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt(parameter.aWidth.W))
  val b: UInt = Input(UInt(parameter.bWidth.W))
  val quotient: UInt = Output(UInt(parameter.aWidth.W))
  val divide_by_0: Bool = Output(Bool())
}
