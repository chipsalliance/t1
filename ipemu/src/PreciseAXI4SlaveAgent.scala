// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022-2024 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu.dpi

import chisel3._
import chisel3.util.Valid
import chisel3.util.circt.dpi.RawClockedNonVoidFunctionCall
import org.chipsalliance.amba.axi4.bundle
import org.chipsalliance.amba.axi4.bundle.{ARChannel, ARFlowControl, AWChannel, AWFlowControl, AXI4ROIrrevocableVerilog, AXI4RWIrrevocableVerilog, AXI4WOIrrevocableVerilog, BChannel, BFlowControl, RChannel, RFlowControl, WChannel, WFlowControl}

/** Cycle Accurate Module with precise DPI.
  * on the RTL side, if a channel is able to accept any
  */
class PreciseAXI4SlaveAgent(parameter: AXI4SlaveAgentParameter) extends AXI4SlaveAgent(parameter) {
  dontTouch(io)
  io.channel match {
    case channel: AWChannel with AWFlowControl => new AWManager(channel)
  }
  io.channel match {
    case channel: WChannel with WFlowControl => new WManager(channel)
  }
  io.channel match {
    case channel: BChannel with BFlowControl => new BManager(channel)
  }
  io.channel match {
    case channel: ARChannel with ARFlowControl => new ARManager(channel)
  }
  io.channel match {
    case channel: RChannel with RFlowControl => new RManager(channel)
  }

  private class AWManager(channel: AWChannel with AWFlowControl) {
    withClockAndReset(io.clock, io.reset) {
      val awPayload: AWChannel = Wire(new AWChannel {
        override lazy val parameter: bundle.AXI4BundleParameter = channel.parameter
      })
      awPayload.AWID := channel.AWID
      awPayload.AWADDR := channel.AWADDR
      awPayload.AWLEN := channel.AWLEN
      awPayload.AWSIZE := channel.AWSIZE
      awPayload.AWBURST := channel.AWBURST
      awPayload.AWLOCK := channel.AWLOCK
      awPayload.AWCACHE := channel.AWCACHE
      awPayload.AWPROT := channel.AWPROT
      awPayload.AWQOS := channel.AWQOS
      awPayload.AWREGION := channel.AWREGION
      awPayload.AWUSER := channel.AWUSER
      channel.AWREADY := RawClockedNonVoidFunctionCall(s"aw_${parameter.name}", Bool())(io.clock, channel.AWVALID, awPayload)
    }
  }

  private class WManager(channel: WChannel with WFlowControl) {
    withClockAndReset(io.clock, io.reset) {
      val wPayload: WChannel = Wire(new WChannel {
        override lazy val parameter: bundle.AXI4BundleParameter = channel.parameter
      })
      wPayload.WDATA := channel.WDATA
      wPayload.WSTRB := channel.WSTRB
      wPayload.WLAST := channel.WLAST
      wPayload.WUSER := channel.WUSER
      channel.WREADY := RawClockedNonVoidFunctionCall(s"w_${parameter.name}", Bool())(io.clock, channel.WVALID, wPayload)
    }
  }

  private class BManager(channel: BChannel with BFlowControl) {
    withClockAndReset(io.clock, io.reset) {
      val bPayload = Wire(Valid(new BChannel {
        override lazy val parameter: bundle.AXI4BundleParameter = channel.parameter
      }))
      bPayload := RawClockedNonVoidFunctionCall(s"b_${parameter.name}", chiselTypeOf(bPayload))(io.clock, channel.BREADY)
      channel.BVALID := bPayload.valid
      channel.BID := bPayload.bits.BID
      channel.BRESP := bPayload.bits.BRESP
      channel.BUSER := bPayload.bits.BUSER
    }
  }

  private class ARManager(channel: ARChannel with ARFlowControl) {
    withClockAndReset(io.clock, io.reset) {
      val arPayload: ARChannel = Wire(new ARChannel {
        override lazy val parameter: bundle.AXI4BundleParameter = channel.parameter
      })
      arPayload := channel
      channel.ARREADY := RawClockedNonVoidFunctionCall(s"ar_${parameter.name}", Bool())(io.clock, channel.ARVALID, arPayload)
    }
  }

  private class RManager(channel: RChannel with RFlowControl) {
    withClockAndReset(io.clock, io.reset) {
      val rPayload = Wire(Valid(new RChannel {
        override lazy val parameter: bundle.AXI4BundleParameter = channel.parameter
      }))
      rPayload := RawClockedNonVoidFunctionCall(s"b_${parameter.name}", chiselTypeOf(rPayload))(io.clock, channel.RREADY)
      channel.RVALID := rPayload.valid
      channel.RID := rPayload.bits.RID
      channel.RDATA := rPayload.bits.RDATA
      channel.RRESP := rPayload.bits.RRESP
      channel.RLAST := rPayload.bits.RLAST
      channel.RUSER := rPayload.bits.RUSER
    }
  }
}
