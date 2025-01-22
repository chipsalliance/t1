// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_shifter

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    dataWidth: Int = 17,
    shWidth: Int = 4,
    invMode: String = "input_normal_output_0"
) extends SerializableModuleParameter {
  require(dataWidth >= 2, "dataWidth must be greater than or equal to 2")
  require(
    Range.inclusive(1, log2Ceil(dataWidth) + 1).contains(shWidth),
    s"shWidth must be between 1 and ${log2Ceil(dataWidth) + 1}"
  )
  require(
    Seq(
      "input_normal_output_0",
      "input_normal_output_1",
      "input_inverted_output_0",
      "input_inverted_output_1"
    ).contains(invMode),
    "invMode must be one of input_normal_output_0, input_normal_output_1, input_inverted_output_0, input_inverted_output_1"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val data_in: UInt = Input(UInt(parameter.dataWidth.W))
  val data_tc: Bool = Input(Bool())
  val sh: UInt = Input(UInt(parameter.shWidth.W))
  val sh_tc: Bool = Input(Bool())
  val sh_mode: Bool = Input(Bool())
  val data_out: UInt = Output(UInt(parameter.dataWidth.W))
}
