// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.fpga

import chisel3._
import chisel3.util._
import freechips.rocketchip.amba.axi4.{AXI4Bundle, AXI4BundleParameters}
import freechips.rocketchip.diplomacy.LazyModule
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.t1.subsystem.VerdesSystem

class MidShell(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle{
    val mem = new AXI4Bundle(new AXI4BundleParameters(30, 32, 4))
    val mmio = Flipped(new AXILiteBundle(AXILiteBundleParameters(32, 32, hasProt = true)))
    val irq = Output(Bool())
    val socAvailable = Input(Bool())
    val socReset = Output(Reset())
  })
  // SoC Reset
  val socResetCounter = RegInit(0.U(10.W))
  val socReset = socResetCounter.orR
  io.socReset := socReset

  // CPU Reset
  val cpuReset = RegInit(true.B)
  val cpuResetOut = reset.asBool || cpuReset || socReset

  when (socReset) {
    socResetCounter := socResetCounter + 1.U
  }

  // CPU Cycles
  val cpuCycles = withReset(cpuResetOut) { RegInit(0.U(64.W)) }
  cpuCycles := cpuCycles + 1.U

  val cpuResetVector = RegInit(0.U(32.W))

  // UART Queue
  val txQueue = Module(new Queue(UInt(8.W), 64, hasFlush = true))
  val rxQueue = Module(new Queue(UInt(8.W), 64, hasFlush = true))

  // UART tx interrupt
  val intEnable = RegInit(false.B)
  val txIRQ = RegInit(false.B)
  io.irq := (txIRQ || rxQueue.io.deq.valid) && intEnable

  val TXenqValid = WireDefault(false.B)
  val TXenqData = WireDefault(0.U(8.W))

  // Do TX
  txQueue.io.enq.bits := TXenqData
  txQueue.io.enq.valid := TXenqValid
  when(txQueue.io.enq.fire) {
    TXenqValid := false.B
  }

  // txIRQ
  val txQueueHasData = txQueue.io.deq.valid
  val lasttxQueueHasData = RegNext(txQueueHasData)
  val txQueueBecameEmpty = !txQueueHasData && lasttxQueueHasData
  when(txQueueBecameEmpty) {
    txIRQ := true.B
  }

  // Do RX
  val RXdeqReady = WireDefault(false.B)
  rxQueue.io.deq.ready := RXdeqReady

  // flush
  val flushTX = WireDefault(false.B)
  val flushRX = WireDefault(false.B)
  txQueue.io.flush.get := flushTX
  rxQueue.io.flush.get := flushRX

  // Debug
  val lastMemRead = withReset(cpuResetOut) { RegInit(~0.U(32.W)) }
  val lastMemWrite = withReset(cpuResetOut) { RegInit(~0.U(32.W)) }
  val lastMMIORead = withReset(cpuResetOut) { RegInit(~0.U(32.W)) }
  val lastMMIOWrite = withReset(cpuResetOut) { RegInit(~0.U(32.W)) }
  val hasMMIOError = withReset(cpuResetOut) { RegInit(false.B) }
  when(io.mem.ar.fire) {
    lastMemRead := io.mem.ar.bits.addr
  }
  when(io.mem.aw.fire) {
    lastMemWrite := io.mem.aw.bits.addr
  }

  def doRead(addr: UInt): (UInt, Bool) = {
    val data = WireDefault(0.U(32.W))
    val ok = WireDefault(false.B)
    txIRQ := false.B
    switch(addr) {
      is(UARTLiteRegMap.RX_FIFO) {
        data := rxQueue.io.deq.bits
        RXdeqReady := rxQueue.io.deq.valid
        ok := true.B
      }
      is(UARTLiteRegMap.TX_FIFO) {
        data := TXenqData
        ok := true.B
      }
      is(UARTLiteRegMap.STATUS) {
        data := Mux(txQueue.io.enq.ready, 0.U, UARTLiteStatus.TX_FIFO_FULL) |
          Mux(txQueue.io.deq.valid, 0.U, UARTLiteStatus.TX_FIFO_EMPTY) |
          Mux(rxQueue.io.deq.valid, UARTLiteStatus.RX_FIFO_VALID_DATA, 0.U) |
          Mux(rxQueue.io.enq.ready, 0.U, UARTLiteStatus.RX_FIFO_FULL) |
          Mux(intEnable, UARTLiteStatus.INTR_ENABLE, 0.U)
        ok := true.B
      }
      is(UARTLiteRegMap.CONTROL) {
        data := 0.U
        ok := true.B
      }
      is(16.U) {
        data := socReset | (io.socAvailable << 1)
        ok := true.B
      }
      is(20.U) {
        data := cpuReset
        ok := true.B
      }
      is(24.U) {
        data := lastMemRead
        ok := true.B
      }
      is(28.U) {
        data := lastMemWrite
        ok := true.B
      }
      is(32.U) {
        data := lastMMIORead
        ok := true.B
      }
      is(36.U) {
        data := lastMMIOWrite
        ok := true.B
      }
      is(40.U) {
        data := hasMMIOError
        ok := true.B
      }
      is(44.U) {
        data := cpuCycles(31,0)
        ok := true.B
      }
      is(48.U) {
        data := cpuCycles(63,32)
        ok := true.B
      }
      is(52.U) {
        data := cpuResetVector
        ok := true.B
      }
    }
    (data, ok)
  }

  def doWrite(addr: UInt, data: UInt): Bool = {
    val ok = WireDefault(false.B)
    switch(addr) {
      is(UARTLiteRegMap.RX_FIFO) {
        ok := true.B
      }
      is(UARTLiteRegMap.TX_FIFO) {
        TXenqData := data
        TXenqValid := true.B
        ok := true.B
      }
      is(UARTLiteRegMap.STATUS) {
        ok := true.B
      }
      is(UARTLiteRegMap.CONTROL) {
        ok := true.B
        when((data & UARTLiteControl.RESET_TX).orR) {
          flushTX := true.B
        }
        when((data & UARTLiteControl.RESET_RX).orR) {
          flushRX := true.B
        }
        intEnable := (data & UARTLiteControl.ENABLE_INTR).orR
      }
      is(16.U) {
        ok := true.B
        socResetCounter := 1.U
      }
      is(20.U) {
        ok := true.B
        cpuReset := data(0)
      }
      is(52.U) {
        ok := true.B
        cpuResetVector := data
      }
    }
    ok
  }

  def doHostMMIO(): Unit = {
    val hasPendingRead = RegInit(false.B)
    // Read
    val rdataReg = RegInit(0.U(32.W))
    val rrespReg = RegInit(0.U(2.W))
    io.mmio.ar.ready := Mux(reset.asBool, false.B, !hasPendingRead)
    io.mmio.r.valid := hasPendingRead
    io.mmio.r.bits.data := rdataReg
    io.mmio.r.bits.resp := rrespReg
    when(hasPendingRead) {
      when(io.mmio.r.fire) {
        hasPendingRead := false.B
      }
    }.elsewhen(io.mmio.ar.fire) {
      val res = doRead(io.mmio.ar.bits.addr)
      rdataReg := res._1
      rrespReg := Mux(res._2, 0.U, 1.U)
      hasPendingRead := true.B
    }
    // Write
    val hasPendingWrite = RegInit(false.B)
    val writeHandshake = RegInit(false.B)
    val bresp = RegInit(0.U(2.W))
    io.mmio.aw.ready := writeHandshake
    io.mmio.w.ready := writeHandshake
    io.mmio.b.valid := hasPendingWrite
    io.mmio.b.bits.resp := bresp
    writeHandshake := false.B
    when(!hasPendingWrite) {
      when(io.mmio.aw.valid && io.mmio.w.valid) {
        writeHandshake := true.B
        hasPendingWrite := true.B
        bresp := Mux(doWrite(io.mmio.aw.bits.addr, io.mmio.w.bits.data), 0.U, 1.U)
      }
    }.otherwise {
      when(io.mmio.b.fire) {
        hasPendingWrite := false.B
      }
    }
  }

  doHostMMIO()

  // dut
  withReset(cpuResetOut) {
    val dutUART = Module(new SimAXIUARTLite)
    dutUART.io.tx <> rxQueue.io.enq
    dutUART.io.rx <> txQueue.io.deq
    val axiltoaxi = Module(new AXILtoAXI(4))
    axiltoaxi.io.axil <> dutUART.io.mmio

    val ldut = LazyModule(new VerdesSystem)
    val dut = Module(ldut.module)

    ldut.resetVector := cpuResetVector

    ldut.mem_axi4.zip(ldut.memAXI4Node.in).map { case (mem_axi4_io, (_, edge)) =>
      mem_axi4_io <> io.mem
    }.toSeq


    ldut.mmio_axi4.zip(ldut.mmioAXI4Node.in).map { case (mmio_axi4_io, (_, edge)) =>
      mmio_axi4_io <> axiltoaxi.io.axi
    }.toSeq

    dut.interrupts := dutUART.io.irq

    // MMIO Debug
    when(axiltoaxi.io.axi.ar.fire) {
      lastMMIORead := axiltoaxi.io.axi.ar.bits.addr
      when(axiltoaxi.io.axi.ar.bits.len =/= 0.U) {
        hasMMIOError := true.B
      }
    }
    when(axiltoaxi.io.axi.aw.fire) {
      lastMMIOWrite := axiltoaxi.io.axi.aw.bits.addr
      when(axiltoaxi.io.axi.aw.bits.len =/= 0.U) {
        hasMMIOError := true.B
      }
    }
  }
}
