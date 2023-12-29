// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle
import chisel3.probe.{Probe, ProbeValue, define}

class LaneStage1Enqueue(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  val groupCounter: UInt = UInt(parameter.groupNumberBits.W)
  val mask: UInt = UInt((parameter.datapathWidth / 8).W)
  val sSendResponse: Option[Bool] = Option.when(isLastSlot)(Bool())
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
}

/** 这一个stage 分两级流水, 分别是 读vrf 等vrf结果
 * */
class LaneStage1(parameter: LaneParameter, isLastSlot: Boolean) extends Module {
  val enqueue = IO(Flipped(Decoupled(new LaneStage1Enqueue(parameter, isLastSlot))))
  val dequeue = IO(Decoupled(new LaneStage1Dequeue(parameter, isLastSlot)))
  val stageValid = IO(Output(Bool()))
  val state: LaneState = IO(Input(new LaneState(parameter)))
  val vrfReadRequest: Vec[DecoupledIO[VRFReadRequest]] = IO(
    Vec(
      3,
      Decoupled(
        new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
      )
    )
  )

  /** VRF read result for each slot,
   * 3 is for [[source1]] [[source2]] [[source3]]
   */
  val vrfReadResult: Vec[UInt] = IO(Input(Vec(3, UInt(parameter.datapathWidth.W))))

  val readBusDequeue: Option[Vec[DecoupledIO[ReadBusData]]] = Option.when(isLastSlot)(IO(
    Vec(2, Flipped(Decoupled(new ReadBusData(parameter: LaneParameter))))
  ))

  val readBusRequest: Option[Vec[DecoupledIO[ReadBusData]]] =
    Option.when(isLastSlot)(IO(Vec(2, Decoupled(new ReadBusData(parameter)))))
  val readFromScalar: UInt = IO(Input(UInt(parameter.datapathWidth.W)))

  val notNeedMaskedWrite: Bool = Mux1H(state.vSew1H, Seq(
    enqueue.bits.mask.andR,
    enqueue.bits.mask(1, 0).andR,
    true.B,
  )) || state.maskNotMaskedElement
  val groupCounter: UInt = enqueue.bits.groupCounter

  // todo: param
  val readRequestQueueSize: Int = 4
  val dataQueueSize: Int = 4
  val vrfReadEntryType = new VRFReadQueueEntry(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits)

  // read request queue for vs1 vs2 vd
  val readRequestQueueVs1: Queue[VRFReadQueueEntry] = Module(new Queue(vrfReadEntryType, readRequestQueueSize))
  val readRequestQueueVs2: Queue[VRFReadQueueEntry] = Module(new Queue(vrfReadEntryType, readRequestQueueSize))
  val readRequestQueueVd: Queue[VRFReadQueueEntry] = Module(new Queue(vrfReadEntryType, readRequestQueueSize))

  // read request queue for cross read lsb & msb
  val readRequestQueueLSB: Option[Queue[VRFReadQueueEntry]] =
    Option.when(isLastSlot)(Module(new Queue(vrfReadEntryType, readRequestQueueSize)))
  val readRequestQueueMSB: Option[Queue[VRFReadQueueEntry]] =
    Option.when(isLastSlot)(Module(new Queue(vrfReadEntryType, readRequestQueueSize)))

  // pipe from enqueue
  val pipeQueue: Queue[LaneStage1Enqueue] =
    Module(new Queue(chiselTypeOf(enqueue.bits), readRequestQueueSize + dataQueueSize + 2))
  pipeQueue.io.enq.bits := enqueue.bits
  pipeQueue.io.enq.valid := enqueue.fire
  pipeQueue.io.deq.ready := dequeue.fire
  assert(pipeQueue.io.enq.ready || !pipeQueue.io.enq.valid)

  val readQueueVec: Seq[Queue[VRFReadQueueEntry]] =
    Seq(readRequestQueueVs1, readRequestQueueVs2, readRequestQueueVd) ++
      readRequestQueueLSB ++ readRequestQueueMSB
  val allReadQueueReady: Bool = readQueueVec.map(_.io.enq.ready).reduce(_ && _)
  val allReadQueueEmpty: Bool = readQueueVec.map(!_.io.deq.valid).reduce(_ && _)
  enqueue.ready := allReadQueueReady

  // request enqueue
  readRequestQueueVs1.io.enq.valid := enqueue.valid && allReadQueueReady && state.decodeResult(Decoder.vtype)
  readRequestQueueVs2.io.enq.valid := enqueue.valid && allReadQueueReady
  readRequestQueueVd.io.enq.valid := enqueue.valid && allReadQueueReady &&
    !(state.decodeResult(Decoder.sReadVD) && notNeedMaskedWrite)
  (readRequestQueueLSB ++ readRequestQueueMSB).foreach { q =>
    q.io.enq.valid := enqueue.valid && allReadQueueReady && state.decodeResult(Decoder.crossRead)
  }

