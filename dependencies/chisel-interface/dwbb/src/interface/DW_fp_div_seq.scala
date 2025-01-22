// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_fp_div_seq

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
    numCyc: Int = 4,
    rstMode: Boolean = false,
    inputMode: Boolean = true,
    outputMode: Boolean = true,
    earlyStart: Boolean = false,
    internalReg: Boolean = true
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
    Range.inclusive(4, 2 * sigWidth + 3).contains(numCyc),
    s"Number of cycles must be between 4 and ${2 * sigWidth + 3}"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val a: UInt = Input(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val b: UInt = Input(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val rnd: UInt = Input(UInt(3.W))
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val start: Bool = Input(Bool())
  val z: UInt = Output(UInt((parameter.sigWidth + parameter.expWidth + 1).W))
  val status: UInt = Output(UInt(8.W))
  val complete: Bool = Output(Bool())
}
