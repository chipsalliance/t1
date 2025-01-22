// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_sqrt

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 32,
    tcMode: Boolean = false
) extends SerializableModuleParameter {
  require(width >= 2, "width must be at least 2")
  val countWidth: Int = width / 2
  val addWidth: Int = width % 2
  val partWidth: Int = countWidth + addWidth
  val totalWidth: Int = width + addWidth
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt(parameter.width.W))
  val root: UInt = Output(UInt(parameter.partWidth.W))
}
