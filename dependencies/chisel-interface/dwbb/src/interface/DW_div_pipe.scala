// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_div_pipe

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
    tcMode: Boolean = false,
    remMode: Boolean = true,
    numStages: Int = 2,
    stallMode: Boolean = true,
    rstMode: String = "async",
    opIsoMode: String = "DW_lp_op_iso_mode"
) extends SerializableModuleParameter {
  require(aWidth >= 2, "aWidth must be greater than or equal to 2")
  require(bWidth >= 2, "bWidth must be greater than or equal to 2")
  require(numStages >= 2, "numStages must be greater than or equal to 2")
  require(
    Seq("none", "async", "sync").contains(rstMode),
    "rstMode must be one of 'none', 'async', 'sync'"
  )
  require(
    Seq("DW_lp_op_iso_mode", "none", "and", "or", "prefer_and").contains(
      opIsoMode
    ),
    "opIsoMode must be one of 'DW_lp_op_iso_mode', 'none', 'and', 'or', 'prefer_and'"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val en: Bool = Input(Bool())
  val a: UInt = Input(UInt(parameter.aWidth.W))
  val b: UInt = Input(UInt(parameter.bWidth.W))
  val quotient: UInt = Output(UInt(parameter.aWidth.W))
  val remainder: UInt = Output(UInt(parameter.bWidth.W))
  val divide_by_0: Bool = Output(Bool())
}
