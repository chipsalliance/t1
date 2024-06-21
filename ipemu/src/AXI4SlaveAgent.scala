// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022-2024 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu.dpi

// TODO: upstream to AMBA as VIP
import chisel3._
import chisel3.util.circt.dpi.{RawClockedNonVoidFunctionCall, RawClockedVoidFunctionCall}
import chisel3.util.{OHToUInt, Reverse, Valid, scanLeftOr}
import org.chipsalliance.amba.axi4.bundle.{ARChannel, ARFlowControl, AWChannel, AWFlowControl, AXI4BundleParameter, AXI4ROIrrevocableVerilog, AXI4RWIrrevocableVerilog, AXI4WOIrrevocableVerilog, BChannel, BFlowControl, RChannel, RFlowControl, WChannel, WFlowControl}

case class AXI4SlaveAgentParameter(name: String, axiParameter: AXI4BundleParameter, outstanding: Int)

class AXI4SlaveAgentInterface(parameter: AXI4SlaveAgentParameter) extends Bundle {
  val clock:     Clock = Input(Clock())
  val reset:     Reset = Input(Reset())
  val channelId: UInt =  Input(Const(UInt(64.W)))
  val channel = Flipped(
    org.chipsalliance.amba.axi4.bundle.verilog.irrevocable(parameter.axiParameter)
  )
}

class WritePayload(dataWidth: Int) extends Bundle {
  val data = Vec(256, UInt(dataWidth.W))
  val strb = Vec(256, UInt((dataWidth / 8).W))
}

class ReadPayload(dataWidth: Int) extends Bundle {
  require(
    Seq(8, 16, 32, 64, 128, 256, 512, 1024).contains(dataWidth),
    "A1.2.1: The data bus, which can be 8, 16, 32, 64, 128, 256, 512, or 1024 bits wide. A read response signal indicating the completion status of the read transaction."
  )
  val data = Vec(256, UInt(dataWidth.W))
  val beats = UInt(8.W)
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
      val valid = RegInit(0.U.asTypeOf(Bool()))
      val writePayload = RegInit(0.U.asTypeOf(new WritePayload(parameter.axiParameter.dataWidth)))
      val writeId = RegInit(0.U(16.W))
      val writeIdx = RegInit(0.U.asTypeOf(UInt(8.W)))
      val last = RegInit(0.U.asTypeOf(Bool()))
      channel.AWREADY := !valid
      channel.WREADY := true.B
      channel.BVALID := last
      channel.BID := writeId
      channel.BRESP := 0.U(2.W) // OK
      channel.BUSER := DontCare

      RawClockedVoidFunctionCall(s"axi_write_${parameter.name}")(
        io.clock,
        last,
        io.channelId,
        channel.AWID.asTypeOf(UInt(64.W)),
        channel.AWADDR.asTypeOf(UInt(64.W)),
        channel.AWLEN.asTypeOf(UInt(64.W)),
        channel.AWSIZE.asTypeOf(UInt(64.W)),
        channel.AWBURST.asTypeOf(UInt(64.W)),
        channel.AWLOCK.asTypeOf(UInt(64.W)),
        channel.AWCACHE.asTypeOf(UInt(64.W)),
        channel.AWPROT.asTypeOf(UInt(64.W)),
        channel.AWQOS.asTypeOf(UInt(64.W)),
        channel.AWREGION.asTypeOf(UInt(64.W)),
        WireDefault(writePayload)
      )

      when(channel.AWREADY && channel.AWVALID) {
        assert(valid === false.B)
        writeId := channel.AWID
        valid := true.B
        writeIdx := 0.U
      }

      when(channel.WVALID && channel.WREADY) {
        writePayload.data(writeIdx) := channel.WDATA
        writePayload.strb(writeIdx) := channel.WSTRB
        writeIdx := writeIdx + 1.U
        last := channel.WLAST
      }

      when(channel.BVALID && channel.BREADY) {
        assert(valid === true.B)
        valid := false.B
        last := false.B
      }
    }
  }
  private class ReadManager(channel: ARChannel with ARFlowControl with RChannel with RFlowControl) {
    withClockAndReset(io.clock, io.reset) {
      // CAM to maintain order and valid. This is maintained as FIFO: ffo for allocate, flo for free
      // idx -> AxID
      val cam = RegInit(0.U.asTypeOf(Vec(parameter.outstanding, Valid(UInt(16.W)))))
      def flo(input:      UInt): UInt = Reverse(ffo(input))
      def ffo(input:      UInt): UInt = ((~(scanLeftOr(input) << 1)).asUInt & input)(input.getWidth - 1, 0)
      def firstEmpty(cam: Vec[Valid[UInt]]): UInt = OHToUInt(
        ffo(VecInit(cam.map(!_.valid)).asUInt)
      )
      def lastOccupied(cam: Vec[Valid[UInt]], id: UInt): UInt = OHToUInt(
        flo(VecInit(cam.map(content => (content.bits === id) && content.valid)).asUInt)
      )
      // AxID sending currently
      val currentId = RegInit(0.U.asTypeOf(UInt(16.W)))
      def currentIdx = cam(lastOccupied(cam, currentId)).valid
      // index to read payload for each outstandings
      val readIndex = RegInit(0.U.asTypeOf(Vec(parameter.outstanding, UInt(8.W))))
      val currentReadIndexes = readIndex(currentIdx)
      val readPayloads =
        RegInit(0.U.asTypeOf(Vec(parameter.outstanding, new ReadPayload(parameter.axiParameter.dataWidth))))
      val currentReadPayload = readPayloads(currentIdx)

      val readPayload =
        RawClockedNonVoidFunctionCall(s"axi_read_${parameter.name}", new ReadPayload(parameter.axiParameter.dataWidth))(
          io.clock,
          channel.ARVALID && channel.ARREADY,
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

      channel.ARREADY := VecInit(cam.map(!_.valid)).asUInt.andR // valid not all 1
      channel.RVALID := VecInit(cam.map(_.valid)).asUInt.orR // valid not all 0
      channel.RID := currentId
      channel.RDATA := currentReadPayload.data(currentReadIndexes)
      channel.RRESP := 0.U // OK
      channel.RLAST := currentReadPayload.beats === readIndex(currentId)
      channel.RUSER := DontCare

      when(channel.ARREADY && channel.ARVALID) {
        readPayloads(firstEmpty(cam)) := readPayload
        cam(firstEmpty(cam)).valid := true.B
        cam(firstEmpty(cam)).bits := channel.ARID
        readIndex(firstEmpty(cam)) := 0.U
      }
      when(channel.RREADY && channel.RVALID) {
        currentReadIndexes := currentReadIndexes + 1.U
        when(channel.RLAST) {
          cam(lastOccupied(cam, channel.RID)).valid := false.B
        }
      }
    }
  }
}
