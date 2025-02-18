// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022-2024 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.t1rocketemu.dpi

// TODO: upstream to AMBA as VIP
import chisel3._
import chisel3.util.circt.dpi.{RawClockedNonVoidFunctionCall, RawClockedVoidFunctionCall}
import chisel3.util.{isPow2, log2Ceil, Queue}
import chisel3.experimental.dataview._
import org.chipsalliance.amba.axi4.bundle.{
  AR, AW, R, W, B,
  HasAR, HasAW, HasR, HasW, HasB,
  AXI4ChiselBundle,
  AXI4ROIrrevocable,
  AXI4RWIrrevocable,
  AXI4WOIrrevocable,

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
  io.channel match {
    case channel : AXI4RWIrrevocableVerilog => {
      val view = channel.viewAs[AXI4RWIrrevocable](
        implicitly,
        AXI4RWIrrevocableVerilog.viewChisel,
        implicitly,
      )
      new ReadManager(view)
      new WriteManager(view)
    }
    case channel : AXI4ROIrrevocableVerilog => {
      // TODO: wait for https://github.com/chipsalliance/chisel-interface/pull/194
      /*
      val view = channel.viewAs[AXI4ROIrrevocable]
      new ReadManager(view)
      */
    }
    case channel : AXI4WOIrrevocableVerilog => {
      // TODO: wait for https://github.com/chipsalliance/chisel-interface/pull/194
      /*
      val view = channel.viewAs[AXI4WOIrrevocable]
      new WriteManager(view)
      */
    }
  }

  RawClockedVoidFunctionCall(s"axi_tick")(
    io.clock,
    true.B,
    io.reset.asTypeOf(UInt(8.W)),
  )

  private class WriteManager(channel: HasAW with HasW with HasB) {
    val awqueue = Module(new Queue(new AW(parameter.axiParameter), 2))
    val wqueue = Module(new Queue(new W(parameter.axiParameter), 2))
    val bqueue = Module(new Queue(new B(parameter.axiParameter), 2))

    awqueue.io.enq <> channel.aw
    wqueue.io.enq <> channel.w
    bqueue.io.deq <> channel.b

    // Invoke DPI at negedge
    // NOTICE: this block CANNOT directly write any outside reg. Only write wires (e.g. here, only writes queue IO)
    withClock((~io.clock.asBool).asClock) {
      // AW
      val awRet = RawClockedNonVoidFunctionCall(s"axi_push_AW", Bool())(
        io.clock,
        awqueue.io.deq.valid,
        io.reset.asTypeOf(UInt(8.W)),
        io.channelId,
        parameter.axiParameter.dataWidth.U(64.W),
        awqueue.io.deq.bits.id.asTypeOf(UInt(64.W)),
        awqueue.io.deq.bits.addr.asTypeOf(UInt(64.W)),
        awqueue.io.deq.bits.size.asTypeOf(UInt(64.W)),
        awqueue.io.deq.bits.len.asTypeOf(UInt(64.W)),
        awqueue.io.deq.bits.user.asTypeOf(UInt(64.W)),
      )
      awqueue.io.deq.ready := awRet

      // W
      val wRet = RawClockedNonVoidFunctionCall(s"axi_push_W", Bool())(
        io.clock,
        wqueue.io.deq.valid,
        io.reset.asTypeOf(UInt(8.W)),
        io.channelId,
        parameter.axiParameter.dataWidth.U(64.W),
        wqueue.io.deq.bits.data,
        wqueue.io.deq.bits.strb,
        wqueue.io.deq.bits.last.asTypeOf(UInt(8.W)),
      )
      wqueue.io.deq.ready := wRet

      class BBUndle extends Bundle {
        val valid = UInt(8.W)
        val _padding = UInt(8.W)
        val id = UInt(16.W)
        val user  = UInt(32.W)
      }

      val bRet = RawClockedNonVoidFunctionCall(s"axi_pop_B", new BBUndle())(
        io.clock,
        bqueue.io.enq.ready,
        io.reset.asTypeOf(UInt(64.W)),
        io.channelId,
        parameter.axiParameter.dataWidth.U(64.W),
      )
      bqueue.io.enq.valid := bRet.valid
      bqueue.io.enq.bits.id := bRet.id
      bqueue.io.enq.bits.resp := 0.U(2.W)
      bqueue.io.enq.bits.user := bRet.user
    }
  }

  private class ReadManager(channel: HasAR with HasR) {
    withClockAndReset(io.clock, io.reset) {
      val arqueue = Module(new Queue(new AR(parameter.axiParameter), 2))
      val rqueue = Module(new Queue(new R(parameter.axiParameter), 2))

      arqueue.io.enq <> channel.ar
      rqueue.io.deq <> channel.r
      withClock((~io.clock.asBool).asClock) {
        // AR
        val arRet = RawClockedNonVoidFunctionCall(s"axi_push_AR", Bool())(
          io.clock,
          arqueue.io.deq.valid,
          io.reset.asTypeOf(UInt(8.W)),
          io.channelId,
          parameter.axiParameter.dataWidth.U(64.W),
          arqueue.io.deq.bits.id.asTypeOf(UInt(64.W)),
          arqueue.io.deq.bits.addr.asTypeOf(UInt(64.W)),
          arqueue.io.deq.bits.size.asTypeOf(UInt(64.W)),
          arqueue.io.deq.bits.len.asTypeOf(UInt(64.W)),
          arqueue.io.deq.bits.user.asTypeOf(UInt(64.W)),
        )
        arqueue.io.deq.ready := arRet

        class RBundle extends Bundle {
          val valid = UInt(8.W)
          val last = UInt(8.W)
          val id = UInt(16.W)
          val user  = UInt(32.W)
          val data = UInt(parameter.axiParameter.dataWidth.W)
        }
        val rRet = RawClockedNonVoidFunctionCall(s"axi_pop_R", new RBundle())(
          io.clock,
          rqueue.io.enq.ready,
          io.reset.asTypeOf(UInt(64.W)),
          io.channelId,
          parameter.axiParameter.dataWidth.U(64.W),
        )
        rqueue.io.enq.valid := rRet.valid
        rqueue.io.enq.bits.id := rRet.id
        rqueue.io.enq.bits.last := rRet.last
        rqueue.io.enq.bits.user := rRet.user
        rqueue.io.enq.bits.data := rRet.data
        rqueue.io.enq.bits.resp := 0.U
      }
    }
  }
}
