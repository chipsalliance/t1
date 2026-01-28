// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_inv_sqrt

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(aWidth: Int = 10, precControl: Int = 0)
    extends SerializableModuleParameter {
  require(aWidth >= 2, "aWidth must be greater than or equal to 2")
  require(
    Range.inclusive(0, (aWidth - 2) / 2).contains(precControl),
    "precControl must be between 0 and (aWidth - 2)/2"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt(parameter.aWidth.W))
  val b: UInt = Output(UInt(parameter.aWidth.W))
  val t: Bool = Output(Bool())
}
