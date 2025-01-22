// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_prod_sum_pipe

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    aWidth: Int = 3,
    bWidth: Int = 5,
    numInputs: Int = 2,
    numStages: Int = 2,
    stallMode: Boolean = true,
    rstMode: String = "async",
    sumWidth: Int = 8,
    opIsoMode: String = "DW_lp_op_iso_mode"
) extends SerializableModuleParameter {
  require(aWidth >= 1, "aWidth must be greater than or equal to 1")
  require(bWidth >= 1, "bWidth must be greater than or equal to 1")
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
  val tc: Bool = Input(Bool())
  val a: UInt = Input(UInt((parameter.aWidth * parameter.numInputs).W))
  val b: UInt = Input(UInt((parameter.bWidth * parameter.numInputs).W))
  val sum: UInt = Output(UInt(parameter.sumWidth.W))
}
