// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{Instance, Instantiate, instantiable, public}
import chisel3.probe.{Probe, ProbeValue, define}
import chisel3.util._
import chisel3.ltl._
import chisel3.ltl.Sequence._
import chisel3.util.experimental.decode.DecodeBundle
import org.chipsalliance.t1.rtl.decoder.Decoder
import org.chipsalliance.t1.rtl.lane.{CrossReadUnit, LaneState, VrfReadPipe}

class LaneStage1Enqueue(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  val groupCounter: UInt = UInt(parameter.groupNumberBits.W)
  val maskForMaskInput: UInt = UInt((parameter.datapathWidth / 8).W)
  val boundaryMaskCorrection: UInt = UInt((parameter.datapathWidth / 8).W)
  val sSendResponse: Option[Bool] = Option.when(isLastSlot)(Bool())
  // pipe state
  val instructionIndex: UInt = UInt(parameter.instructionIndexBits.W)
  val decodeResult: DecodeBundle = Decoder.bundle(parameter.decoderParam)
  val laneIndex: UInt = UInt(parameter.laneNumberBits.W)
  // skip vrf read in stage 1?
  val skipRead: Bool = Bool()
  val vs1: UInt = UInt(5.W)
  val vs2: UInt = UInt(5.W)
  val vd: UInt = UInt(5.W)
  val vSew1H: UInt = UInt(3.W)
  val maskNotMaskedElement: Bool = Bool()

  // pipe state
  val csr: CSRInterface = new CSRInterface(parameter.vlMaxBits)
  val maskType: Bool = Bool()
  val loadStore: Bool = Bool()
  val readFromScalar: UInt = UInt(parameter.datapathWidth.W)
  val bordersForMaskLogic: Bool = Bool()
}

class LaneStage1Dequeue(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  /** for dequeue group counter match */
  val readBusDequeueGroup: Option[UInt] = Option.when(isLastSlot)(UInt(parameter.groupNumberBits.W))
  val maskForFilter: UInt = UInt((parameter.datapathWidth / 8).W)
  val mask: UInt = UInt((parameter.datapathWidth / 8).W)
  val groupCounter: UInt = UInt(parameter.groupNumberBits.W)
  val sSendResponse: Option[Bool] = Option.when(isLastSlot)(Bool())
  // read result
  val src: Vec[UInt] = Vec(3, UInt(parameter.datapathWidth.W))
  val crossReadSource: Option[UInt] = Option.when(isLastSlot)(UInt((parameter.datapathWidth * 2).W))

  // pipe state
  // for exe stage
  val decodeResult: DecodeBundle = Decoder.bundle(parameter.decoderParam)
  val vSew1H: UInt = UInt(3.W)
  val csr: CSRInterface = new CSRInterface(parameter.vlMaxBits)
  val maskType: Bool = Bool()
  // Newly added in LaneExecutionBridge
  val laneIndex: UInt = UInt(parameter.laneNumberBits.W)

  // for stage3
  val instructionIndex: UInt = UInt(parameter.instructionIndexBits.W)
  val loadStore: Bool = Bool()
  /** vd or rd */
  val vd: UInt = UInt(5.W)
  val bordersForMaskLogic: Bool = Bool()
}

/** 这一个stage 分两级流水, 分别是 读vrf 等vrf结果
 * */
@instantiable
class LaneStage1(parameter: LaneParameter, isLastSlot: Boolean) extends Module {
  val readRequestType: VRFReadRequest =
    new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
  @public
  val enqueue = IO(Flipped(Decoupled(new LaneStage1Enqueue(parameter, isLastSlot))))
  @public
  val dequeue = IO(Decoupled(new LaneStage1Dequeue(parameter, isLastSlot)))
  @public
  val stageValid = IO(Output(Bool()))
  @public
  val vrfReadRequest: Vec[DecoupledIO[VRFReadRequest]] = IO(Vec(3, Decoupled(readRequestType)))

  val readCheckSize: Int = if(isLastSlot) 5 else 3
  @public
  val vrfCheckRequest: Vec[VRFReadRequest] = IO(Vec(readCheckSize, Output(readRequestType)))

  @public
  val checkResult: Vec[Bool] = IO(Vec(readCheckSize, Input(Bool())))

  /** VRF read result for each slot,
   * 3 is for [[source1]] [[source2]] [[source3]]
   */
  @public
  val vrfReadResult: Vec[UInt] = IO(Input(Vec(3, UInt(parameter.datapathWidth.W))))

