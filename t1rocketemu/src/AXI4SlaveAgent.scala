// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022-2024 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.t1rocketemu.dpi

// TODO: upstream to AMBA as VIP
import chisel3._
import chisel3.util.circt.dpi.{RawClockedNonVoidFunctionCall, RawClockedVoidFunctionCall}
import chisel3.util.{isPow2, log2Ceil, Queue}
import chisel3.experimental.dataview._
import org.chipsalliance.amba.axi4.bundle.{
  AR,
  ARChannel,
  ARFlowControl,
  AW,
  AWChannel,
  AWFlowControl,
  AXI4BundleParameter,
  AXI4ChiselBundle,
  AXI4ROIrrevocable,
  AXI4ROIrrevocableVerilog,
  AXI4RWIrrevocable,
  AXI4RWIrrevocableVerilog,
  AXI4WOIrrevocable,
  AXI4WOIrrevocableVerilog,
  B,
  BChannel,
  BFlowControl,
  HasAR,
  HasAW,
  HasB,
  HasR,
  HasW,
  R,
  RChannel,
  RFlowControl,
  W,
  WChannel,
  WFlowControl
}

case class AXI4SlaveAgentParameter(
  name:             String,
  axiParameter:     AXI4BundleParameter,
  outstanding:      Int,
  readPayloadSize:  Int,
  writePayloadSize: Int)

class AXI4SlaveAgentInterface(parameter: AXI4SlaveAgentParameter) extends Bundle {
  val clock:     Clock = Input(Clock())
  val reset:     Reset = Input(Reset())
  val channelId: UInt  = Input(Const(UInt(64.W)))
  // don't issue read DPI
  val gateRead:  Bool  = Input(Bool())
  // don't issue write DPI
  val gateWrite: Bool  = Input(Bool())
  val channel = Flipped(
    org.chipsalliance.amba.axi4.bundle.verilog.irrevocable(parameter.axiParameter)
  )
}

class WritePayload(length: Int, dataWidth: Int) extends Bundle {
  val data = Vec(length, UInt(dataWidth.W))
  // For dataWidth <= 8, align strb to u8 for a simple C-API
  val strb = Vec(length, UInt(math.max(8, dataWidth / 8).W))
}

class ReadPayload(length: Int, dataWidth: Int) extends Bundle {
  val data = Vec(length, UInt(dataWidth.W))
}

