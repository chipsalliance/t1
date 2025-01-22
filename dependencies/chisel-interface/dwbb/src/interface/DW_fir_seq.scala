// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_fir_seq

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    dataInWidth: Int = 8,
    coefWidth: Int = 8,
    dataOutWidth: Int = 18,
    order: Int = 6
) extends SerializableModuleParameter {
  require(
    dataInWidth >= 1,
    "dataInWidth must be greater than or equal to 1"
  )
  require(
    coefWidth >= 1,
    "coefWidth must be greater than or equal to 1"
  )
  require(
    dataOutWidth >= 1,
    "dataOutWidth must be greater than or equal to 1"
  )
  require(
    Range.inclusive(2, 256).contains(order),
    "order must be between 2 and 256"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val coef_shift_en: Bool = Input(Bool())
  val tc: Bool = Input(Bool())
  val run: Bool = Input(Bool())
  val data_in: UInt = Input(UInt(parameter.dataInWidth.W))
  val coef_in: UInt = Input(UInt(parameter.coefWidth.W))
  val init_acc_val: UInt = Input(UInt(parameter.dataOutWidth.W))
  val start: Bool = Output(Bool())
  val hold: Bool = Output(Bool())
  val data_out: UInt = Output(UInt(parameter.dataOutWidth.W))
}
