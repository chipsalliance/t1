// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_sincos

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    aWidth: Int = 24,
    waveWidth: Int = 25,
    arch: String = "area",
    errRange: Int = 1
) extends SerializableModuleParameter {
  require(
    Range.inclusive(2, 34).contains(aWidth),
    "aWidth must be between 2 and 34"
  )
  require(
    Range.inclusive(2, 35).contains(waveWidth),
    "waveWidth must be between 2 and 35"
  )
  require(
    Seq("area", "speed").contains(arch),
    "Architecture must be one of 'area', or 'speed'"
  )
  require(Range.inclusive(1, 2).contains(errRange), "errRange must be 1 or 2")
}

class Interface(parameter: Parameter) extends Bundle {
  val A: UInt = Input(UInt(parameter.aWidth.W))
  val SIN_COS: Bool = Input(Bool())
  val WAVE: UInt = Output(UInt(parameter.waveWidth.W))
}
