// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_pl_reg

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 8,
    inReg: Boolean = false,
    stages: Int = 4,
    outReg: Boolean = false,
    rstMode: String = "async"
) extends SerializableModuleParameter {
  require(width >= 1, "width must be greater than or equal to 1")
  require(
    Range.inclusive(1, 1024).contains(stages),
    "stages must be between 1 and 1024"
  )
  require(
    Seq("async", "sync").contains(rstMode),
    "rstMode must be one of 'async' and 'sync'"
  )
  val enWidth: Int = stages + (if (inReg) 1 else 0) + (if (outReg) 1 else 0) - 1
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val enable: UInt = Input(UInt(parameter.enWidth.W))
  val data_in: UInt = Input(UInt(parameter.width.W))
  val data_out: UInt = Output(UInt(parameter.width.W))
}
