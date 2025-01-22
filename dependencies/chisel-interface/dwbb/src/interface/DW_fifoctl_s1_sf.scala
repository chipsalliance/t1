// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_fifoctl_s1_sf

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    depth: Int = 4,
    aeLevel: Int = 1,
    afLevel: Int = 1,
    errMode: String = "pointer_latched",
    rstMode: String = "async"
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 24).contains(log2Ceil(depth)),
    "width must be between 2 and pow(2, 24)"
  )
  require(
    Range.inclusive(1, depth - 1).contains(aeLevel),
    "aeLevel must be between 1 and depth-1"
  )
  require(
    Range.inclusive(1, depth - 1).contains(afLevel),
    "afLevel must be between 1 and depth-1"
  )
  require(
    Seq("pointer_latched", "latched", "unlatched").contains(errMode),
    "errMode must be one of 'pointer_latched', 'latched', 'unlatched'"
  )
  require(
    Seq("async", "sync").contains(rstMode),
    "rstMode must be one of 'async', 'sync'"
  )
  val bitWidth: Int = log2Ceil(depth)
}

// interface with comments
class Interface(parameter: Parameter) extends Bundle {
  // Input Clock (pos edge)
  val clk: Clock = Input(Clock())
  // Async reset (active low)
  val rst_n: Bool = Input(Bool())
  // Push request (active low)
  val push_req_n: Bool = Input(Bool())
  // Pop Request (active low)
  val pop_req_n: Bool = Input(Bool())
  // Diagnostic sync. reset rd_addr (active low)
  val diag_n: Bool = Input(Bool())
  // RAM Write Enable output (active low)
  val we_n: Bool = Output(Bool())
  // FIFO Empty flag output (active high)
  val empty: Bool = Output(Bool())
  // FIFO Almost Empty flag output (active high)
  val almost_empty: Bool = Output(Bool())
  // FIFO Half Full flag output (active high)
  val half_full: Bool = Output(Bool())
  // FIFO almost Full flag output (active high)
  val almost_full: Bool = Output(Bool())
  // FIFO full flag output (active high)
  val full: Bool = Output(Bool())
  // FIFO Error flag output (active high)
  val error: Bool = Output(Bool())
  // RAM Write Address output bus
  val wr_addr: UInt = Output(UInt(parameter.bitWidth.W))
  // RAM Read Address output bus
  val rd_addr: UInt = Output(UInt(parameter.bitWidth.W))
}