// consume transaction from DPI, drive RTL signal
class AXI4SlaveAgent(parameter: AXI4SlaveAgentParameter)
    extends FixedIORawModule[AXI4SlaveAgentInterface](new AXI4SlaveAgentInterface(parameter)) {
  dontTouch(io)
  val invClock = (~io.clock.asBool).asClock;

  io.channel match {
    case channel: AXI4RWIrrevocableVerilog => {
      val view = channel.viewAs[AXI4RWIrrevocable](
        implicitly,
        AXI4RWIrrevocableVerilog.viewChisel,
        implicitly
      )
      new ReadManager(view)
      new WriteManager(view)
    }
    case channel: AXI4ROIrrevocableVerilog => {
      val view = channel.viewAs[AXI4ROIrrevocable](
        implicitly,
        AXI4ROIrrevocableVerilog.viewChisel,
        implicitly
      )
      new ReadManager(view)
    }
    case channel: AXI4WOIrrevocableVerilog => {
      val view = channel.viewAs[AXI4WOIrrevocable](
        implicitly,
        AXI4WOIrrevocableVerilog.viewChisel,
        implicitly
      )
      new WriteManager(view)
    }
  }

  RawClockedVoidFunctionCall(s"axi_tick")(
    io.clock,
    true.B,
    io.reset.asTypeOf(UInt(8.W))
  )

  /// Widen a wire to make all DPI calls' types uniform
  def widen(wire: UInt, target: Int): UInt = {
    require(wire.isWidthKnown)
    require(wire.getWidth <= target)
    wire.asTypeOf(UInt(target.W))
  }

  private class WriteManager(channel: HasAW with HasW with HasB) {
    withClockAndReset(io.clock, io.reset) {
      val awqueue = Module(new Queue(new AW(parameter.axiParameter), 2))
      val wqueue  = Module(new Queue(new W(parameter.axiParameter), 2))
      val bqueue  = Module(new Queue(new B(parameter.axiParameter), 2))

      awqueue.io.enq <> channel.aw
      wqueue.io.enq <> channel.w
      bqueue.io.deq <> channel.b

      // Invoke DPI at negedge
      // NOTICE: results from these AXI calls CANNOT directly write any outside reg. Only write wires (e.g. here, only writes queue IO)

      // AW
      val awRet = RawClockedNonVoidFunctionCall(s"axi_push_AW", Bool())(
        invClock,
        awqueue.io.deq.valid,
        io.reset.asTypeOf(UInt(8.W)),
        io.channelId,
        parameter.axiParameter.dataWidth.U(64.W),
        awqueue.io.deq.bits.id.asTypeOf(UInt(64.W)),
        awqueue.io.deq.bits.addr.asTypeOf(UInt(64.W)),
        awqueue.io.deq.bits.size.asTypeOf(UInt(64.W)),
        awqueue.io.deq.bits.len.asTypeOf(UInt(64.W)),
        awqueue.io.deq.bits.user.asTypeOf(UInt(64.W))
      )
      awqueue.io.deq.ready := awRet

      // W
      val wRet = RawClockedNonVoidFunctionCall(s"axi_push_W", Bool())(
        invClock,
        wqueue.io.deq.valid,
        io.reset.asTypeOf(UInt(8.W)),
        io.channelId,
        parameter.axiParameter.dataWidth.U(64.W),
        widen(wqueue.io.deq.bits.data, 1024),
        widen(wqueue.io.deq.bits.strb, 128),
        wqueue.io.deq.bits.last.asTypeOf(UInt(8.W))
      )
      wqueue.io.deq.ready := wRet

      class BBundle extends Bundle {
        val user     = UInt(32.W)
        val id       = UInt(16.W)
        val _padding = UInt(8.W)
        val valid    = UInt(8.W)
      }

      val bRet = RawClockedNonVoidFunctionCall(s"axi_pop_B", new BBundle())(
        invClock,
        bqueue.io.enq.ready,
        io.reset.asTypeOf(UInt(64.W)),
        io.channelId,
        parameter.axiParameter.dataWidth.U(64.W)
      )
      bqueue.io.enq.valid     := bRet.valid
      bqueue.io.enq.bits.id   := bRet.id
      bqueue.io.enq.bits.resp := 0.U(2.W)
      bqueue.io.enq.bits.user := bRet.user
    }
  }

  private class ReadManager(channel: HasAR with HasR) {
    withClockAndReset(io.clock, io.reset) {
      val arqueue = Module(new Queue(new AR(parameter.axiParameter), 2))
      val rqueue  = Module(new Queue(new R(parameter.axiParameter), 2))

      arqueue.io.enq <> channel.ar
      rqueue.io.deq <> channel.r

      // Invoke DPI at negedge
      // See the notice in WriteManager

      // AR
      val arRet = RawClockedNonVoidFunctionCall(s"axi_push_AR", Bool())(
        invClock,
        arqueue.io.deq.valid,
        io.reset.asTypeOf(UInt(8.W)),
        io.channelId,
        parameter.axiParameter.dataWidth.U(64.W),
        arqueue.io.deq.bits.id.asTypeOf(UInt(64.W)),
        arqueue.io.deq.bits.addr.asTypeOf(UInt(64.W)),
        arqueue.io.deq.bits.size.asTypeOf(UInt(64.W)),
        arqueue.io.deq.bits.len.asTypeOf(UInt(64.W)),
        arqueue.io.deq.bits.user.asTypeOf(UInt(64.W))
      )
      arqueue.io.deq.ready := arRet

      require(parameter.axiParameter.dataWidth <= 1024)
      class RBundle extends Bundle {
        val data  = UInt(1024.W)
        val user  = UInt(32.W)
        val id    = UInt(16.W)
        val last  = UInt(8.W)
        val valid = UInt(8.W)
      }
      val rRet = RawClockedNonVoidFunctionCall(s"axi_pop_R", new RBundle())(
        invClock,
        rqueue.io.enq.ready,
        io.reset.asTypeOf(UInt(8.W)),
        io.channelId,
        parameter.axiParameter.dataWidth.U(64.W)
      )
      rqueue.io.enq.valid     := rRet.valid
      rqueue.io.enq.bits.id   := rRet.id
      rqueue.io.enq.bits.last := rRet.last
      rqueue.io.enq.bits.user := rRet.user
      rqueue.io.enq.bits.data := rRet.data
      rqueue.io.enq.bits.resp := 0.U
    }
  }
}
