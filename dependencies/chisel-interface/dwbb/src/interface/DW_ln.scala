// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_ln

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    opWidth: Int = 4,
    arch: String = "area",
    errRange: Int = 1
) extends SerializableModuleParameter {
  require(
    Range.inclusive(2, 60).contains(opWidth),
    "opWidth must be between 2 and 60"
  )
  require(
    Seq("area", "speed").contains(arch),
    "Architecture must be one of 'area', 'speed'"
  )
  require(Range.inclusive(1, 2).contains(errRange), "errRange must be 1 or 2")
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt(parameter.opWidth.W))
  val z: UInt = Output(UInt(parameter.opWidth.W))
}
