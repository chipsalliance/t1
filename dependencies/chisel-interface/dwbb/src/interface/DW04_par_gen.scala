// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW04_par_gen

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 16,
    parType: Boolean = false
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 256).contains(width),
    s"width must be between 1 and 256"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val datain: UInt = Input(UInt(parameter.width.W))
  val parity: Bool = Output(Bool())
}