  @public
  val readBusDequeue: Option[Vec[DecoupledIO[ReadBusData]]] = Option.when(isLastSlot)(IO(
    Vec(2, Flipped(Decoupled(new ReadBusData(parameter: LaneParameter))))
  ))

  @public
  val readBusRequest: Option[Vec[DecoupledIO[ReadBusData]]] =
    Option.when(isLastSlot)(IO(Vec(2, Decoupled(new ReadBusData(parameter)))))

  val groupCounter: UInt = enqueue.bits.groupCounter

  // todo: param
  val readRequestQueueSizeBeforeCheck: Int = 4
  val readRequestQueueSizeAfterCheck: Int = 4
  val dataQueueSize: Int = 4
  val vrfReadEntryType = new VRFReadQueueEntry(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits)

  // read request queue for vs1 vs2 vd
  val queueAfterCheck1: Queue[VRFReadQueueEntry] = Module(new Queue(vrfReadEntryType, readRequestQueueSizeAfterCheck))
  val queueAfterCheck2: Queue[VRFReadQueueEntry] = Module(new Queue(vrfReadEntryType, readRequestQueueSizeAfterCheck))
  val queueAfterCheckVd: Queue[VRFReadQueueEntry] = Module(new Queue(vrfReadEntryType, readRequestQueueSizeAfterCheck))

  // read request queue for vs1 vs2 vd
  val queueBeforeCheck1: Queue[VRFReadQueueEntry] = Module(new Queue(vrfReadEntryType, readRequestQueueSizeBeforeCheck))
  val queueBeforeCheck2: Queue[VRFReadQueueEntry] = Module(new Queue(vrfReadEntryType, readRequestQueueSizeBeforeCheck))
  val queueBeforeCheckVd: Queue[VRFReadQueueEntry] = Module(new Queue(vrfReadEntryType, readRequestQueueSizeBeforeCheck))

  // read request queue for cross read lsb & msb
  val queueAfterCheckLSB: Option[Queue[VRFReadQueueEntry]] =
    Option.when(isLastSlot)(Module(new Queue(vrfReadEntryType, readRequestQueueSizeAfterCheck)))
  val queueAfterCheckMSB: Option[Queue[VRFReadQueueEntry]] =
    Option.when(isLastSlot)(Module(new Queue(vrfReadEntryType, readRequestQueueSizeAfterCheck)))

  // read request queue for cross read lsb & msb
  val queueBeforeCheckLSB: Option[Queue[VRFReadQueueEntry]] =
    Option.when(isLastSlot)(Module(new Queue(vrfReadEntryType, readRequestQueueSizeBeforeCheck)))
  val queueBeforeCheckMSB: Option[Queue[VRFReadQueueEntry]] =
    Option.when(isLastSlot)(Module(new Queue(vrfReadEntryType, readRequestQueueSizeBeforeCheck)))

  // pipe from enqueue
  val pipeQueue: Queue[LaneStage1Enqueue] =
    Module(
      new Queue(chiselTypeOf(enqueue.bits),
        readRequestQueueSizeBeforeCheck + readRequestQueueSizeAfterCheck + dataQueueSize + 2
      ))
  pipeQueue.io.enq.bits := enqueue.bits
  pipeQueue.io.enq.valid := enqueue.fire
  pipeQueue.io.deq.ready := dequeue.fire

  val beforeCheckQueueVec: Seq[Queue[VRFReadQueueEntry]] =
    Seq(queueBeforeCheck1, queueBeforeCheck2, queueBeforeCheckVd) ++
      queueBeforeCheckLSB ++ queueBeforeCheckMSB
  val afterCheckQueueVec: Seq[Queue[VRFReadQueueEntry]] =
    Seq(queueAfterCheck1, queueAfterCheck2, queueAfterCheckVd) ++
      queueAfterCheckLSB ++ queueAfterCheckMSB
  val allReadQueueReady: Bool = beforeCheckQueueVec.map(_.io.enq.ready).reduce(_ && _)
  beforeCheckQueueVec.foreach{ q =>
    q.io.enq.bits.instructionIndex := enqueue.bits.instructionIndex
    q.io.enq.bits.groupIndex := enqueue.bits.groupCounter
  }

  enqueue.ready := allReadQueueReady && pipeQueue.io.enq.ready

