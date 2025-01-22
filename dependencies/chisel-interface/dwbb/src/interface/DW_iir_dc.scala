// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_iir_dc

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    dataInWidth: Int = 8,
    dataOutWidth: Int = 16,
    fracDataOutWidth: Int = 4,
    feedbackWidth: Int = 12,
    maxCoefWidth: Int = 8,
    fracCoefWidth: Int = 4,
    saturationMode: Boolean = false,
    outReg: Boolean = true
) extends SerializableModuleParameter {
  require(
    dataInWidth >= 2,
    "dataInWidth must be greater than or equal to 2"
  )
  require(
    dataOutWidth >= 2,
    "dataOutWidth must be greater than or equal to 2"
  )
  require(
    Range.inclusive(0, dataOutWidth - 1).contains(fracDataOutWidth),
    s"fracDataOutWidth must be between 0 and ${dataOutWidth - 1}"
  )
  require(
    feedbackWidth >= 2,
    "feedbackWidth must be greater than or equal to 2"
  )
  require(
    maxCoefWidth >= 2,
    "maxCoefWidth must be greater than or equal to 2"
  )
  require(
    Range.inclusive(0, maxCoefWidth - 1).contains(fracCoefWidth),
    s"fracCoefWidth must be between 0 and ${maxCoefWidth - 1}"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val init_n: Bool = Input(Bool())
  val enable: Bool = Input(Bool())
  val A1_coef: UInt = Input(UInt(parameter.maxCoefWidth.W))
  val A2_coef: UInt = Input(UInt(parameter.maxCoefWidth.W))
  val B0_coef: UInt = Input(UInt(parameter.maxCoefWidth.W))
  val B1_coef: UInt = Input(UInt(parameter.maxCoefWidth.W))
  val B2_coef: UInt = Input(UInt(parameter.maxCoefWidth.W))
  val data_in: UInt = Input(UInt(parameter.dataInWidth.W))
  val data_out: UInt = Output(UInt(parameter.dataOutWidth.W))
  val saturation: Bool = Output(Bool())
}
