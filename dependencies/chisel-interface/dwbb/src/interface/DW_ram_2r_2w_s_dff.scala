// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_ram_2r_2w_s_dff

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 8,
    addrWidth: Int = 3,
    rstMode: Boolean = false
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 8192).contains(width),
    "Width must be between 1 and 8192"
  )
  require(
    Range.inclusive(1, 12).contains(addrWidth),
    "Address width must be between 1 and 12"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val en_w1_n: Bool = Input(Bool())
  val addr_w1: UInt = Input(UInt(parameter.addrWidth.W))
  val data_w1: UInt = Input(UInt(parameter.width.W))
  val en_w2_n: Bool = Input(Bool())
  val addr_w2: UInt = Input(UInt(parameter.addrWidth.W))
  val data_w2: UInt = Input(UInt(parameter.width.W))
  val en_r1_n: Bool = Input(Bool())
  val addr_r1: UInt = Input(UInt(parameter.addrWidth.W))
  val data_r1: UInt = Output(UInt(parameter.width.W))
  val en_r2_n: Bool = Input(Bool())
  val addr_r2: UInt = Input(UInt(parameter.addrWidth.W))
  val data_r2: UInt = Output(UInt(parameter.width.W))
}