  // calculate vs
  readRequestQueueVs1.io.enq.bits.vs := Mux(
    // encodings with vm=0 are reserved for mask type logic
    state.decodeResult(Decoder.maskLogic) && !state.decodeResult(Decoder.logic),
    // read v0 for (15. Vector Mask Instructions)
    0.U,
    state.vs1 + groupCounter(
      parameter.groupNumberBits - 1,
      parameter.vrfOffsetBits
    )
  )
  readRequestQueueVs1.io.enq.bits.readSource := Mux(
    state.decodeResult(Decoder.maskLogic) && !state.decodeResult(Decoder.logic),
    3.U,
    0.U
  )
  readRequestQueueVs2.io.enq.bits.vs := state.vs2 +
    groupCounter(parameter.groupNumberBits - 1, parameter.vrfOffsetBits)
  readRequestQueueVs2.io.enq.bits.readSource := 1.U
  readRequestQueueVd.io.enq.bits.vs := state.vd +
    groupCounter(parameter.groupNumberBits - 1, parameter.vrfOffsetBits)
  readRequestQueueVd.io.enq.bits.readSource := 2.U

  // calculate offset
  readRequestQueueVs1.io.enq.bits.offset := groupCounter(parameter.vrfOffsetBits - 1, 0)
  readRequestQueueVs2.io.enq.bits.offset := groupCounter(parameter.vrfOffsetBits - 1, 0)
  readRequestQueueVd.io.enq.bits.offset := groupCounter(parameter.vrfOffsetBits - 1, 0)

  // cross read enqueue
  readRequestQueueLSB.foreach { q =>
    q.io.enq.bits.vs := Mux(
      state.decodeResult(Decoder.vwmacc),
      // cross read vd for vwmacc, since it need dual [[dataPathWidth]], use vs2 port to read LSB part of it.
      state.vd,
      // read vs2 for other instruction
      state.vs2
    ) + groupCounter(parameter.groupNumberBits - 2, parameter.vrfOffsetBits - 1)
    q.io.enq.bits.readSource := Mux(state.decodeResult(Decoder.vwmacc), 2.U, 1.U)
    q.io.enq.bits.offset := groupCounter(parameter.vrfOffsetBits - 2, 0) ## false.B
  }

  readRequestQueueMSB.foreach { q =>
    q.io.enq.bits.vs := Mux(
      state.decodeResult(Decoder.vwmacc),
      // cross read vd for vwmacc
      state.vd,
      // cross lane access use vs2
      state.vs2
    ) + groupCounter(parameter.groupNumberBits - 2, parameter.vrfOffsetBits - 1)
    q.io.enq.bits.readSource := Mux(state.decodeResult(Decoder.vwmacc), 2.U, 1.U)
    q.io.enq.bits.offset := groupCounter(parameter.vrfOffsetBits - 2, 0) ## true.B
  }

  // todo: for debug
  readQueueVec.foreach {q => q.io.enq.bits.groupIndex := enqueue.bits.groupCounter}

  // read pipe
  val readPipe0: VrfReadPipe = Module(new VrfReadPipe(parameter, arbitrate = false))
  val readPipe1: VrfReadPipe = Module(new VrfReadPipe(parameter, arbitrate = isLastSlot))
  val readPipe2: VrfReadPipe = Module(new VrfReadPipe(parameter, arbitrate = isLastSlot))
  val pipeVec: Seq[VrfReadPipe] = Seq(readPipe0, readPipe1, readPipe2)

  readPipe0.enqueue <> readRequestQueueVs1.io.deq
  readPipe1.enqueue <> readRequestQueueVs2.io.deq
  readPipe2.enqueue <> readRequestQueueVd.io.deq

  // contender for cross read
  readPipe1.contender.zip(readRequestQueueLSB).foreach { case (port, queue) => port <> queue.io.deq }
  readPipe2.contender.zip(readRequestQueueMSB).foreach { case (port, queue) => port <> queue.io.deq }

  // read port connect
  vrfReadRequest.zip(pipeVec).foreach { case (port, pipe) => port <> pipe.vrfReadRequest }
  vrfReadResult.zip(pipeVec).foreach { case (result, pipe) => pipe.vrfReadResult := result }
  // replace instructionIndex
  vrfReadRequest.foreach(_.bits.instructionIndex := state.instructionIndex)

