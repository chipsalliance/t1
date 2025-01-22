// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_fp_sincos

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    sigWidth: Int = 23,
    expWidth: Int = 8,
    ieeeCompliance: Boolean = false,
    piMultiple: Boolean = true,
    arch: String = "area",
    errRange: Int = 1
) extends SerializableModuleParameter {
  require(
    Range.inclusive(2, 33).contains(sigWidth),
    "Significand width must be between 2 and 33"
  )
  require(
    Range.inclusive(3, 31).contains(expWidth),
    "Exponent width must be between 3 and 31"
  )
  require(
    Seq("area", "speed").contains(arch),
    "Architecture must be either area or speed"
  )
  require(
    Range.inclusive(1, 2).contains(errRange),
    "Error range must be between 1 and 2"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val sin_cos: Bool = Input(Bool())
  val z: UInt = Output(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val status: UInt = Output(UInt(8.W))
}
