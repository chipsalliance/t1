// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_tap_uc

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import chisel3.util.log2Ceil
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    width: Int = 8,
    id: Boolean = false,
    idcodeOpcode: Int = 1,
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
    Range.inclusive(0, width - 1).contains(log2Ceil(idcodeOpcode)),
    "idcodeOpcode must be between 1 and 2^(width-1)"
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
  val sentinel_val: UInt = Input(UInt((parameter.width - 1).W))
  val device_id_sel: Bool = Input(Bool())
  val user_code_sel: Bool = Input(Bool())
  val user_code_val: UInt = Input(UInt(32.W))
  val ver: UInt = Input(UInt(4.W))
  val ver_sel: Bool = Input(Bool())
  val part_num: UInt = Input(UInt(16.W))
  val part_num_sel: Bool = Input(Bool())
  val mnfr_id: UInt = Input(UInt(11.W))
  val mnfr_id_sel: Bool = Input(Bool())
  val test: Bool = Input(Bool())
  val clock_dr: Clock = Output(Clock())
  val shift_dr: Bool = Output(Bool())
  val update_dr: Bool = Output(Bool())
  val tdo: Bool = Output(Bool())
  val tdo_en: Bool = Output(Bool())
  val tap_state: UInt = Output(UInt(16.W))
  val instructions: UInt = Output(UInt(parameter.width.W))
  val sync_capture_en: Bool = Output(Bool())
  val sync_update_dr: Bool = Output(Bool())
}