  val dataQueueVs1: DecoupledIO[UInt] = Queue(readPipe0.dequeue, dataQueueSize)
  val dataQueueVs2: Queue[UInt] = Module(new Queue(UInt(parameter.datapathWidth.W), dataQueueSize))
  val dataQueueVd: Queue[UInt] = Module(new Queue(UInt(parameter.datapathWidth.W), dataQueueSize))

  // cross lane queue
  val dataQueueLSB = Option.when(isLastSlot)(Module(new Queue(UInt(parameter.datapathWidth.W), dataQueueSize)))
  val dataQueueMSB = Option.when(isLastSlot)(Module(new Queue(UInt(parameter.datapathWidth.W), dataQueueSize)))

  // data: pipe <-> queue
  if (isLastSlot) {
    // pipe1 <-> dataQueueVs2
    dataQueueVs2.io.enq.valid := readPipe1.dequeue.valid && readPipe1.dequeueChoose.get
    dataQueueVs2.io.enq.bits := readPipe1.dequeue.bits
    // pipe1 <> dataQueueLSB
    dataQueueLSB.get.io.enq.valid := readPipe1.dequeue.valid && !readPipe1.dequeueChoose.get
    dataQueueLSB.get.io.enq.bits := readPipe1.dequeue.bits
    // ready select
    readPipe1.dequeue.ready :=
      Mux(readPipe1.dequeueChoose.get, dataQueueVs2.io.enq.ready, dataQueueLSB.get.io.enq.ready)

    // pipe2 <-> dataQueueVd
    dataQueueVd.io.enq.valid := readPipe2.dequeue.valid && readPipe2.dequeueChoose.get
    dataQueueVd.io.enq.bits := readPipe2.dequeue.bits
    // pipe2 <-> dataQueueMSB
    dataQueueMSB.get.io.enq.valid := readPipe2.dequeue.valid && !readPipe2.dequeueChoose.get
    dataQueueMSB.get.io.enq.bits := readPipe2.dequeue.bits
    // ready select
    readPipe2.dequeue.ready :=
      Mux(readPipe2.dequeueChoose.get, dataQueueVd.io.enq.ready, dataQueueMSB.get.io.enq.ready)
  } else {
    dataQueueVs2.io.enq <> readPipe1.dequeue
    dataQueueVd.io.enq <> readPipe2.dequeue
  }

  // cross read data queue(before cross) <-> cross read unit <-> cross read data queue(after cross)
  val crossReadResultQueue: Option[Queue[UInt]] =
    Option.when(isLastSlot)(Module(new Queue(UInt((parameter.datapathWidth * 2).W), 1)))
  val crossReadStageFree: Option[Bool] = Option.when(isLastSlot)(Wire(Bool()))
  val crossReadUnitOp: Option[CrossReadUnit] = Option.when(isLastSlot)(Module(new CrossReadUnit(parameter)))
  if (isLastSlot) {
    val dataGroupQueue: Queue[UInt] =
      Module(new Queue(UInt(parameter.groupNumberBits.W), readRequestQueueSize + dataQueueSize + 2))
    val crossReadUnit = crossReadUnitOp.get
    crossReadUnit.dataInputLSB <> dataQueueLSB.get.io.deq
    crossReadUnit.dataInputMSB <> dataQueueMSB.get.io.deq
    crossReadUnit.laneIndex := state.laneIndex
    crossReadUnit.dataGroup := dataGroupQueue.io.deq.bits
    readBusRequest.get.zip(crossReadUnit.readBusRequest).foreach { case (sink, source) => sink <> source}
    crossReadUnit.readBusDequeue.zip(readBusDequeue.get).foreach { case (sink, source) => sink <> source}
    crossReadResultQueue.get.io.enq <> crossReadUnit.crossReadDequeue
    crossReadStageFree.get := crossReadUnit.crossReadStageFree

    // data group
    dataGroupQueue.io.enq.valid := enqueue.fire && state.decodeResult(Decoder.crossRead)
    assert(dataGroupQueue.io.enq.ready || !dataGroupQueue.io.enq.valid)
    dataGroupQueue.io.enq.bits := enqueue.bits.groupCounter
    dataGroupQueue.io.deq.ready := crossReadUnit.dataInputLSB.fire
    dequeue.bits.readBusDequeueGroup.get := crossReadUnitOp.get.currentGroup
  }

  val scalarDataRepeat: UInt = Mux1H(
    state.vSew1H,
    Seq(
      Fill(4, readFromScalar(7, 0)),
      Fill(2, readFromScalar(15, 0)),
      readFromScalar
    )
  )

