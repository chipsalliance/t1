// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_ecc

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 8,
    chkBits: Int = 5,
    syndSel: Boolean = false
) extends SerializableModuleParameter {
  require(
    Range.inclusive(4, 8178).contains(width),
    s"width must be between 4 and 8178"
  )
  require(
    Range.inclusive(5, 14).contains(chkBits),
    "chkBits must be between 5 and 14"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val gen: Bool = Input(Bool())
  val correct_n: Bool = Input(Bool())
  val datain: UInt = Input(UInt(parameter.width.W))
  val chkin: UInt = Input(UInt(parameter.chkBits.W))
  val err_detect: Bool = Output(Bool())
  val err_multpl: Bool = Output(Bool())
  val dataout: UInt = Output(UInt(parameter.width.W))
  val chkout: UInt = Output(UInt(parameter.chkBits.W))
}