  // chaining check
  beforeCheckQueueVec.zip(afterCheckQueueVec).zipWithIndex.foreach { case ((before, after), i) =>
    vrfCheckRequest(i) := before.io.deq.bits
    before.io.deq.ready := after.io.enq.ready && checkResult(i)
    after.io.enq.valid := before.io.deq.valid  && checkResult(i)
    after.io.enq.bits := before.io.deq.bits
  }
  // request enqueue
  queueBeforeCheck1.io.enq.valid := enqueue.fire && enqueue.bits.decodeResult(Decoder.vtype) && !enqueue.bits.skipRead
  queueBeforeCheck2.io.enq.valid := enqueue.fire && !enqueue.bits.skipRead
  queueBeforeCheckVd.io.enq.valid := enqueue.fire && !enqueue.bits.decodeResult(Decoder.sReadVD)
  (queueBeforeCheckLSB ++ queueBeforeCheckMSB).foreach { q =>
    q.io.enq.valid := enqueue.valid && allReadQueueReady && enqueue.bits.decodeResult(Decoder.crossRead)
  }

  // calculate vs
  queueBeforeCheck1.io.enq.bits.vs := Mux(
    // encodings with vm=0 are reserved for mask type logic
    enqueue.bits.decodeResult(Decoder.maskLogic) && !enqueue.bits.decodeResult(Decoder.logic),
    // read v0 for (15. Vector Mask Instructions)
    0.U,
    enqueue.bits.vs1 + groupCounter(
      parameter.groupNumberBits - 1,
      parameter.vrfOffsetBits
    )
  )
  queueBeforeCheck1.io.enq.bits.readSource := Mux(
  enqueue.bits.decodeResult(Decoder.maskLogic) && !enqueue.bits.decodeResult(Decoder.logic),
    3.U,
    0.U
  )
  queueBeforeCheck2.io.enq.bits.vs := enqueue.bits.vs2 +
    groupCounter(parameter.groupNumberBits - 1, parameter.vrfOffsetBits)
  queueBeforeCheck2.io.enq.bits.readSource := 1.U
  queueBeforeCheckVd.io.enq.bits.vs := enqueue.bits.vd +
    groupCounter(parameter.groupNumberBits - 1, parameter.vrfOffsetBits)
  queueBeforeCheckVd.io.enq.bits.readSource := 2.U

  // calculate offset
  queueBeforeCheck1.io.enq.bits.offset := groupCounter(parameter.vrfOffsetBits - 1, 0)
  queueBeforeCheck2.io.enq.bits.offset := groupCounter(parameter.vrfOffsetBits - 1, 0)
  queueBeforeCheckVd.io.enq.bits.offset := groupCounter(parameter.vrfOffsetBits - 1, 0)

  // cross read enqueue
  queueBeforeCheckLSB.foreach { q =>
    q.io.enq.bits.vs := Mux(
      enqueue.bits.decodeResult(Decoder.vwmacc),
      // cross read vd for vwmacc, since it need dual [[dataPathWidth]], use vs2 port to read LSB part of it.
      enqueue.bits.vd,
      // read vs2 for other instruction
      enqueue.bits.vs2
    ) + groupCounter(parameter.groupNumberBits - 2, parameter.vrfOffsetBits - 1)
    q.io.enq.bits.readSource := Mux(enqueue.bits.decodeResult(Decoder.vwmacc), 2.U, 1.U)
    q.io.enq.bits.offset := groupCounter(parameter.vrfOffsetBits - 2, 0) ## false.B
  }

  queueBeforeCheckMSB.foreach { q =>
    q.io.enq.bits.vs := Mux(
      enqueue.bits.decodeResult(Decoder.vwmacc),
      // cross read vd for vwmacc
      enqueue.bits.vd,
      // cross lane access use vs2
      enqueue.bits.vs2
    ) + groupCounter(parameter.groupNumberBits - 2, parameter.vrfOffsetBits - 1)
    q.io.enq.bits.readSource := Mux(enqueue.bits.decodeResult(Decoder.vwmacc), 2.U, 1.U)
    q.io.enq.bits.offset := groupCounter(parameter.vrfOffsetBits - 2, 0) ## true.B
  }

