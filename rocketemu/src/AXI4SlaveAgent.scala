// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022-2024 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rocketv.dpi

// TODO: upstream to AMBA as VIP
import chisel3._
import chisel3.util.circt.dpi.{RawClockedNonVoidFunctionCall, RawClockedVoidFunctionCall}
import chisel3.util.{scanLeftOr, OHToUInt, Reverse, Valid}
import org.chipsalliance.amba.axi4.bundle.{
  ARChannel,
  ARFlowControl,
  AWChannel,
  AWFlowControl,
  AXI4BundleParameter,
  AXI4ROIrrevocableVerilog,
  AXI4RWIrrevocableVerilog,
  AXI4WOIrrevocableVerilog,
  BChannel,
  BFlowControl,
  RChannel,
  RFlowControl,
  WChannel,
  WFlowControl
}

case class AXI4SlaveAgentParameter(name: String, axiParameter: AXI4BundleParameter, outstanding: Int)

class AXI4SlaveAgentInterface(parameter: AXI4SlaveAgentParameter) extends Bundle {
  val clock:     Clock = Input(Clock())
  val reset:     Reset = Input(Reset())
  val channelId: UInt = Input(Const(UInt(64.W)))
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

      /** There is an valid write transaction. */
      val valid = RegInit(0.U.asTypeOf(Bool()))

      /** memory to store the write payload
        * @todo limit the payload size based on the RTL configuration.
        */
      val writePayload = RegInit(0.U.asTypeOf(new WritePayload(parameter.axiParameter.dataWidth)))

      /** AWID, latch at AW fire, used at B fire. */
      val writeId = RegInit(0.U(16.W))

      /** index the payload, used to write [[writePayload]] */
      val writeIdx = RegInit(0.U.asTypeOf(UInt(8.W)))

      /** indicate W is finished, used to wake up B channel. */
      val last = RegInit(0.U.asTypeOf(Bool()))

      // AW
      channel.AWREADY := !valid
      when(channel.AWREADY && channel.AWVALID) {
        assert(valid === false.B)
        writeId := channel.AWID
        valid := true.B
        writeIdx := 0.U
      }

      // W
      channel.WREADY := true.B
      when(channel.WVALID && channel.WREADY) {
        writePayload.data(writeIdx) := channel.WDATA
        writePayload.strb(writeIdx) := channel.WSTRB
        writeIdx := writeIdx + 1.U
        when(channel.WLAST) {
          last := true.B
          RawClockedVoidFunctionCall(s"axi_write_${parameter.name}")(
            io.clock,
            when.cond,
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
        }
      }

      // B
      channel.BVALID := last
      channel.BID := writeId
      channel.BRESP := 0.U(2.W) // OK
      channel.BUSER := DontCare
      when(channel.BVALID && channel.BREADY) {
        assert(valid === true.B)
        valid := false.B
        last := false.B
      }
    }
  }

  private class ReadManager(channel: ARChannel with ARFlowControl with RChannel with RFlowControl) {
    withClockAndReset(io.clock, io.reset) {
      class CAMValue extends Bundle {
        val arid = UInt(16.W)
        val readPayload = new ReadPayload(parameter.axiParameter.dataWidth)
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

      /** find oldest read. */
      val oldest = OHToUInt(ffo(VecInit(cam.map(content => content.valid)).asUInt))

      /** index to select value from [[cam]]
        * if cam empty, always select the next allocate value.
        * if cam non-empty, update to oldest at each transaction end, this can be changed to random response with LFSR.
        * @todo in the future, we can provide a fine-grand control to this index to provide out-of-order return.
        */
      val rIndex = RegInit(0.U.asTypeOf(UInt(16.W)))

      // AR
      channel.ARREADY := VecInit(cam.map(!_.valid)).asUInt.andR
      when(channel.ARREADY && channel.ARVALID) {
        cam(firstEmpty).arid := channel.ARID
        cam(firstEmpty).readPayload := RawClockedNonVoidFunctionCall(
          s"axi_read_${parameter.name}",
          new ReadPayload(parameter.axiParameter.dataWidth)
        )(
          io.clock,
          when.cond,
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
      rIndex := Mux(
        camIsEmpty,
        firstEmpty, // if cam empty, always select the next allocate value.
        Mux(
          channel.RREADY && channel.RVALID && channel.RLAST,
          oldest, // if cam non-empty, update to oldest at each transaction end, this can be changed to random response with LFSR.
          rIndex
        )
      )

      channel.RVALID := VecInit(cam.map(_.valid)).asUInt.orR
      channel.RID := cam(rIndex).arid
      channel.RDATA := cam(rIndex).readPayload.data(cam(rIndex).readPayloadIndex)
      channel.RRESP := 0.U // OK
      channel.RLAST := cam(rIndex).readPayload.beats === cam(rIndex).readPayloadIndex
      channel.RUSER := DontCare
      when(channel.RREADY && channel.RVALID) {
        // increase index
        cam(rIndex).readPayloadIndex := cam(rIndex).readPayloadIndex + 1.U
        when(channel.RLAST) {
          cam(rIndex).valid := false.B
        }
      }
    }
  }
}
