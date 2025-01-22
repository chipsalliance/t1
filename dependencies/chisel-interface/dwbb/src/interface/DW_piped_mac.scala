// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.DW_piped_mac

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}

case class Parameter(
    aWidth: Int = 8,
    bWidth: Int = 8,
    accWidth: Int = 16,
    tc: Boolean = false,
    pipeReg: String = "no_pipe",
    idWidth: Int = 1,
    noPm: Boolean = false,
    opIsoMode: String = "DW_lp_op_iso_mode"
) extends SerializableModuleParameter {
  require(
    Range.inclusive(1, 1024).contains(aWidth),
    "aWidth must be between 1 and 1024"
  )
  require(
    Range.inclusive(1, 1024).contains(bWidth),
    "bWidth must be between 1 and 1024"
  )
  require(
    accWidth >= (aWidth + bWidth),
    s"accWidth must be greater than or equal to ${aWidth + bWidth}"
  )
  require(
    Seq(
      "no_pipe",
      "pipe_stage0",
      "pipe_stage1",
      "pipe_stage0_1",
      "pipe_stage2",
      "pipe_stage0_2",
      "pipe_stage1_2",
      "pipe_stage0_1_2"
    ).contains(pipeReg),
    "pipeReg must be one of 'no_pipe', 'pipe_stage0', 'pipe_stage1', 'pipe_stage0_1', 'pipe_stage2', 'pipe_stage0_2', 'pipe_stage1_2', 'pipe_stage0_1_2'"
  )
  require(
    Range.inclusive(1, 1024).contains(idWidth),
    "idWidth must be between 1 and 1024"
  )
  require(
    Seq("DW_lp_op_iso_mode", "none", "and", "or", "prefer_and").contains(
      opIsoMode
    ),
    "opIsoMode must be one of 'DW_lp_op_iso_mode', 'none', 'and', 'or', 'prefer_and'"
  )
}

class Interface(parameter: Parameter) extends Bundle {
  val clk: Clock = Input(Clock())
  val rst_n: Bool = Input(Bool())
  val init_n: Bool = Input(Bool())
  val clr_acc_n: Bool = Input(Bool())
  val a: UInt = Input(UInt(parameter.aWidth.W))
  val b: UInt = Input(UInt(parameter.bWidth.W))
  val acc: UInt = Output(UInt(parameter.accWidth.W))
  val launch: Bool = Input(Bool())
  val launch_id: UInt = Input(UInt(parameter.idWidth.W))
  val pipe_full: Bool = Output(Bool())
  val pipe_ovf: Bool = Output(Bool())
  val accept_n: Bool = Input(Bool())
  val arrive: Bool = Output(Bool())
  val arrive_id: UInt = Output(UInt(parameter.idWidth.W))
  val push_out_n: Bool = Output(Bool())
  val pipe_census: UInt = Output(UInt(3.W))
}
