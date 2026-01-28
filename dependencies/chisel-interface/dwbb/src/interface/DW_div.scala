// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_div

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
    tcMode: Boolean = false,
    remMode: Boolean = true
) extends SerializableModuleParameter {
  require(aWidth >= 1, "aWidth must be greater than or equal to 1")
  require(
    bWidth >= 1,
    "bWidth must be greater than or equal to 1"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt(parameter.aWidth.W))
  val b: UInt = Input(UInt(parameter.bWidth.W))
  val quotient: UInt = Output(UInt(parameter.aWidth.W))
  val remainder: UInt = Output(UInt(parameter.bWidth.W))
  val divide_by_0: Bool = Output(Bool())
}
