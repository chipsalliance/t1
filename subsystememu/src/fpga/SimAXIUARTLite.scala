package verdes.fpga

import chisel3._
import chisel3.util._
import verdes.fpga.io._

object UARTLiteRegMap { // All RX is defined as dut side RX
  val RX_FIFO = 0x0.U
  val TX_FIFO = 0x4.U
  val STATUS = 0x8.U
  val CONTROL = 0xC.U
}
object UARTLiteStatus {
  val RX_FIFO_VALID_DATA: UInt = 0x01.U // data in receive FIFO
  val RX_FIFO_FULL: UInt = 0x02.U // receive FIFO full
  val TX_FIFO_EMPTY: UInt = 0x04.U // transmit FIFO empty
  val TX_FIFO_FULL: UInt = 0x08.U  // transmit FIFO full
  val INTR_ENABLE: UInt = 0x10.U // interrupt enable
}

object UARTLiteControl {
  val RESET_TX = 0x01.U
  val RESET_RX = 0x02.U
  val ENABLE_INTR = 0x10.U
}

class SimAXIUARTLite(val queueSize: Int = 64) extends Module {
  val io = IO(new Bundle{
    val mmio = Flipped(new AXILiteBundle(AXILiteBundleParameters(32, 32, true)))
    val irq = Output(Bool())
    val rx = Flipped(EnqIO(UInt(8.W)))
    val tx = Flipped(DeqIO(UInt(8.W)))
  })
  // Queue
  val txQueue = Module(new Queue(UInt(8.W), queueSize, hasFlush = true))
  val rxQueue = Module(new Queue(UInt(8.W), queueSize, hasFlush = true))
  rxQueue.io.enq <> io.rx
  txQueue.io.deq <> io.tx

  // tx interrupt
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
  when (txQueueBecameEmpty) {
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


  def doRead(addr: UInt): (UInt, Bool) = {
    val data = WireDefault(0.U(32.W))
    val ok = WireDefault(false.B)
    txIRQ := false.B
    switch(addr(3,0)) {
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
    }
    (data, ok)
  }

  def doWrite(addr: UInt, data: UInt): Bool = {
    val ok = WireDefault(false.B)
    switch(addr(3,0)) {
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
}
