// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_sqrt_pipe

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 8,
    numStages: Int = 2,
    stallMode: Boolean = true,
    rstMode: String = "async",
    tcMode: Boolean = false,
    opIsoMode: String = "DW_lp_op_iso_mode"
) extends SerializableModuleParameter {
  require(width >= 2, "width must be greater than or equal to 2")
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
  val countWidth: Int = width / 2
  val addWidth: Int = width % 2
  val partWidth: Int = countWidth + addWidth
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val en: Bool = Input(Bool())
  val a: UInt = Input(UInt(parameter.width.W))
  val root: UInt = Output(UInt(parameter.partWidth.W))
}
