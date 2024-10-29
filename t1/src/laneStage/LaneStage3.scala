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
  val ffoIndex:         UInt         = UInt(log2Ceil(parameter.vLen / parameter.laneNumber).W)
  val crossWriteData:   Vec[UInt]    = Vec(2, UInt(parameter.datapathWidth.W))
  val sSendResponse:    Bool         = Bool()
  val ffoSuccess:       Bool         = Bool()
  val fpReduceValid:    Option[Bool] = Option.when(parameter.fpuEnable && isLastSlot)(Bool())
  // pipe state
  val decodeResult:     DecodeBundle = Decoder.bundle(parameter.decoderParam)
  val instructionIndex: UInt         = UInt(parameter.instructionIndexBits.W)
  // Need real-time status, no pipe
  val ffoByOtherLanes:  Bool         = Bool()
  val loadStore:        Bool         = Bool()

  /** vd or rd */
  val vd: UInt = UInt(5.W)
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

  val pipeEnqueue: Option[LaneStage3Enqueue] = Option.when(isLastSlot)(RegInit(0.U.asTypeOf(enqueue.bits)))

  /** response to [[T1.lsu]] or mask unit in [[T1]] */
  @public
  val laneResponse: Option[ValidIO[LaneResponse]] = Option.when(isLastSlot)(IO(Valid(new LaneResponse(parameter))))
  @public
  val stageValid:   Bool                          = IO(Output(Bool()))

  /** feedback from [[T1]] to [[Lane]] for [[laneResponse]] */
  @public
  val laneResponseFeedback: Option[ValidIO[LaneResponseFeedback]]  =
    Option.when(isLastSlot)(IO(Flipped(Valid(new LaneResponseFeedback(parameter)))))
  @public
  val crossWritePort:       Option[Vec[DecoupledIO[WriteBusData]]] =
    Option.when(isLastSlot)(IO(Vec(2, Decoupled(new WriteBusData(parameter)))))

  val stageValidReg: Option[Bool] = Option.when(isLastSlot)(RegInit(false.B))

  /** schedule cross lane write LSB */
  val sCrossWriteLSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

  /** schedule cross lane write MSB */
  val sCrossWriteMSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

  // state for response to scheduler
  /** schedule send [[LaneResponse]] to scheduler */
  val sSendResponse: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

  /** wait scheduler send [[LaneResponseFeedback]] */
  val wResponseFeedback: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

  // update register
  when(enqueue.fire) {
    pipeEnqueue.foreach(_ := enqueue.bits)
    (sCrossWriteLSB ++ sCrossWriteMSB).foreach(_ := !enqueue.bits.decodeResult(Decoder.crossWrite))
    (sSendResponse ++ wResponseFeedback).foreach(
      _ := enqueue.bits.decodeResult(Decoder.scheduler) || enqueue.bits.sSendResponse
    )
  }

  // Used to cut off back pressure forward
  val vrfWriteQueue: QueueIO[VRFWriteRequest] =
    Queue.io(vrfWriteBundle, entries = 4, pipe = false, flow = false)
  // The load of the pointer is a bit large, copy one
  val vrfPtrReplica: QueueIO[UInt]            =
    Queue.io(UInt(parameter.vrfParam.vrfOffsetBits.W), entries = 4, pipe = false, flow = false)
  vrfPtrReplica.enq.valid := vrfWriteQueue.enq.valid
  vrfPtrReplica.enq.bits  := vrfWriteQueue.enq.bits.offset
  vrfPtrReplica.deq.ready := vrfWriteQueue.deq.ready

  if (isLastSlot) {

    /** Write queue ready or not need to write. */
    val vrfWriteReady: Bool = vrfWriteQueue.enq.ready || pipeEnqueue.get.decodeResult(Decoder.sWrite)

    // VRF cross write
    val sendState = (sCrossWriteLSB ++ sCrossWriteMSB).toSeq
    crossWritePort.get.zipWithIndex.foreach { case (port, index) =>
      port.valid                 := stageValidReg.get && !sendState(index)
      port.bits.mask             := pipeEnqueue.get.mask(2 * index + 1, 2 * index)
      port.bits.data             := pipeEnqueue.get.crossWriteData(index)
      port.bits.counter          := pipeEnqueue.get.groupCounter
      port.bits.instructionIndex := pipeEnqueue.get.instructionIndex
      when(port.fire) {
        sendState(index) := true.B
      }
    }
    // scheduler synchronization
    val schedulerFinish: Bool = (sSendResponse ++ wResponseFeedback).reduce(_ && _)

    val dataSelect: Option[UInt] = Option.when(isLastSlot) {
      Mux(
        pipeEnqueue.get.decodeResult(Decoder.nr) ||
          (enqueue.bits.ffoByOtherLanes && pipeEnqueue.get.decodeResult(Decoder.ffo)) ||
          pipeEnqueue.get.decodeResult(Decoder.dontNeedExecuteInLane),
        pipeEnqueue.get.pipeData,
        pipeEnqueue.get.data
      )
    }
    // mask request
    laneResponse.head.valid                 := stageValidReg.get && !sSendResponse.get
    laneResponse.head.bits.data             := Mux(
      pipeEnqueue.get.decodeResult(Decoder.ffo),
      pipeEnqueue.get.ffoIndex,
      dataSelect.get
    )
    laneResponse.head.bits.toLSU            := pipeEnqueue.get.loadStore
    laneResponse.head.bits.instructionIndex := pipeEnqueue.get.instructionIndex
    laneResponse.head.bits.ffoSuccess       := pipeEnqueue.get.ffoSuccess
    laneResponse.head.bits.fpReduceValid.zip(pipeEnqueue.get.fpReduceValid).foreach { case (s, f) => s := f }

    sSendResponse.foreach(state =>
      when(laneResponse.head.valid) {
        state := true.B
      }
    )
    wResponseFeedback.foreach(state =>
      when(laneResponseFeedback.head.valid) {
        state := true.B
      }
    )

    // enqueue write for last slot
    vrfWriteQueue.enq.valid := stageValidReg.get && schedulerFinish && !pipeEnqueue.get.decodeResult(Decoder.sWrite)

    // UInt(5.W) + UInt(3.W), use `+` here
    vrfWriteQueue.enq.bits.vd := pipeEnqueue.get.vd + pipeEnqueue.get.groupCounter(
      parameter.groupNumberBits - 1,
      parameter.vrfOffsetBits
    )

    vrfWriteQueue.enq.bits.offset           := pipeEnqueue.get.groupCounter
    vrfWriteQueue.enq.bits.data             := dataSelect.get
    vrfWriteQueue.enq.bits.last             := DontCare
    vrfWriteQueue.enq.bits.instructionIndex := pipeEnqueue.get.instructionIndex
    vrfWriteQueue.enq.bits.mask             := pipeEnqueue.get.mask

    // Handshake
    /** Cross-lane writing is over */
    val CrossLaneWriteOver: Bool = (sCrossWriteLSB ++ sCrossWriteMSB).reduce(_ && _)

    enqueue.ready := !stageValidReg.get || (CrossLaneWriteOver && schedulerFinish && vrfWriteReady)
    val dequeueFire = stageValidReg.get && CrossLaneWriteOver && schedulerFinish && vrfWriteReady
    stageValidReg.foreach { data =>
      when(dequeueFire ^ enqueue.fire) {
        data := enqueue.fire
      }
    }
    stageValid := stageValidReg.get || vrfWriteQueue.deq.valid
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
