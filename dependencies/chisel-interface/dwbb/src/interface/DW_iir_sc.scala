// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_iir_sc

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    dataInWidth: Int = 4,
    dataOutWidth: Int = 6,
    fracDataOutWidth: Int = 0,
    feedbackWidth: Int = 8,
    maxCoefWidth: Int = 4,
    fracCoefWidth: Int = 0,
    saturationMode: Boolean = false,
    outReg: Boolean = true,
    a1Coef: Int = -2,
    a2Coef: Int = 3,
    b0Coef: Int = 5,
    b1Coef: Int = -6,
    b2Coef: Int = -2
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
    Range.inclusive(2, 31).contains(maxCoefWidth),
    "maxCoefWidth must be between 2 and 31"
  )
  require(
    Range.inclusive(0, maxCoefWidth - 1).contains(fracCoefWidth),
    s"fracCoefWidth must be between 0 and ${maxCoefWidth - 1}"
  )
  Seq(a1Coef, a2Coef, b0Coef, b1Coef, b2Coef).foreach { coef =>
    val maxCoef = math.pow(2, maxCoefWidth - 1).toInt
    require(
      Range.inclusive(-maxCoef, maxCoef - 1).contains(coef),
      s"coef must be between ${-maxCoef} and ${maxCoef - 1}"
    )
  }
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