  // read pipe
  val readPipe0: Instance[VrfReadPipe] = Instantiate(new VrfReadPipe(parameter, arbitrate = false))
  val readPipe1: Instance[VrfReadPipe] = Instantiate(new VrfReadPipe(parameter, arbitrate = isLastSlot))
  val readPipe2: Instance[VrfReadPipe] = Instantiate(new VrfReadPipe(parameter, arbitrate = isLastSlot))
  val pipeVec: Seq[Instance[VrfReadPipe]] = Seq(readPipe0, readPipe1, readPipe2)

  // read port connect
  vrfReadRequest.zip(pipeVec).foreach { case (port, pipe) => port <> pipe.vrfReadRequest }
  vrfReadResult.zip(pipeVec).foreach { case (result, pipe) => pipe.vrfReadResult := result }

  val dataQueueVs1: DecoupledIO[UInt] = Queue(readPipe0.dequeue, dataQueueSize)
  val dataQueueVs2: Queue[UInt] = Module(new Queue(UInt(parameter.datapathWidth.W), dataQueueSize))
  val dataQueueVd: Queue[UInt] = Module(new Queue(UInt(parameter.datapathWidth.W), dataQueueSize))

  // cross lane queue
  val dataQueueLSB = Option.when(isLastSlot)(Module(new Queue(UInt(parameter.datapathWidth.W), dataQueueSize)))
  val dataQueueMSB = Option.when(isLastSlot)(Module(new Queue(UInt(parameter.datapathWidth.W), dataQueueSize)))

  val dataQueueNotFull2: Bool = {
    val counterReg = RegInit(0.U(log2Ceil(dataQueueSize + 1).W))
    val doEnq = queueAfterCheck2.io.deq.fire
    val doDeq = dataQueueVs2.io.deq.fire
    val countChange = Mux(doEnq, 1.U, -1.S(log2Ceil(dataQueueSize + 1).W).asUInt)
    when(doEnq ^ doDeq) {
      counterReg := counterReg + countChange
    }
    !counterReg(log2Ceil(dataQueueSize))
  }

  val dataQueueNotFullVd: Bool = {
    val counterReg = RegInit(0.U(log2Ceil(dataQueueSize + 1).W))
    val doEnq = queueAfterCheckVd.io.deq.fire
    val doDeq = dataQueueVd.io.deq.fire
    val countChange = Mux(doEnq, 1.U, -1.S(log2Ceil(dataQueueSize + 1).W).asUInt)
    when(doEnq ^ doDeq) {
      counterReg := counterReg + countChange
    }
    !counterReg(log2Ceil(dataQueueSize))
  }

  readPipe0.enqueue <> queueAfterCheck1.io.deq
  blockingHandshake(readPipe1.enqueue, queueAfterCheck2.io.deq, dataQueueNotFull2)
  blockingHandshake(readPipe2.enqueue, queueAfterCheckVd.io.deq, dataQueueNotFullVd)

  // contender for cross read
  readPipe1.contender.zip(queueAfterCheckLSB).foreach { case (port, queue) =>
    val dataQueueNotFullLSB: Bool = {
      val counterReg = RegInit(0.U(log2Ceil(dataQueueSize + 1).W))
      val doEnq = queue.io.deq.fire
      val doDeq = dataQueueLSB.get.io.deq.fire
      val countChange = Mux(doEnq, 1.U, -1.S(log2Ceil(dataQueueSize + 1).W).asUInt)
      when(doEnq ^ doDeq) {
        counterReg := counterReg + countChange
      }
      !counterReg(log2Ceil(dataQueueSize))
    }
    blockingHandshake(port, queue.io.deq, dataQueueNotFullLSB)
  }
  readPipe2.contender.zip(queueAfterCheckMSB).foreach { case (port, queue) =>
    val dataQueueNotFullMSB: Bool = {
      val counterReg = RegInit(0.U(log2Ceil(dataQueueSize + 1).W))
      val doEnq = queue.io.deq.fire
      val doDeq = dataQueueMSB.get.io.deq.fire
      val countChange = Mux(doEnq, 1.U, -1.S(log2Ceil(dataQueueSize + 1).W).asUInt)
      when(doEnq ^ doDeq) {
        counterReg := counterReg + countChange
      }
      !counterReg(log2Ceil(dataQueueSize))
    }
    blockingHandshake(port, queue.io.deq, dataQueueNotFullMSB)
  }

