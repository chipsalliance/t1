// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_norm_rnd

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    aWidth: Int = 16,
    srchWind: Int = 4,
    expWidth: Int = 4,
    bWidth: Int = 10,
    expCtr: Boolean = false
) extends SerializableModuleParameter {
  require(aWidth >= 2, "aWidth must be greater than or equal to 2")
  require(
    Range.inclusive(2, aWidth).contains(srchWind),
    s"srchWind must be between 2 and $aWidth"
  )
  require(
    expWidth >= 1,
    s"expWidth must be greater than or equal to 1"
  )
  require(
    Range.inclusive(2, aWidth).contains(bWidth),
    s"bWidth must be between 2 and $aWidth"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val a_mag: UInt = Input(UInt(parameter.aWidth.W))
  val a_sign: Bool = Input(Bool())
  val pos_offset: UInt = Input(UInt(parameter.expWidth.W))
  val sticky_bit: Bool = Input(Bool())
  val rnd_mode: UInt = Input(UInt(3.W))
  val no_detect: Bool = Output(Bool())
  val pos_err: Bool = Output(Bool())
  val b: UInt = Output(UInt(parameter.bWidth.W))
  val pos: UInt = Output(UInt(parameter.expWidth.W))
}
