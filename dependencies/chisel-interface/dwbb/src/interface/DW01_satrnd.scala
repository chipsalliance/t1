// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW01_satrnd

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(width: Int = 8, msbOut: Int = 6, lsbOut: Int = 2)
    extends SerializableModuleParameter {
  require(width >= 2, "width must be greater than or equal to 2")
  require(
    lsbOut < msbOut && lsbOut >= 0,
    "lsbOut must be less than msbOut and greater than or equal to 0"
  )
  require(msbOut <= width - 1, "msbOut must be less than or equal to width - 1")
}

class Interface(parameter: Parameter) extends Bundle {
  val din: UInt = Input(UInt(parameter.width.W))
  val tc: Bool = Input(Bool())
  val sat: Bool = Input(Bool())
  val rnd: Bool = Input(Bool())
  val ov: Bool = Output(Bool())
  val dout: UInt = Output(UInt((parameter.msbOut - parameter.lsbOut + 1).W))
}