  // data: pipe <-> queue
  if (isLastSlot) {
    // pipe1 <-> dataQueueVs2
    dataQueueVs2.io.enq <> readPipe1.dequeue
    // pipe1 <> dataQueueLSB
    dataQueueLSB.zip(readPipe1.contenderDequeue).foreach { case (sink, source) => sink.io.enq <> source }

    // pipe2 <-> dataQueueVd
    dataQueueVd.io.enq <> readPipe2.dequeue
    // pipe2 <-> dataQueueMSB
    dataQueueMSB.zip(readPipe2.contenderDequeue).foreach { case (sink, source) => sink.io.enq <> source }
  } else {
    dataQueueVs2.io.enq <> readPipe1.dequeue
    dataQueueVd.io.enq <> readPipe2.dequeue
  }

  // cross read data queue(before cross) <-> cross read unit <-> cross read data queue(after cross)
  val crossReadResultQueue: Option[Queue[UInt]] =
    Option.when(isLastSlot)(Module(new Queue(UInt((parameter.datapathWidth * 2).W), 1)))
  val crossReadStageFree: Option[Bool] = Option.when(isLastSlot)(Wire(Bool()))
  val crossReadUnitOp: Option[Instance[CrossReadUnit]] = Option.when(isLastSlot)(Instantiate(new CrossReadUnit(parameter)))
  if (isLastSlot) {
    val dataGroupQueue: Queue[UInt] =
      Module(
        new Queue(
          UInt(parameter.groupNumberBits.W),
          readRequestQueueSizeBeforeCheck + readRequestQueueSizeBeforeCheck + dataQueueSize + 2
        )
      )
    // todo: need pipe ?
    val laneIndexReg = RegInit(enqueue.bits.laneIndex)
    val crossReadUnit = crossReadUnitOp.get
    crossReadUnit.dataInputLSB <> dataQueueLSB.get.io.deq
    crossReadUnit.dataInputMSB <> dataQueueMSB.get.io.deq
    crossReadUnit.laneIndex := laneIndexReg
    crossReadUnit.dataGroup := dataGroupQueue.io.deq.bits
    readBusRequest.get.zip(crossReadUnit.readBusRequest).foreach { case (sink, source) => sink <> source}
    crossReadUnit.readBusDequeue.zip(readBusDequeue.get).foreach { case (sink, source) => sink <> source}
    crossReadResultQueue.get.io.enq <> crossReadUnit.crossReadDequeue
    crossReadStageFree.get := crossReadUnit.crossReadStageFree

    // data group
    dataGroupQueue.io.enq.valid := enqueue.fire && enqueue.bits.decodeResult(Decoder.crossRead)
    AssertProperty(BoolSequence(dataGroupQueue.io.enq.ready || !dataGroupQueue.io.enq.valid))
    dataGroupQueue.io.enq.bits := enqueue.bits.groupCounter
    dataGroupQueue.io.deq.ready := crossReadUnit.dataInputLSB.fire
    dequeue.bits.readBusDequeueGroup.get := crossReadUnitOp.get.currentGroup
  }

  val source1Select: UInt = Mux(
    pipeQueue.io.deq.bits.decodeResult(Decoder.vtype),
    dataQueueVs1.bits,
    pipeQueue.io.deq.bits.readFromScalar
  )
  dequeue.bits.mask := pipeQueue.io.deq.bits.maskForMaskInput
  dequeue.bits.groupCounter := pipeQueue.io.deq.bits.groupCounter
  dequeue.bits.src := VecInit(Seq(source1Select, dataQueueVs2.io.deq.bits, dataQueueVd.io.deq.bits))
  dequeue.bits.crossReadSource.foreach(_ := crossReadResultQueue.get.io.deq.bits)
  dequeue.bits.sSendResponse.foreach(_ := pipeQueue.io.deq.bits.sSendResponse.get)
  dequeue.bits.decodeResult := pipeQueue.io.deq.bits.decodeResult
  dequeue.bits.vSew1H := pipeQueue.io.deq.bits.vSew1H
  dequeue.bits.csr := pipeQueue.io.deq.bits.csr
  dequeue.bits.maskType := pipeQueue.io.deq.bits.maskType
  dequeue.bits.laneIndex := pipeQueue.io.deq.bits.laneIndex
  dequeue.bits.instructionIndex := pipeQueue.io.deq.bits.instructionIndex
  dequeue.bits.loadStore := pipeQueue.io.deq.bits.loadStore
  dequeue.bits.vd := pipeQueue.io.deq.bits.vd
  dequeue.bits.bordersForMaskLogic := pipeQueue.io.deq.bits.bordersForMaskLogic

