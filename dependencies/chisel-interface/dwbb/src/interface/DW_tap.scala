// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_tap

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 8,
    id: Boolean = false,
    version: Int = 0,
    part: Int = 0,
    manNum: Int = 0,
    syncMode: String = "async",
    tstMode: Boolean = true
) extends SerializableModuleParameter {
  require(
    Range.inclusive(2, 32).contains(width),
    "width must be between 2 and 32"
  )
  require(
    Range.inclusive(0, 15).contains(version),
    "version must be between 0 and 15"
  )
  require(
    Range.inclusive(0, 65535).contains(part),
    "part must be between 0 and 65535"
  )
  require(
    (0 <= manNum) && (manNum >= 2047) && (manNum != 127)
  )
  require(
    Seq("sync", "async").contains(syncMode),
    "rstMode must be one of 'async', 'sync'"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val tck: Clock = Input(Clock())
  val trst_n: Bool = Input(Bool())
  val tms: Bool = Input(Bool())
  val tdi: Bool = Input(Bool())
  val so: Bool = Input(Bool())
  val bypass_sel: Bool = Input(Bool())
  val test: Bool = Input(Bool())
  val sentinel_val: UInt = Input(UInt((parameter.width - 2).W))
  val clock_dr: Clock = Output(Clock())
  val shift_dr: Bool = Output(Bool())
  val update_dr: Bool = Output(Bool())
  val tdo: Bool = Output(Bool())
  val tdo_en: Bool = Output(Bool())
  val tap_state: UInt = Output(UInt(16.W))
  val extest: Bool = Output(Bool())
  val samp_load: Bool = Output(Bool())
  val instructions: UInt = Output(UInt(parameter.width.W))
  val sync_capture_en: Bool = Output(Bool())
  val sync_update_dr: Bool = Output(Bool())
}
