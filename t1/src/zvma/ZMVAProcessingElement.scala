package org.chipsalliance.t1.rtl.zvma

import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import chisel3._
import org.chipsalliance.t1.rtl._
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}
import org.chipsalliance.t1.rtl.lsu.DataToZVMA

class ProcessInterface(parameter: ZVMAParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Reset())
  val request:  ValidIO[ZVMAExecute] = Flipped(Valid(new ZVMAExecute(parameter)))
  val response: DecoupledIO[UInt]    = Decoupled(UInt((parameter.elen * 2).W))
  val release:  Bool                 = Output(Bool())
}

class ZMVAProcessingElement(val parameter: ZVMAParameter)
    extends FixedIORawModule(new ProcessInterface(parameter))
    with SerializableModule[ZVMAParameter]
    with ImplicitClock
    with ImplicitReset {
  protected def implicitClock = io.clock
  protected def implicitReset = io.reset

  val reqQueue = Queue.io(new ZVMAExecute(parameter), parameter.subArrayBufferDepth)
  // data enq queue
  reqQueue.enq.valid := io.request.valid
  reqQueue.enq.bits  := io.request.bits
  // state ram
  val stateVec: Seq[SRAMInterface[UInt]] = Seq.tabulate(parameter.subArrayRamBank) { i =>
    SRAM(
      size = parameter.ramDepth,
      tpe = UInt((parameter.elen * 4).W),
      numReadPorts = 0,
      numWritePorts = 0,
      numReadwritePorts = 1
    )
  }

  // pipe reg
  // execute stage 0
  val dataPipe0:  ZVMAExecute = RegEnable(reqQueue.deq.bits, 0.U.asTypeOf(reqQueue.deq.bits), reqQueue.deq.fire)
  val pipeValid0: Bool        = RegNext(reqQueue.deq.fire, false.B)

  // execute stage 1
  val dataPipe1:  ZVMAExecute = RegInit(0.U.asTypeOf(new ZVMAExecute(parameter)))
  val pipeValid1: Bool        = RegNext(pipeValid0, false.B)
  val readData:   UInt        = RegInit(0.U((4 * parameter.elen).W))

  // execute stage 2
  val pipeValid2: Bool = RegNext(pipeValid1, false.B)
  val index2:     UInt = RegInit(0.U.asTypeOf(dataPipe0.index))
  val result:     UInt = RegInit(0.U((4 * parameter.elen).W))
  val resultR2:   UInt = RegInit(0.U((2 * parameter.elen).W))
  val accessTile: UInt = RegInit(0.U(4.W))
  val writeState2 = RegInit(false.B)

  // alu
  val readDataVec = cutUInt(readData, parameter.elen)
  val aluResult   = Wire(Vec(4, UInt(parameter.elen.W)))
  dataPipe1.rowData.zipWithIndex.foreach { case (rd, ri) =>
    dataPipe1.colData.zipWithIndex.foreach { case (cd, ci) =>
      val di: Int = (ri << 1) + ci
      val base           = readDataVec(di)
      // TODO: Temporarily there is int8
      val rdVec          = cutUInt(rd, 8)
      val cdVec          = cutUInt(cd, 8)
      val adderRes       = base + rdVec.zipWithIndex.map { case (d, i) =>
        Mux(dataPipe1.tk > i.U, d * cdVec(i), 0.U)
      }.reduce(_ + _)
      val loadDataSelect = Mux(dataPipe1.col, cd, rd)
      val useLoadData    = Mux(dataPipe1.col, ri.asUInt === dataPipe1.accessIndex, ci.asUInt === dataPipe1.accessIndex)
      aluResult(di) := Mux(dataPipe1.execute, adderRes, Mux(useLoadData, loadDataSelect, base))
    }
  }
  val mvData      = Mux(
    dataPipe1.col,
    Mux(dataPipe1.accessIndex, readDataVec(3) ## readDataVec(2), readDataVec(1) ## readDataVec(0)),
    Mux(dataPipe1.accessIndex, readDataVec(3) ## readDataVec(1), readDataVec(2) ## readDataVec(0))
  )

  // control
  val readReady = !(pipeValid2 && writeState2) || (index2(0) ^ reqQueue.deq.bits.index(0))
  reqQueue.deq.ready := readReady

  when(pipeValid0) {
    dataPipe1 := dataPipe0
    readData  := Mux(
      dataPipe0.index(0),
      stateVec.last.readwritePorts.head.readData,
      stateVec.head.readwritePorts.head.readData
    )
  }

  when(pipeValid1) {
    index2      := dataPipe1.index
    result      := aluResult.asUInt
    writeState2 := !dataPipe1.readTile
    resultR2    := mvData
    accessTile  := dataPipe1.accessTile
  }

  io.release := pipeValid2
  // rf read write
  stateVec.zipWithIndex.foreach { case (ram, index) =>
    val write     = pipeValid2 && (index2(0) === index.U) && writeState2
    val tryToRead = reqQueue.deq.valid && (reqQueue.deq.bits.index(0) === index.U)
    ram.readwritePorts.head.enable    := write || tryToRead
    ram.readwritePorts.head.address   := accessTile ## Mux(
      write,
      index2,
      reqQueue.deq.bits.index
    ) >> 1
    ram.readwritePorts.head.isWrite   := write
    ram.readwritePorts.head.writeData := result
  }

  val deqQueue = Queue.io(UInt((parameter.elen * 2).W), 8)
  deqQueue.enq.valid := pipeValid2 && !writeState2
  deqQueue.enq.bits  := resultR2

  io.response <> deqQueue.deq
}
