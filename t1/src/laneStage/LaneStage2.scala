// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle
import org.chipsalliance.t1.rtl.{cutUIntBySize, CSRInterface, LaneExecuteStage, LaneParameter}
import org.chipsalliance.t1.rtl.decoder.Decoder
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}

class LaneStage2Enqueue(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  val src:                 Vec[UInt]    = Vec(3, UInt(parameter.datapathWidth.W))
  val groupCounter:        UInt         = UInt(parameter.groupNumberBits.W)
  val maskForFilter:       UInt         = UInt((parameter.datapathWidth / 8).W)
  val mask:                UInt         = UInt((parameter.datapathWidth / 8).W)
  val sSendResponse:       Option[Bool] = Option.when(isLastSlot)(Bool())
  val bordersForMaskLogic: Bool         = Bool()
  // pipe state
  // for stage3
  val decodeResult:        DecodeBundle = Decoder.bundle(parameter.decoderParam)
  val instructionIndex:    UInt         = UInt(parameter.instructionIndexBits.W)
  val loadStore:           Bool         = Bool()

  /** vd or rd */
  val vd:       UInt         = UInt(5.W)
  // Newly added in stage2
  val csr:      CSRInterface = new CSRInterface(parameter.vlMaxBits)
  val vSew1H:   UInt         = UInt(3.W)
  val maskType: Bool         = Bool()

  // pipe for mask pipe
  val readFromScalar: Option[UInt] = Option.when(isLastSlot)(UInt(parameter.datapathWidth.W))

  // pipe for mask stage
  val secondPipe:        Option[Bool]              = Option.when(isLastSlot)(Bool())
  val pipeForSecondPipe: Option[PipeForSecondPipe] = Option.when(isLastSlot)(
    new PipeForSecondPipe(
      parameter.datapathWidth,
      parameter.groupNumberBits,
      parameter.laneNumberBits,
      parameter.eLen
    )
  )
}

class LaneStage2Dequeue(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  val groupCounter:  UInt         = UInt(parameter.groupNumberBits.W)
  val mask:          UInt         = UInt((parameter.datapathWidth / 8).W)
  val sSendResponse: Option[Bool] = Option.when(isLastSlot)(Bool())
  val pipeData:      Option[UInt] = Option.when(isLastSlot)(UInt(parameter.datapathWidth.W))

  // pipe state for stage3
  val decodeResult:     DecodeBundle = Decoder.bundle(parameter.decoderParam)
  val instructionIndex: UInt         = UInt(parameter.instructionIndexBits.W)
  val loadStore:        Bool         = Bool()
  val vd:               UInt         = UInt(5.W)
  val vSew1H:           UInt         = UInt(3.W)

  // pipe for mask pipe
  val readFromScalar: Option[UInt] = Option.when(isLastSlot)(UInt(parameter.datapathWidth.W))

  // pipe for mask stage
  val secondPipe:        Option[Bool]              = Option.when(isLastSlot)(Bool())
  val pipeForSecondPipe: Option[PipeForSecondPipe] = Option.when(isLastSlot)(
    new PipeForSecondPipe(
      parameter.datapathWidth,
      parameter.groupNumberBits,
      parameter.laneNumberBits,
      parameter.eLen
    )
  )
}

