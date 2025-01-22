// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_squarep

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(width: Int = 8, verifEn: Int = 2)
    extends SerializableModuleParameter {
  require(width >= 1, "width must be greater than or equal to 1")
  require(
    Range.inclusive(0, 3).contains(verifEn),
    "verifEn must be in the range 0 to 3"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt(parameter.width.W))
  val tc: Bool = Input(Bool())
  val out0: UInt = Output(UInt((parameter.width * 2).W))
  val out1: UInt = Output(UInt((parameter.width * 2).W))
}
