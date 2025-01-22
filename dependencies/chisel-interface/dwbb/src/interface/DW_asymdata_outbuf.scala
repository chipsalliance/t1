// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_asymdata_outbuf

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    inWidth: Int = 16,
    outWidth: Int = 8,
    errMode: String = "sticky_error_flag",
    byteOrder: String = "msb"
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 2048).contains(inWidth),
    "inWidth must be between 1 and 2048"
  )
  require(
    Range.inclusive(1, 2048).contains(outWidth),
    "outWidth must be between 1 and 2048"
  )
  require(
    Seq("sticky_error_flag", "dynamic_error_flag").contains(errMode),
    "errMode must be one of 'sticky_error_flag', 'dynamic_error_flag'"
  )
  require(
    Seq("msb", "lsb").contains(byteOrder),
    "byteOrder must be one of 'msb', 'lsb'"
  )
  require(
    inWidth >= outWidth,
    "inWidth must be greater than or equal to outWidth"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val clk_pop: Clock = Input(Clock())
  val rst_pop_n: Bool = Input(Bool())
  val init_pop_n: Bool = Input(Bool())
  val pop_req_n: Bool = Input(Bool())
  val data_in: UInt = Input(UInt(parameter.inWidth.W))
  val fifo_empty: Bool = Input(Bool())
  val pop_wd_n: Bool = Output(Bool())
  val data_out: UInt = Output(UInt(parameter.outWidth.W))
  val part_wd: Bool = Output(Bool())
  val pop_error: Bool = Output(Bool())
}