  dequeue.bits.maskForFilter :=
    (FillInterleaved(4, pipeQueue.io.deq.bits.maskNotMaskedElement) | pipeQueue.io.deq.bits.maskForMaskInput) &
      pipeQueue.io.deq.bits.boundaryMaskCorrection
  // All required data is ready
  val dataQueueValidVec: Seq[Bool] =
    Seq(
      dataQueueVs1.valid || !pipeQueue.io.deq.bits.decodeResult(Decoder.vtype) || pipeQueue.io.deq.bits.skipRead,
      dataQueueVs2.io.deq.valid || pipeQueue.io.deq.bits.skipRead,
      dataQueueVd.io.deq.valid || (pipeQueue.io.deq.bits.decodeResult(Decoder.sReadVD))
    ) ++
      crossReadResultQueue.map(_.io.deq.valid || !pipeQueue.io.deq.bits.decodeResult(Decoder.crossRead))
  val allDataQueueValid: Bool = VecInit(dataQueueValidVec).asUInt.andR
  dequeue.valid := allDataQueueValid && pipeQueue.io.deq.valid
  dataQueueVs1.ready := allDataQueueValid && dequeue.ready && pipeQueue.io.deq.bits.decodeResult(Decoder.vtype)
  dataQueueVs2.io.deq.ready := allDataQueueValid && dequeue.ready && !pipeQueue.io.deq.bits.skipRead
  dataQueueVd.io.deq.ready :=
    allDataQueueValid && dequeue.ready && !pipeQueue.io.deq.bits.decodeResult(Decoder.sReadVD)
  crossReadResultQueue.foreach(_.io.deq.ready := allDataQueueValid && dequeue.ready && pipeQueue.io.deq.bits.decodeResult(Decoder.crossRead))
  stageValid := pipeQueue.io.deq.valid
  val stageFinish = !stageValid

  @public
  val dequeueReadyProbe = IO(Output(Probe(Bool())))
  @public
  val dequeueValidProbe = IO(Output(Probe(Bool())))
  @public
  val hasDataOccupiedProbe = IO(Output(Probe(Bool())))
  @public
  val stageFinishProbe = IO(Output(Probe(Bool())))
  @public
  val readFinishProbe = Option.when(isLastSlot)(IO(Output(Probe(Bool()))))
  @public
  val sSendCrossReadResultLSBProbe = Option.when(isLastSlot)(IO(Output(Probe(Bool()))))
  @public
  val sSendCrossReadResultMSBProbe = Option.when(isLastSlot)(IO(Output(Probe(Bool()))))
  @public
  val wCrossReadLSBProbe = Option.when(isLastSlot)(IO(Output(Probe(Bool()))))
  @public
  val wCrossReadMSBProbe = Option.when(isLastSlot)(IO(Output(Probe(Bool()))))
  @public
  val vrfReadRequestProbe: Seq[(Bool, Bool)] = Seq.fill(3)((IO(Output(Probe(Bool()))),IO(Output(Probe(Bool())))))


  define(dequeueReadyProbe, ProbeValue(dequeue.ready))
  define(dequeueValidProbe, ProbeValue(dequeue.valid))
  define(hasDataOccupiedProbe, ProbeValue(stageValid))
  define(stageFinishProbe, ProbeValue(stageFinish))

  if (isLastSlot) {
    readFinishProbe.foreach(p => define(p, ProbeValue(dataQueueVs2.io.deq.valid)))
    sSendCrossReadResultLSBProbe.foreach(p => define(p, ProbeValue(crossReadUnitOp.get.crossWriteState.sSendCrossReadResultLSB)))
    sSendCrossReadResultMSBProbe.foreach(p => define(p, ProbeValue(crossReadUnitOp.get.crossWriteState.sSendCrossReadResultMSB)))
    wCrossReadLSBProbe.foreach(p => define(p, ProbeValue(crossReadUnitOp.get.crossWriteState.wCrossReadLSB)))
    wCrossReadMSBProbe.foreach(p => define(p, ProbeValue(crossReadUnitOp.get.crossWriteState.wCrossReadMSB)))
  }

  vrfReadRequestProbe.zipWithIndex.foreach { case((ready, valid), i) =>
    define(ready, ProbeValue(vrfReadRequest(i).ready))
    define(valid, ProbeValue(vrfReadRequest(i).valid))
  }
}
