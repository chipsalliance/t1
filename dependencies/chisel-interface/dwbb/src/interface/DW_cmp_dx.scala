// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_cmp_dx

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(width: Int = 4, p1Width: Int = 2)
    extends SerializableModuleParameter {
  require(width >= 4, "width must be greater than or equal to 4")
  require(
    Range.inclusive(2, width - 2).contains(p1Width),
    s"p1Width must be between 2 and ${width - 2}"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt(parameter.width.W))
  val b: UInt = Input(UInt(parameter.width.W))
  val tc: Bool = Input(Bool())
  val dplx: Bool = Input(Bool())
  val lt1: Bool = Output(Bool())
  val eq1: Bool = Output(Bool())
  val gt1: Bool = Output(Bool())
  val lt2: Bool = Output(Bool())
  val eq2: Bool = Output(Bool())
  val gt2: Bool = Output(Bool())
}