// s2 执行
@instantiable
class LaneStage2(parameter: LaneParameter, isLastSlot: Boolean)
    extends LaneStage(true)(
      new LaneStage2Enqueue(parameter, isLastSlot),
      new LaneStage2Dequeue(parameter, isLastSlot)
    ) {

  val decodeResult: DecodeBundle = enqueue.bits.decodeResult

  val executionQueue: QueueIO[LaneExecuteStage] =
    Queue.io(new LaneExecuteStage(parameter)(isLastSlot), parameter.executionQueueSize)

  // ffo success in current data group?
  val ffoSuccess: Option[Bool] = Option.when(isLastSlot)(RegInit(false.B))

  val bordersCorrectMask: UInt = Mux(
    enqueue.bits.bordersForMaskLogic,
    (scanRightOr(UIntToOH(enqueue.bits.csr.vl(parameter.datapathWidthBits - 1, 0))) >> 1).asUInt,
    -1.S(parameter.datapathWidth.W).asUInt
  )
  val maskTypeMask:       UInt = Mux(
    enqueue.bits.maskType,
    enqueue.bits.src(0),
    -1.S(parameter.datapathWidth.W).asUInt
  )
  val complexMask:        UInt = bordersCorrectMask & maskTypeMask
  val ffoCompleteWrite:   UInt = Mux(
    enqueue.bits.maskType || enqueue.bits.bordersForMaskLogic,
    (~complexMask).asUInt & enqueue.bits.src(2),
    0.U
  )
  // executionQueue enqueue
  executionQueue.enq.bits.pipeData.foreach { data =>
    data := Mux(
      // pipe source1 for gather, pipe ~v0 & vd for ffo
      decodeResult(Decoder.gather) || decodeResult(Decoder.ffo),
      Mux(decodeResult(Decoder.gather), enqueue.bits.src(0), ffoCompleteWrite),
      enqueue.bits.src(1)
    )
  }
  executionQueue.enq.bits.sSendResponse.foreach { d => d := enqueue.bits.sSendResponse.get }
  executionQueue.enq.bits.readFromScalar.foreach { d => d := enqueue.bits.readFromScalar.get }
  executionQueue.enq.bits.secondPipe.foreach { d => d := enqueue.bits.secondPipe.get }
  executionQueue.enq.bits.pipeForSecondPipe.foreach { d => d := enqueue.bits.pipeForSecondPipe.get }
  executionQueue.enq.bits.groupCounter := enqueue.bits.groupCounter
  executionQueue.enq.bits.mask             := Mux1H(
    enqueue.bits.vSew1H,
    Seq(
      enqueue.bits.maskForFilter,
      FillInterleaved(2, cutUIntBySize(enqueue.bits.maskForFilter, 2).head),
      // todo: handle first masked
      FillInterleaved(4, cutUIntBySize(enqueue.bits.maskForFilter, 4).head)
    )
  )
  executionQueue.enq.bits.decodeResult     := enqueue.bits.decodeResult
  executionQueue.enq.bits.instructionIndex := enqueue.bits.instructionIndex
  executionQueue.enq.bits.loadStore        := enqueue.bits.loadStore
  executionQueue.enq.bits.vd               := enqueue.bits.vd
  executionQueue.enq.bits.vSew1H           := enqueue.bits.vSew1H
  executionQueue.enq.valid                 := enqueue.valid
  enqueue.ready                            := executionQueue.enq.ready
  dequeue.valid                            := executionQueue.deq.valid
  executionQueue.deq.ready                 := dequeue.ready

  dequeue.bits.readFromScalar.foreach(_ := executionQueue.deq.bits.readFromScalar.get)
  dequeue.bits.pipeData.foreach(_ := executionQueue.deq.bits.pipeData.get)
  dequeue.bits.groupCounter     := executionQueue.deq.bits.groupCounter
  dequeue.bits.mask             := executionQueue.deq.bits.mask
  dequeue.bits.decodeResult     := executionQueue.deq.bits.decodeResult
  dequeue.bits.instructionIndex := executionQueue.deq.bits.instructionIndex
  dequeue.bits.loadStore        := executionQueue.deq.bits.loadStore
  dequeue.bits.vd               := executionQueue.deq.bits.vd
  dequeue.bits.vSew1H           := executionQueue.deq.bits.vSew1H
  dequeue.bits.sSendResponse.foreach(_ := executionQueue.deq.bits.sSendResponse.get)
  dequeue.bits.secondPipe.foreach(_ := executionQueue.deq.bits.secondPipe.get)
  dequeue.bits.pipeForSecondPipe.foreach(_ := executionQueue.deq.bits.pipeForSecondPipe.get)
  stageValid                    := executionQueue.deq.valid
}
