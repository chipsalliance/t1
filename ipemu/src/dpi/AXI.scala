// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu.dpi

import chisel3._
import org.chipsalliance.amba.axi4.bundle.AXI4RWIrrevocableVerilog

class awDpi(channelName: String, bundle: AXI4RWIrrevocableVerilog, triggerDelay: Int) extends DPIModuleLegacy {
  override def desiredName: String = channelName + '_' + super.desiredName
  val isImport: Boolean = true
  val clock = dpiTrigger("clock", Input(Bool()))

  val AWID = dpiIn("AWID", bundle.AWID)
  val AWADDR = dpiIn("AWADDR", bundle.AWADDR)
  val AWLEN = dpiIn("AWLEN", bundle.AWLEN)
  val AWSIZE = dpiIn("AWSIZE", bundle.AWSIZE)
  val AWBURST = dpiIn("AWBURST", bundle.AWBURST)
  val AWLOCK = dpiIn("AWLOCK", bundle.AWLOCK)
  val AWCACHE = dpiIn("AWCACHE", bundle.AWCACHE)
  val AWPROT = dpiIn("AWPROT", bundle.AWPROT)
  val AWQOS = dpiIn("AWQOS", bundle.AWQOS)
  val AWREGION = dpiIn("AWREGION", bundle.AWREGION)
  val AWUSER = dpiIn("AWUSER", bundle.AWUSER)
  val AWVALID = dpiIn("AWVALID", bundle.AWVALID)
  val AWREADY = dpiOut("AWREADY", bundle.AWREADY)

  override val trigger = s"always @(posedge ${clock.name}) #(${triggerDelay})"
}

class wDpi(channelName: String, bundle: AXI4RWIrrevocableVerilog, triggerDelay: Int) extends DPIModuleLegacy {
  override def desiredName = channelName + '_' + super.desiredName
  val isImport: Boolean = true
  val clock = dpiTrigger("clock", Input(Bool()))

  val WDATA = dpiIn("WDATA", bundle.WDATA)
  val WSTRB = dpiIn("WSTRB", bundle.WSTRB)
  val WLAST = dpiIn("WLAST", bundle.WLAST)
  val WUSER = dpiIn("WUSER", bundle.WUSER)
  val WVALID = dpiIn("WVALID", bundle.WVALID)
  val WREADY = dpiOut("WREADY", bundle.WREADY)

  override val trigger = s"always @(posedge ${clock.name}) #(${triggerDelay})"
}

class bDpi(channelName: String, bundle: AXI4RWIrrevocableVerilog, triggerDelay: Int) extends DPIModuleLegacy {
  override def desiredName = channelName + '_' + super.desiredName
  val isImport: Boolean = true
  val clock = dpiTrigger("clock", Input(Bool()))

  val BID = dpiOut("BID", bundle.BID)
  val BRESP = dpiOut("BRESP", bundle.BRESP)
  val BUSER = dpiOut("BUSER", bundle.BUSER)
  val BVALID = dpiOut("BVALID", bundle.BVALID)
  val BREADY = dpiIn("BREADY", bundle.BREADY)

  override val trigger = s"always @(posedge ${clock.name}) #(${triggerDelay})"
}

class arDpi(channelName: String, bundle: AXI4RWIrrevocableVerilog, triggerDelay: Int) extends DPIModuleLegacy {
  override def desiredName = channelName + '_' + super.desiredName
  val isImport: Boolean = true
  val clock = dpiTrigger("clock", Input(Bool()))

  val ARID = dpiIn("ARID", bundle.ARID)
  val ARADDR = dpiIn("ARADDR", bundle.ARADDR)
  val ARLEN = dpiIn("ARLEN", bundle.ARLEN)
  val ARSIZE = dpiIn("ARSIZE", bundle.ARSIZE)
  val ARBURST = dpiIn("ARBURST", bundle.ARBURST)
  val ARLOCK = dpiIn("ARLOCK", bundle.ARLOCK)
  val ARCACHE = dpiIn("ARCACHE", bundle.ARCACHE)
  val ARPROT = dpiIn("ARPROT", bundle.ARPROT)
  val ARQOS = dpiIn("ARQOS", bundle.ARQOS)
  val ARREGION = dpiIn("ARREGION", bundle.ARREGION)
  val ARUSER = dpiIn("ARUSER", bundle.ARUSER)
  val ARVALID = dpiIn("ARVALID", bundle.ARVALID)
  val ARREADY = dpiOut("ARREADY", bundle.ARREADY)

  override val trigger = s"always @(posedge ${clock.name}) #(${triggerDelay})"
}

class rDpi(channelName: String, bundle: AXI4RWIrrevocableVerilog, triggerDelay: Int) extends DPIModuleLegacy {
  override def desiredName = channelName + '_' + super.desiredName
  val isImport: Boolean = true
  val clock = dpiTrigger("clock", Input(Bool()))

  val RID = dpiOut("RID", bundle.RID)
  val RDATA = dpiOut("RDATA", bundle.RDATA)
  val RRESP = dpiOut("RRESP", bundle.RRESP)
  val RLAST = dpiOut("RLAST", bundle.RLAST)
  val RUSER = dpiOut("RUSER", bundle.RUSER)
  val RVALID = dpiOut("RVALID", bundle.RVALID)
  val RREADY = dpiIn("RREADY", bundle.RREADY)

  override val trigger = s"always @(posedge ${clock.name}) #(${triggerDelay})"
}

