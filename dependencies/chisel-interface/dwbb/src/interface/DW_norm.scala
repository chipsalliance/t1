// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_norm

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    aWidth: Int = 8,
    srchWind: Int = 8,
    expWidth: Int = 4,
    expCtr: Boolean = false
) extends SerializableModuleParameter {
  require(aWidth >= 2, "aWidth must be greater than or equal to 2")
  require(
    Range.inclusive(2, aWidth).contains(srchWind),
    s"srchWind must be between 2 and $aWidth"
  )
  require(
    expWidth >= log2Ceil(srchWind),
    s"expWidth must be greater than or equal to ${log2Ceil(srchWind)}"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt(parameter.aWidth.W))
  val exp_offset: UInt = Input(UInt(parameter.expWidth.W))
  val no_detect: Bool = Output(Bool())
  val ovfl: Bool = Output(Bool())
  val b: UInt = Output(UInt(parameter.aWidth.W))
  val exp_adj: UInt = Output(UInt(parameter.expWidth.W))
}
