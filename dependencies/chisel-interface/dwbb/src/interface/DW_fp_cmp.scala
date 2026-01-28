// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_fp_cmp

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
    ieeeCompliance: Boolean = false
) extends SerializableModuleParameter {
  require(
    Range.inclusive(2, 253).contains(sigWidth),
    "Significand width must be between 2 and 253"
  )
  require(
    Range.inclusive(3, 31).contains(expWidth),
    "Exponent width must be between 3 and 31"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val b: UInt = Input(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val altb: Bool = Output(Bool())
  val agtb: Bool = Output(Bool())
  val aeqb: Bool = Output(Bool())
  val unordered: Bool = Output(Bool())
  val z0: UInt = Output(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val z1: UInt = Output(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val status0: UInt = Output(UInt(8.W))
  val status1: UInt = Output(UInt(8.W))
  val zctr: Bool = Input(Bool())
}
