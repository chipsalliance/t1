// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022-2024 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rocketv.dpi

// TODO: upstream to AMBA as VIP
import chisel3._
import chisel3.util.circt.dpi.{RawClockedVoidFunctionCall, RawUnclockedNonVoidFunctionCall}
import chisel3.util.{OHToUInt, scanLeftOr}
import org.chipsalliance.amba.axi4.bundle.{ARChannel, ARFlowControl, AWChannel, AWFlowControl, AXI4BundleParameter, AXI4ROIrrevocableVerilog, AXI4RWIrrevocableVerilog, AXI4WOIrrevocableVerilog, BChannel, BFlowControl, RChannel, RFlowControl, WChannel, WFlowControl}

case class AXI4SlaveAgentParameter(name: String, axiParameter: AXI4BundleParameter, outstanding: Int, readPayloadSize: Int, writePayloadSize: Int)

class AXI4SlaveAgentInterface(parameter: AXI4SlaveAgentParameter) extends Bundle {
  val clock:     Clock = Input(Clock())
  val reset:     Reset = Input(Reset())
  val channelId: UInt =  Input(Const(UInt(64.W)))
  // don't issue read DPI
  val gateRead: Bool = Input(Bool())
  // don't issue write DPI
  val gateWrite: Bool = Input(Bool())
  val channel = Flipped(
    org.chipsalliance.amba.axi4.bundle.verilog.irrevocable(parameter.axiParameter)
  )
}

class WritePayload(length: Int, dataWidth: Int) extends Bundle {
  val data = Vec(length, UInt(dataWidth.W))
  val strb = Vec(length, UInt((dataWidth / 8).W))
}

class ReadPayload(length: Int,dataWidth: Int) extends Bundle {
  val data = Vec(length, UInt(dataWidth.W))
}

// consume transaction from DPI, drive RTL signal
class AXI4SlaveAgent(parameter: AXI4SlaveAgentParameter)
    extends FixedIORawModule[AXI4SlaveAgentInterface](new AXI4SlaveAgentInterface(parameter)) {
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
      /** indicate AW is issued. */
      val awIssued = RegInit(0.U.asTypeOf(Bool()))
      /** indicate W is finished, used to wake up B channel. */
      val last = RegInit(0.U.asTypeOf(Bool()))
      /** indicate there is an ongoing write transaction. */
      val busy = RegInit(0.U.asTypeOf(Bool()))

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

      /** index the payload, used to write [[writePayload]] */
      val writeIdx = RegInit(0.U.asTypeOf(UInt(8.W)))

      // AW
      channel.AWREADY := !busy || (busy && !awIssued)
      when(channel.AWREADY && channel.AWVALID) {
        awIssued := true.B
        busy := true.B
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
      }

      // W
      channel.WREADY := !busy || (busy && !last)
      when(channel.WVALID && channel.WREADY) {
        busy := true.B
        writePayload.data(writeIdx) := channel.WDATA
        writePayload.strb(writeIdx) := channel.WSTRB
        writeIdx := writeIdx + 1.U
        when(channel.WLAST) {
          last := true.B
        }
      }

      // B
      channel.BVALID := last && awIssued
      channel.BID := awid
      channel.BRESP := 0.U(2.W) // OK
      channel.BUSER := DontCare
      when(channel.BVALID && channel.BREADY) {
        RawClockedVoidFunctionCall(s"axi_write_${parameter.name}")(
          io.clock,
          when.cond && !io.gateWrite,
          io.channelId,
          // handle AW and W at same beat.
          Mux(channel.AWREADY && channel.AWVALID, channel.AWID, awid.asTypeOf(UInt(64.W))),
          Mux(channel.AWREADY && channel.AWVALID, channel.AWADDR, awaddr.asTypeOf(UInt(64.W))),
          Mux(channel.AWREADY && channel.AWVALID, channel.AWLEN, awlen.asTypeOf(UInt(64.W))),
          Mux(channel.AWREADY && channel.AWVALID, channel.AWSIZE, awsize.asTypeOf(UInt(64.W))),
          Mux(channel.AWREADY && channel.AWVALID, channel.AWBURST, awburst.asTypeOf(UInt(64.W))),
          Mux(channel.AWREADY && channel.AWVALID, channel.AWLOCK, awlock.asTypeOf(UInt(64.W))),
          Mux(channel.AWREADY && channel.AWVALID, channel.AWCACHE, awcache.asTypeOf(UInt(64.W))),
          Mux(channel.AWREADY && channel.AWVALID, channel.AWPROT, awprot.asTypeOf(UInt(64.W))),
          Mux(channel.AWREADY && channel.AWVALID, channel.AWQOS, awqos.asTypeOf(UInt(64.W))),
          Mux(channel.AWREADY && channel.AWVALID, channel.AWREGION, awregion.asTypeOf(UInt(64.W))),
          WireDefault(writePayload)
        )
        awIssued := false.B
        last := false.B
        writeIdx := 0.U
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
      /** find first one circuit. */
      def ffo(input: UInt): UInt = ((~(scanLeftOr(input) << 1)).asUInt & input)(input.getWidth - 1, 0)
      /** find first non-valid slot in [[cam]] */
      val firstEmpty: UInt = OHToUInt(ffo(VecInit(cam.map(!_.valid)).asUInt))
      /** there are no outstanding read requests. */
      val camIsEmpty = VecInit(cam.map(content => !content.valid)).asUInt.andR
      /** find oldest to index which cam to use. */
      val oldest = OHToUInt(ffo(VecInit(cam.map(content => content.valid)).asUInt))

      // AR
      channel.ARREADY := VecInit(cam.map(!_.valid)).asUInt.orR
      when(channel.ARREADY && channel.ARVALID) {
        cam(firstEmpty).arid := channel.ARID
        cam(firstEmpty).arlen := channel.ARLEN
        cam(firstEmpty).readPayload := RawUnclockedNonVoidFunctionCall(s"axi_read_${parameter.name}", new ReadPayload(parameter.readPayloadSize, parameter.axiParameter.dataWidth))(
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
        ).asInstanceOf[ReadPayload]
        cam(firstEmpty).readPayloadIndex := 0.U
        cam(firstEmpty).valid := true.B
      }

      // R
      channel.RVALID := VecInit(cam.map(_.valid)).asUInt.orR
      channel.RID := cam(oldest).arid
      channel.RDATA := cam(oldest).readPayload.data(cam(oldest).readPayloadIndex)
      channel.RRESP := 0.U // OK
      channel.RLAST := (cam(oldest).arlen === cam(oldest).readPayloadIndex) && cam(oldest).valid
      channel.RUSER := DontCare
      when(channel.RREADY && channel.RVALID) {
        // increase index
        cam(oldest).readPayloadIndex := cam(oldest).readPayloadIndex + 1.U
        when(channel.RLAST) {
          cam(oldest).valid := false.B
        }
      }
    }
  }
}
