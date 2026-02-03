// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DWF_dp_mult_comb_sat

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    aWidth: Int = 8,
    bWidth: Int = 8,
    pWidth: Int = 8
) extends SerializableModuleParameter {
  require(aWidth >= 2, "aWidth must be greater than or equal to 2")
  require(bWidth >= 2, "bWidth must be greater than or equal to 2")
  require(pWidth >= 2, "pWidth must be greater than or equal to 2")
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt(parameter.aWidth.W))
  val a_tc: Bool = Input(Bool())
  val b: UInt = Input(UInt(parameter.bWidth.W))
  val b_tc: Bool = Input(Bool())
  val DWF_dp_mult_comb_sat: UInt = Output(UInt(parameter.pWidth.W))
}
