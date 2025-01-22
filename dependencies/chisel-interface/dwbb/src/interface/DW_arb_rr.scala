// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_arb_rr

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    n: Int = 4,
    outputMode: Boolean = false,
    indexMode: Int = 0
) extends SerializableModuleParameter {
  require(
    Range.inclusive(2, 32).contains(n),
    "n must be between 2 and 32"
  )
  require(
    Range.inclusive(0, 2).contains(indexMode),
    s"indexMode must be between 0 and 2"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val init_n: Bool = Input(Bool())
  val enable: Bool = Input(Bool())
  val request: UInt = Input(UInt(parameter.n.W))
  val mask: UInt = Input(UInt(parameter.n.W))
  val granted: Bool = Output(Bool())
  val grant: UInt = Output(UInt(parameter.n.W))
  val grant_index: UInt = Output(
    UInt(log2Ceil(parameter.n + (parameter.indexMode % 2)).W)
  )
}
