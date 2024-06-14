// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util.Cat

case class BreakpointUnitParameter(nBreakpoints: Int, xLen: Int, useBPWatch: Boolean, vaddrBits: Int, mcontextWidth: Int, scontextWidth: Int) extends SerializableModuleParameter

class BreakpointUnitInterface(parameter: BreakpointUnitParameter) extends Bundle {
  val status = Input(new MStatus())
  val bp = Input(Vec(parameter.nBreakpoints, new BP(parameter.xLen, parameter.useBPWatch, parameter.vaddrBits, parameter.mcontextWidth, parameter.scontextWidth)))
  val pc = Input(UInt(parameter.vaddrBits.W))
  val ea = Input(UInt(parameter.vaddrBits.W))
  val mcontext = Input(UInt(parameter.mcontextWidth.W))
  val scontext = Input(UInt(parameter.scontextWidth.W))
  val xcpt_if = Output(Bool())
  val xcpt_ld = Output(Bool())
  val xcpt_st = Output(Bool())
  val debug_if = Output(Bool())
  val debug_ld = Output(Bool())
  val debug_st = Output(Bool())
  val bpwatch = Output(Vec(parameter.nBreakpoints, new BPWatch))
}

class BreakpointUnit(val parameter: BreakpointUnitParameter)
  extends FixedIORawModule(new BreakpointUnitInterface(parameter))
    with SerializableModule[BreakpointUnitParameter] {
  // compatibility layer(don't use function in the bundle)
  def enabled(bpControl: BPControl, mstatus: MStatus): Bool = !mstatus.debug && Cat(bpControl.m, bpControl.h, bpControl.s, bpControl.u)(mstatus.prv)

  def contextMatch(bp: BP, mcontext: UInt, scontext: UInt): Bool =
    (if (parameter.mcontextWidth > 0)
      !bp.textra.mselect || (mcontext(bp.textra.mvalueBits - 1, 0) === bp.textra.mvalue)
    else true.B) &&
      (if (parameter.scontextWidth > 0)
        !bp.textra.sselect || (scontext(bp.textra.svalueBits - 1, 0) === bp.textra.svalue)
      else true.B
        )

  def addressMatch(bp: BP, x: UInt) = {
    def rangeAddressMatch(x: UInt) =
      (x >= bp.address) ^ bp.control.tmatch(0)

    def pow2AddressMatch(x: UInt): Bool = {
      def mask(): UInt = {
        import chisel3.experimental.conversions.seq2vec
        def maskMax = 4
        (0 until maskMax - 1).scanLeft(bp.control.tmatch(0))((m, i) => m && bp.address(i)).asUInt
      }
      (~x | mask()) === (~bp.address | mask())
    }
    Mux(bp.control.tmatch(1), rangeAddressMatch(x), pow2AddressMatch(x))
  }

  // original implementation

  io.xcpt_if := false.B
  io.xcpt_ld := false.B
  io.xcpt_st := false.B
  io.debug_if := false.B
  io.debug_ld := false.B
  io.debug_st := false.B

  (io.bpwatch.zip(io.bp)).foldLeft((true.B, true.B, true.B)) {
    case ((ri, wi, xi), (bpw, bp)) =>
      val en = enabled(bp.control, io.status)
      val cx = contextMatch(bp, io.mcontext, io.scontext)
      val r = en && bp.control.r && addressMatch(bp, io.ea) && cx
      val w = en && bp.control.w && addressMatch(bp, io.ea) && cx
      val x = en && bp.control.x && addressMatch(bp, io.pc) && cx
      val end = !bp.control.chain
      val action = bp.control.action

      bpw.action := action
      bpw.valid(0) := false.B
      bpw.rvalid(0) := false.B
      bpw.wvalid(0) := false.B
      bpw.ivalid(0) := false.B

      when(end && r && ri) {
        io.xcpt_ld := (action === 0.U); io.debug_ld := (action === 1.U); bpw.valid(0) := true.B; bpw.rvalid(0) := true.B
      }
      when(end && w && wi) {
        io.xcpt_st := (action === 0.U); io.debug_st := (action === 1.U); bpw.valid(0) := true.B; bpw.wvalid(0) := true.B
      }
      when(end && x && xi) {
        io.xcpt_if := (action === 0.U); io.debug_if := (action === 1.U); bpw.valid(0) := true.B; bpw.ivalid(0) := true.B
      }

      (end || r, end || w, end || x)
  }
}
