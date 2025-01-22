// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW02_multp

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
    outWidth: Int = 18,
    verifEn: Int = 2
) extends SerializableModuleParameter {
  require(aWidth >= 1, "aWidth must be greater than or equal to 1")
  require(bWidth >= 1, "bWidth must be greater than or equal to 1")
  require(
    outWidth >= (aWidth + bWidth + 2),
    s"outWidth must be greater than or equal to ${aWidth + bWidth + 2}"
  )
  require(
    Range.inclusive(0, 3).contains(verifEn),
    "verifEn must be between 0 and 3"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt(parameter.aWidth.W))
  val b: UInt = Input(UInt(parameter.bWidth.W))
  val tc: Bool = Input(Bool())
  val out0: UInt = Output(UInt(parameter.outWidth.W))
  val out1: UInt = Output(UInt(parameter.outWidth.W))
}
