// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022-2024 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu.dpi

// TODO: upstream to AMBA as VIP
import chisel3._
import chisel3.util.circt.dpi.{RawClockedVoidFunctionCall, RawUnclockedNonVoidFunctionCall}
import chisel3.util.{isPow2, log2Ceil}
import org.chipsalliance.amba.axi4.bundle.{ARChannel, ARFlowControl, AWChannel, AWFlowControl, AXI4BundleParameter, AXI4ROIrrevocableVerilog, AXI4RWIrrevocableVerilog, AXI4WOIrrevocableVerilog, BChannel, BFlowControl, RChannel, RFlowControl, WChannel, WFlowControl}

class WritePayload(length: Int, dataWidth: Int) extends Bundle {
  val data = Vec(length, UInt(dataWidth.W))
  // For dataWidth <= 8, align strb to u8 for a simple C-API
  val strb = Vec(length, UInt(math.max(8, dataWidth / 8).W))
}

// TODO: consider adding the latency of the read transaction
class ReadPayload(length: Int,dataWidth: Int) extends Bundle {
  val data = Vec(length, UInt(dataWidth.W))
}

// consume transaction from DPI, drive RTL signal
class SimpleAXI4SlaveAgent(parameter: AXI4SlaveAgentParameter)
    extends AXI4SlaveAgent(parameter) {
  dontTouch(io)
  io.channel match {
    case channel: AXI4RWIrrevocableVerilog =>
      new WriteManager(channel)
      new ReadManager(channel)
    case channel: AXI4ROIrrevocableVerilog =>
      new ReadManager(channel)
    case channel: AXI4WOIrrevocableVerilog =>
      new WriteManager(channel)
  }

  private class WriteManager(
    channel: AWChannel with AWFlowControl with WChannel with WFlowControl with BChannel with BFlowControl) {
    withClockAndReset(io.clock, io.reset) {
      /** There is an aw in the register. */
      val awIssued = RegInit(false.B)
      /** There is a w in the register. */
      val last = RegInit(false.B)

      /** memory to store the write payload
        * @todo limit the payload size based on the RTL configuration.
        */
      val writePayload = RegInit(0.U.asTypeOf(new WritePayload(parameter.writePayloadSize, parameter.axiParameter.dataWidth)))
      /** AWID, latch at AW fire, used at B fire. */
      val awid = RegInit(0.U.asTypeOf(chiselTypeOf(channel.AWID)))
      val awaddr = RegInit(0.U.asTypeOf(chiselTypeOf(channel.AWADDR)))
      val awlen = RegInit(0.U.asTypeOf(chiselTypeOf(channel.AWLEN)))
      val awsize = RegInit(0.U.asTypeOf(chiselTypeOf(channel.AWSIZE)))
      val awburst = RegInit(0.U.asTypeOf(chiselTypeOf(channel.AWBURST)))
      val awlock = RegInit(0.U.asTypeOf(chiselTypeOf(channel.AWLOCK)))
      val awcache = RegInit(0.U.asTypeOf(chiselTypeOf(channel.AWCACHE)))
      val awprot = RegInit(0.U.asTypeOf(chiselTypeOf(channel.AWPROT)))
      val awqos = RegInit(0.U.asTypeOf(chiselTypeOf(channel.AWQOS)))
      val awregion = RegInit(0.U.asTypeOf(chiselTypeOf(channel.AWREGION)))
      val awuser = RegInit(0.U.asTypeOf(chiselTypeOf(channel.AWUSER)))

      /** index the payload, used to write [[writePayload]] */
      val writeIdx = RegInit(0.U.asTypeOf(UInt(8.W)))
      val bFire = channel.BREADY && channel.BVALID
      val awFire = channel.AWREADY && channel.AWVALID
      val wLastFire = channel.WVALID && channel.WREADY && channel.WLAST
      val awExist = channel.AWVALID || awIssued
      val wExist = channel.WVALID && channel.WLAST || last

      // AW
      channel.AWREADY := !awIssued || (wExist && channel.BREADY)
      when(channel.AWREADY && channel.AWVALID) {
        awid := channel.AWID
        awaddr := channel.AWADDR
        awlen := channel.AWLEN
        awsize := channel.AWSIZE
        awburst := channel.AWBURST
        awlock := channel.AWLOCK
        awcache := channel.AWCACHE
        awprot := channel.AWPROT
        awqos := channel.AWQOS
        awregion := channel.AWREGION
        awuser := channel.AWUSER
      }
      when(awFire ^ bFire) {
        awIssued := awFire
      }

      // W
      val writePayloadUpdate = WireDefault(writePayload)
      channel.WREADY := !last || (awExist && channel.BREADY)
      when(channel.WVALID && channel.WREADY) {
        writePayload.data(writeIdx) := channel.WDATA
        writePayloadUpdate.data(writeIdx) := channel.WDATA
        writePayload.strb(writeIdx) := channel.WSTRB.pad(writePayload.strb.getWidth)
        writePayloadUpdate.strb(writeIdx) := channel.WSTRB.pad(writePayload.strb.getWidth)
        writeIdx := writeIdx + 1.U
        when(channel.WLAST) {
          writeIdx := 0.U
        }
      }
      when(wLastFire ^ bFire) {
        last := wLastFire
      }

      // B
      channel.BVALID := awExist && wExist
      channel.BID := Mux(awIssued, awid, channel.AWID)
      channel.BRESP := 0.U(2.W) // OK
      channel.BUSER := DontCare
      // TODO: add latency to the write transaction reply
      when(channel.BVALID && channel.BREADY) {
        RawClockedVoidFunctionCall(s"axi_write_${parameter.name}")(
          io.clock,
          when.cond && !io.gateWrite,
          io.channelId,
          // handle AW and W at same beat.
          Mux(awIssued, awid.asTypeOf(UInt(64.W)), channel.AWID),
          Mux(awIssued, awaddr.asTypeOf(UInt(64.W)), channel.AWADDR),
          Mux(awIssued, awlen.asTypeOf(UInt(64.W)), channel.AWLEN),
          Mux(awIssued, awsize.asTypeOf(UInt(64.W)), channel.AWSIZE),
          Mux(awIssued, awburst.asTypeOf(UInt(64.W)), channel.AWBURST),
          Mux(awIssued, awlock.asTypeOf(UInt(64.W)), channel.AWLOCK),
          Mux(awIssued, awcache.asTypeOf(UInt(64.W)), channel.AWCACHE),
          Mux(awIssued, awprot.asTypeOf(UInt(64.W)), channel.AWPROT),
          Mux(awIssued, awqos.asTypeOf(UInt(64.W)), channel.AWQOS),
          Mux(awIssued, awregion.asTypeOf(UInt(64.W)), channel.AWREGION),
          writePayloadUpdate
        )
      }
    }
  }

  private class ReadManager(channel: ARChannel with ARFlowControl with RChannel with RFlowControl) {
    withClockAndReset(io.clock, io.reset) {
      class CAMValue extends Bundle {
        val arid = UInt(16.W)
        val arlen = UInt(8.W)
        val readPayload = new ReadPayload(parameter.readPayloadSize, parameter.axiParameter.dataWidth)
        val readPayloadIndex = UInt(8.W)
        val valid = Bool()
      }
      /** CAM to maintain order of read requests. This is maintained as FIFO. */
      val cam: Vec[CAMValue] = RegInit(0.U.asTypeOf(Vec(parameter.outstanding, new CAMValue)))
      require(isPow2(parameter.outstanding), "Need to handle pointers")
      val arPtr = RegInit(0.U.asTypeOf(UInt(log2Ceil(parameter.outstanding).W)))
      val rPtr = RegInit(0.U.asTypeOf(UInt(log2Ceil(parameter.outstanding).W)))

      // AR
      channel.ARREADY := !cam(arPtr).valid
      when(channel.ARREADY && channel.ARVALID) {
        cam(arPtr).arid := channel.ARID
        cam(arPtr).arlen := channel.ARLEN
        cam(arPtr).readPayload := RawUnclockedNonVoidFunctionCall(s"axi_read_${parameter.name}", new ReadPayload(parameter.readPayloadSize, parameter.axiParameter.dataWidth))(
          when.cond && !io.gateRead,
          io.channelId,
          channel.ARID.asTypeOf(UInt(64.W)),
          channel.ARADDR.asTypeOf(UInt(64.W)),
          channel.ARLEN.asTypeOf(UInt(64.W)),
          channel.ARSIZE.asTypeOf(UInt(64.W)),
          channel.ARBURST.asTypeOf(UInt(64.W)),
          channel.ARLOCK.asTypeOf(UInt(64.W)),
          channel.ARCACHE.asTypeOf(UInt(64.W)),
          channel.ARPROT.asTypeOf(UInt(64.W)),
          channel.ARQOS.asTypeOf(UInt(64.W)),
          channel.ARREGION.asTypeOf(UInt(64.W))
        )
        cam(arPtr).readPayloadIndex := 0.U
        cam(arPtr).valid := true.B
        arPtr := arPtr + 1.U
      }

      // R
      channel.RVALID := cam(rPtr).valid
      channel.RID := cam(rPtr).arid
      channel.RDATA := cam(rPtr).readPayload.data(cam(rPtr).readPayloadIndex)
      channel.RRESP := 0.U // OK
      channel.RLAST := (cam(rPtr).arlen === cam(rPtr).readPayloadIndex) && cam(rPtr).valid
      channel.RUSER := DontCare
      when(channel.RREADY && channel.RVALID) {
        // increase index
        cam(rPtr).readPayloadIndex := cam(rPtr).readPayloadIndex + 1.U
        when(channel.RLAST) {
          cam(rPtr).valid := false.B
          rPtr := rPtr + 1.U
        }
      }
    }
  }
}
