// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.instantiable
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}

object BreakpointUnitParameter {
  implicit def rwP: upickle.default.ReadWriter[BreakpointUnitParameter] =
    upickle.default.macroRW[BreakpointUnitParameter]
}

case class BreakpointUnitParameter(
  nBreakpoints:  Int,
  xLen:          Int,
  useBPWatch:    Boolean,
  vaddrBits:     Int,
  mcontextWidth: Int,
  scontextWidth: Int)
    extends SerializableModuleParameter

class BreakpointUnitInterface(parameter: BreakpointUnitParameter) extends Bundle {
  val status   = Input(new MStatus)
  val bp       = Input(
    Vec(
      parameter.nBreakpoints,
      new BP(
        parameter.xLen,
        parameter.useBPWatch,
        parameter.vaddrBits,
        parameter.mcontextWidth,
        parameter.scontextWidth
      )
    )
  )
  val pc       = Input(UInt(parameter.vaddrBits.W))
  val ea       = Input(UInt(parameter.vaddrBits.W))
  val mcontext = Input(UInt(parameter.mcontextWidth.W))
  val scontext = Input(UInt(parameter.scontextWidth.W))
  val xcpt_if  = Output(Bool())
  val xcpt_ld  = Output(Bool())
  val xcpt_st  = Output(Bool())
  val debug_if = Output(Bool())
  val debug_ld = Output(Bool())
  val debug_st = Output(Bool())
  val bpwatch  = Output(Vec(parameter.nBreakpoints, new BPWatch))
}

@instantiable
class BreakpointUnit(val parameter: BreakpointUnitParameter)
    extends FixedIORawModule(new BreakpointUnitInterface(parameter))
    with SerializableModule[BreakpointUnitParameter]
    with Public {
  io.xcpt_if  := false.B
  io.xcpt_ld  := false.B
  io.xcpt_st  := false.B
  io.debug_if := false.B
  io.debug_ld := false.B
  io.debug_st := false.B

  (io.bpwatch.zip(io.bp)).foldLeft((true.B, true.B, true.B)) { case ((ri, wi, xi), (bpw, bp)) =>
    val en     = BPControl.enabled(bp.control, io.status)
    val cx     =
      BP.contextMatch(bp, io.mcontext, io.scontext, parameter.xLen, parameter.mcontextWidth, parameter.scontextWidth)
    val r      = en && bp.control.r && BP.addressMatch(bp, io.ea) && cx
    val w      = en && bp.control.w && BP.addressMatch(bp, io.ea) && cx
    val x      = en && bp.control.x && BP.addressMatch(bp, io.pc) && cx
    val end    = !bp.control.chain
    val action = bp.control.action

    bpw.action := action
    bpw.valid  := false.B
    bpw.rvalid := false.B
    bpw.wvalid := false.B
    bpw.ivalid := false.B

    when(end && r && ri) {
      io.xcpt_ld := (action === 0.U); io.debug_ld := (action === 1.U); bpw.valid := true.B; bpw.rvalid := true.B
    }
    when(end && w && wi) {
      io.xcpt_st := (action === 0.U); io.debug_st := (action === 1.U); bpw.valid := true.B; bpw.wvalid := true.B
    }
    when(end && x && xi) {
      io.xcpt_if := (action === 0.U); io.debug_if := (action === 1.U); bpw.valid := true.B; bpw.ivalid := true.B
    }

    (end || r, end || w, end || x)
  }
}
