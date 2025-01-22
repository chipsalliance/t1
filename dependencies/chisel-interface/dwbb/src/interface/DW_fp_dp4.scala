// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_fp_dp4

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
    archType: String = "precision"
) extends SerializableModuleParameter {
  require(
    Range.inclusive(2, 253).contains(sigWidth),
    "Significand width must be between 2 and 253"
  )
  require(
    Range.inclusive(3, 31).contains(expWidth),
    "Exponent width must be between 3 and 31"
  )
  require(
    Seq("precision", "area").contains(archType),
    "Architecture type must be either precision or area"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val b: UInt = Input(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val c: UInt = Input(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val d: UInt = Input(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val e: UInt = Input(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val f: UInt = Input(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val g: UInt = Input(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val h: UInt = Input(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val rnd: UInt = Input(UInt(3.W))
  val z: UInt = Output(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val status: UInt = Output(UInt(8.W))
}