  val source1Select: UInt = Mux(state.decodeResult(Decoder.vtype), dataQueueVs1.bits, scalarDataRepeat)
  dequeue.bits.mask := pipeQueue.io.deq.bits.mask
  dequeue.bits.groupCounter := pipeQueue.io.deq.bits.groupCounter
  dequeue.bits.src := VecInit(Seq(source1Select, dataQueueVs2.io.deq.bits, dataQueueVd.io.deq.bits))
  dequeue.bits.crossReadSource.foreach(_ := crossReadResultQueue.get.io.deq.bits)
  dequeue.bits.sSendResponse.foreach(_ := pipeQueue.io.deq.bits.sSendResponse.get)

  dequeue.bits.maskForFilter := FillInterleaved(4, state.maskNotMaskedElement) | pipeQueue.io.deq.bits.mask
  val notNeedMaskedWriteDeq: Bool = Mux1H(state.vSew1H, Seq(
    pipeQueue.io.deq.bits.mask.andR,
    pipeQueue.io.deq.bits.mask(1, 0).andR,
    true.B,
  )) || state.maskNotMaskedElement
  // All required data is ready
  val dataQueueValidVec: Seq[Bool] =
    Seq(
      dataQueueVs1.valid || !state.decodeResult(Decoder.vtype),
      dataQueueVs2.io.deq.valid,
      dataQueueVd.io.deq.valid || (state.decodeResult(Decoder.sReadVD) && notNeedMaskedWriteDeq)
    ) ++
      crossReadResultQueue.map(_.io.deq.valid || !state.decodeResult(Decoder.crossRead))
  val allDataQueueValid: Bool = VecInit(dataQueueValidVec).asUInt.andR
  dequeue.valid := allDataQueueValid
  dataQueueVs1.ready := allDataQueueValid && dequeue.ready && state.decodeResult(Decoder.vtype)
  dataQueueVs2.io.deq.ready := allDataQueueValid && dequeue.ready
  dataQueueVd.io.deq.ready :=
    allDataQueueValid && dequeue.ready && !(state.decodeResult(Decoder.sReadVD) && notNeedMaskedWriteDeq)
  crossReadResultQueue.foreach(_.io.deq.ready := allDataQueueValid && dequeue.ready && state.decodeResult(Decoder.crossRead))
  stageValid := Seq(dataQueueVs2.io.deq.valid, readPipe1.dequeue.valid, readRequestQueueVs2.io.deq.valid).reduce(_ || _)
  val stageFinish = !stageValid

  object stageProbe {
    def newProbe = () => IO(Output(Probe(Bool())))

    val dequeueReadyProbe = newProbe()
    val dequeueValidProbe = newProbe()

    val hasDataOccupiedProbe = newProbe()

    val stageFinishProbe = newProbe()
    val readFinishProbe = Option.when(isLastSlot)(newProbe())
    val sSendCrossReadResultLSBProbe = Option.when(isLastSlot)(newProbe())
    val sSendCrossReadResultMSBProbe = Option.when(isLastSlot)(newProbe())
    val wCrossReadLSBProbe = Option.when(isLastSlot)(newProbe())
    val wCrossReadMSBProbe = Option.when(isLastSlot)(newProbe())

    val vrfReadRequestProbe: Seq[(Bool, Bool)] = Seq.fill(3)((newProbe(),newProbe()))
  }

  define(stageProbe.dequeueReadyProbe, ProbeValue(dequeue.ready))
  define(stageProbe.dequeueValidProbe, ProbeValue(dequeue.valid))
  define(stageProbe.hasDataOccupiedProbe, ProbeValue(stageValid))
  define(stageProbe.stageFinishProbe, ProbeValue(stageFinish))

  if (isLastSlot) {
    stageProbe.readFinishProbe.foreach(p => define(p, ProbeValue(dataQueueVs2.io.deq.valid)))
    stageProbe.sSendCrossReadResultLSBProbe.foreach(p => define(p, ProbeValue(crossReadUnitOp.get.crossWriteState.sSendCrossReadResultLSB)))
    stageProbe.sSendCrossReadResultMSBProbe.foreach(p => define(p, ProbeValue(crossReadUnitOp.get.crossWriteState.sSendCrossReadResultMSB)))
    stageProbe.wCrossReadLSBProbe.foreach(p => define(p, ProbeValue(crossReadUnitOp.get.crossWriteState.wCrossReadLSB)))
    stageProbe.wCrossReadMSBProbe.foreach(p => define(p, ProbeValue(crossReadUnitOp.get.crossWriteState.wCrossReadMSB)))
  }

  stageProbe.vrfReadRequestProbe.zipWithIndex.foreach { case((ready, valid), i) =>
    define(ready, ProbeValue(vrfReadRequest(i).ready))
    define(valid, ProbeValue(vrfReadRequest(i).valid))
  }
}
