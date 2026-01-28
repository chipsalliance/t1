// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW01_mux_any

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    aWidth: Int = 512,
    selWidth: Int = 8,
    muxWidth: Int = 2,
    balStr: Boolean = false
) extends SerializableModuleParameter {
  require(
    aWidth >= 1,
    "aWidth must be greater than or equal to 1"
  )
  require(
    selWidth >= 1,
    "selWidth must be greater than or equal to 1"
  )
  require(
    muxWidth >= 1,
    "muxWidth must be greater than or equal to 1"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val A: UInt = Input(UInt(parameter.aWidth.W))
  val SEL: UInt = Input(UInt(parameter.selWidth.W))
  val MUX: UInt = Output(UInt(parameter.muxWidth.W))
}
