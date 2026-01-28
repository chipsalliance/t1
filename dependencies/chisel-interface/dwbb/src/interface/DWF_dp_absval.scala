// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DWF_dp_absval

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(width: Int = 16) extends SerializableModuleParameter {}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt(parameter.width.W))
  val b: UInt = Input(UInt(parameter.width.W))
  val c: UInt = Input(UInt(parameter.width.W))
  val z: UInt = Output(UInt((parameter.width + 1).W))
}
