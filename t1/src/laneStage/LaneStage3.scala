// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle
import org.chipsalliance.t1.rtl.decoder.Decoder
import org.chipsalliance.t1.rtl._
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}

class LaneStage3Enqueue(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  val groupCounter:     UInt         = UInt(parameter.groupNumberBits.W)
  val data:             UInt         = UInt(parameter.datapathWidth.W)
  val pipeData:         UInt         = UInt(parameter.datapathWidth.W)
  val mask:             UInt         = UInt((parameter.datapathWidth / 8).W)
  val ffoIndex:         UInt         = UInt(
    (parameter.laneScale * log2Ceil(parameter.vLen / parameter.laneNumber / parameter.laneScale)).W
  )
  val crossWriteData:   Vec[UInt]    = Vec(2, UInt(parameter.datapathWidth.W))
  val sSendResponse:    Bool         = Bool()
  val ffoSuccess:       UInt         = UInt((parameter.datapathWidth / parameter.eLen).W)
  val fpReduceValid:    Option[UInt] =
    Option.when(parameter.fpuEnable && isLastSlot)(UInt((parameter.datapathWidth / parameter.eLen).W))
  // pipe state
  val decodeResult:     DecodeBundle = Decoder.bundle(parameter.decoderParam)
  val instructionIndex: UInt         = UInt(parameter.instructionIndexBits.W)
  // Need real-time status, no pipe
  val ffoByOtherLanes:  Bool         = Bool()
  val loadStore:        Bool         = Bool()

  /** vd or rd */
  val vd: UInt = UInt(5.W)

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

@instantiable
class LaneStage3(parameter: LaneParameter, isLastSlot: Boolean) extends Module {
  @public
  val enqueue:         DecoupledIO[LaneStage3Enqueue] = IO(Flipped(Decoupled(new LaneStage3Enqueue(parameter, isLastSlot))))
  val vrfWriteBundle:  VRFWriteRequest                = new VRFWriteRequest(
    parameter.vrfParam.regNumBits,
    parameter.vrfOffsetBits,
    parameter.instructionIndexBits,
    parameter.datapathWidth
  )
  @public
  val vrfWriteRequest: DecoupledIO[VRFWriteRequest]   = IO(Decoupled(vrfWriteBundle))

  @public
  val stageValid: Bool = IO(Output(Bool()))

  // Used to cut off back pressure forward
  val vrfWriteQueue: QueueIO[VRFWriteRequest] = Queue.io(vrfWriteBundle, 4)
  // The load of the pointer is a bit large, copy one
  val offsetBit:     Int                      = 1.max(parameter.vrfParam.vrfOffsetBits)
  val vrfPtrReplica: QueueIO[UInt]            = Queue.io(UInt(offsetBit.W), 4)
  vrfPtrReplica.enq.valid := vrfWriteQueue.enq.valid
  vrfPtrReplica.enq.bits  := vrfWriteQueue.enq.bits.offset
  vrfPtrReplica.deq.ready := vrfWriteQueue.deq.ready

  if (isLastSlot) {

    /** Write queue ready or not need to write. */
    val vrfWriteReady: Bool = vrfWriteQueue.enq.ready || enqueue.bits.decodeResult(Decoder.sWrite)

    val dataSelect: Option[UInt] = Option.when(isLastSlot) {
      Mux(
        enqueue.bits.decodeResult(Decoder.nr) || enqueue.bits.ffoByOtherLanes,
        enqueue.bits.pipeData,
        enqueue.bits.data
      )
    }

    // enqueue write for last slot
    vrfWriteQueue.enq.valid := enqueue.valid && !enqueue.bits.decodeResult(Decoder.sWrite)

    // UInt(5.W) + UInt(3.W), use `+` here
    vrfWriteQueue.enq.bits.vd := enqueue.bits.vd + enqueue.bits.groupCounter(
      parameter.groupNumberBits - 1,
      parameter.vrfOffsetBits
    )

    vrfWriteQueue.enq.bits.offset           := enqueue.bits.groupCounter
    vrfWriteQueue.enq.bits.data             := dataSelect.get
    vrfWriteQueue.enq.bits.last             := DontCare
    vrfWriteQueue.enq.bits.instructionIndex := enqueue.bits.instructionIndex
    vrfWriteQueue.enq.bits.mask             := enqueue.bits.mask

    enqueue.ready := vrfWriteReady
    stageValid    := vrfWriteQueue.deq.valid
  } else {
    // Normal will be one level less
    vrfWriteQueue.enq.valid := enqueue.valid

    // UInt(5.W) + UInt(3.W), use `+` here
    vrfWriteQueue.enq.bits.vd := enqueue.bits.vd + enqueue.bits.groupCounter(
      parameter.groupNumberBits - 1,
      parameter.vrfOffsetBits
    )

    vrfWriteQueue.enq.bits.offset := enqueue.bits.groupCounter

    vrfWriteQueue.enq.bits.data             := enqueue.bits.data
    vrfWriteQueue.enq.bits.last             := DontCare
    vrfWriteQueue.enq.bits.instructionIndex := enqueue.bits.instructionIndex
    vrfWriteQueue.enq.bits.mask             := enqueue.bits.mask

    // Handshake
    enqueue.ready := vrfWriteQueue.enq.ready
    stageValid    := vrfWriteQueue.deq.valid
  }
  vrfWriteRequest <> vrfWriteQueue.deq
  vrfWriteRequest.bits.offset := vrfPtrReplica.deq.bits
  vrfWriteRequest.valid       := vrfPtrReplica.deq.valid
}
