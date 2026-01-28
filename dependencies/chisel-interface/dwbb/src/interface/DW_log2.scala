// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_log2

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    opWidth: Int = 8,
    arch: String = "obsolete",
    errRange: Int = 1
) extends SerializableModuleParameter {
  require(
    Range.inclusive(2, 60).contains(opWidth),
    "opWidth must be between 2 and 60"
  )
  require(
    Seq("area", "speed", "obsolete").contains(arch),
    "Architecture must be one of 'area', 'speed', or 'obsolete'"
  )
  require(Range.inclusive(1, 2).contains(errRange), "errRange must be 1 or 2")
  require(
    arch != "obsolete" || errRange == 1,
    "errRange must be 1 when arch is 'obsolete'"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt(parameter.opWidth.W))
  val z: UInt = Output(UInt(parameter.opWidth.W))
}
