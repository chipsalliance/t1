// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import org.chipsalliance.dwbb.stdlib.queue
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}
import org.chipsalliance.t1.rtl._
import org.chipsalliance.t1.rtl.decoder.Decoder

class PipeForMaskUnit(parameter: LaneParameter) extends Bundle {
  val sew1H: UInt = UInt(3.W)
}

class MaskExchangeRelease extends Bundle {
  val maskPipe: Bool = Bool()
}

class MaskPipeBundle(parameter: LaneParameter) extends Bundle {
  val req      = new LaneStage3Enqueue(parameter, true)
  val maskPipe = new PipeForMaskUnit(parameter)
}

class CrossWritePipe(parameter: LaneParameter) extends Bundle {
  val data:         UInt = UInt(parameter.datapathWidth.W)
  val groupCounter: UInt = UInt(parameter.groupNumberBits.W)
  val mask:         UInt = UInt((parameter.datapathWidth / 8).W)
}

@instantiable
class MaskExchangeUnit(parameter: LaneParameter) extends Module {
  @public
  val enqueue: DecoupledIO[LaneStage3Enqueue] =
    IO(Flipped(Decoupled(new LaneStage3Enqueue(parameter, true))))

  @public
  val pipeForMask: PipeForMaskUnit = IO(Input(new PipeForMaskUnit(parameter)))

  @public
  val dequeue: DecoupledIO[LaneStage3Enqueue] =
    IO(Decoupled(new LaneStage3Enqueue(parameter, true)))

  @public
  val maskReq: DecoupledIO[MaskUnitExeReq] = IO(
    Decoupled(
      new MaskUnitExeReq(parameter.eLen, parameter.datapathWidth, parameter.instructionIndexBits, parameter.fpuEnable)
    )
  )

  @public
  val maskRequestToLSU: Bool = IO(Output(Bool()))

  @public
  val crossWritePort2Deq: Vec[DecoupledIO[WriteBusData]] =
    IO(
      Vec(
        2,
        Decoupled(
          new WriteBusData(
            parameter.datapathWidth,
            parameter.instructionIndexBits,
            parameter.groupNumberBits
          )
        )
      )
    )

  @public
  val crossWritePort2Enq: Vec[DecoupledIO[WriteBusData]] =
    IO(
      Vec(
        2,
        Flipped(
          Decoupled(
            new WriteBusData(
              parameter.datapathWidth,
              parameter.instructionIndexBits,
              parameter.groupNumberBits
            )
          )
        )
      )
    )

  @public
  val crossWritePort4Deq: Vec[DecoupledIO[WriteBusData]] =
    IO(
      Vec(
        4,
        Decoupled(
          new WriteBusData(
            parameter.datapathWidth,
            parameter.instructionIndexBits,
            parameter.groupNumberBits
          )
        )
      )
    )

  @public
  val crossWritePort4Enq: Vec[DecoupledIO[WriteBusData]] =
    IO(
      Vec(
        4,
        Flipped(
          Decoupled(
            new WriteBusData(
              parameter.datapathWidth,
              parameter.instructionIndexBits,
              parameter.groupNumberBits
            )
          )
        )
      )
    )

  @public
  val maskPipeRelease: MaskExchangeRelease = IO(Output(new MaskExchangeRelease))

  // todo: sSendResponse -> sendResponse
  val enqIsMaskRequest: Bool = !enqueue.bits.sSendResponse
  // not maskUnit && not send out
  val enqSendToDeq:     Bool =
    !enqueue.bits.decodeResult(Decoder.maskUnit) && enqueue.bits.sSendResponse && !enqueue.bits.decodeResult(
      Decoder.maskPipeType
    )
  val enqSendMaskPipe:  Bool = enqueue.bits.decodeResult(Decoder.maskPipeType)
  val enqFFoIndex:      Bool = enqueue.bits.decodeResult(Decoder.ffo) &&
    enqueue.bits.decodeResult(Decoder.targetRd)

  // todo: connect mask request & response
  maskReq.valid        := enqIsMaskRequest && enqueue.valid
  maskReq.bits.source1 := enqueue.bits.pipeData
  val ffoIndexDataExtend: UInt = VecInit(cutUIntBySize(enqueue.bits.ffoIndex, parameter.laneScale).map { d =>
    changeUIntSize(d, parameter.eLen)
  }).asUInt
  maskReq.bits.source2          := Mux(
    enqFFoIndex,
    ffoIndexDataExtend,
    enqueue.bits.data
  )
  maskReq.bits.index            := enqueue.bits.instructionIndex
  maskReq.bits.ffo              := enqueue.bits.ffoSuccess
  maskReq.bits.maskRequestToLSU := enqueue.bits.loadStore

  maskReq.bits.fpReduceValid.zip(enqueue.bits.fpReduceValid).foreach { case (sink, source) => sink := source }

  maskRequestToLSU := enqueue.bits.loadStore

  // mask pipe request queue
  val maskReqQueue: queue.QueueIO[MaskPipeBundle] =
    Queue.io(new MaskPipeBundle(parameter), parameter.maskRequestQueueSize)
  maskReqQueue.enq.valid         := enqueue.valid && enqSendMaskPipe
  maskPipeRelease.maskPipe       := maskReqQueue.deq.fire
  maskReqQueue.enq.bits.req      := enqueue.bits
  maskReqQueue.enq.bits.maskPipe := pipeForMask

  // opcode (0, 1) cross write 2/4
  val crossWriteState: UInt = RegInit(15.U(4.W))

  // todo: other type
  val maskPipeDeqReady:   Bool              = crossWriteState.andR
  val maskPipeEnqReq:     LaneStage3Enqueue = maskReqQueue.deq.bits.req
  val maskPipeReqReg:     LaneStage3Enqueue = RegInit(0.U.asTypeOf(maskPipeEnqReq))
  val maskPipeMessageReg: PipeForMaskUnit   = RegInit(0.U.asTypeOf(new PipeForMaskUnit(parameter)))
  val rxGroupIndex:       UInt              = RegInit(0.U(parameter.groupNumberBits.W))

  val maskPipeValid:   Bool      = RegInit(false.B)
  val crossWriteFire2: Vec[Bool] = Wire(Vec(2, Bool()))
  val crossWriteFire4: Vec[Bool] = Wire(Vec(4, Bool()))
  val crossWriteDeqFire = crossWriteFire4.asUInt | crossWriteFire2.asUInt

  maskReqQueue.deq.ready := !maskPipeValid || maskPipeDeqReady
  val opcode1H: UInt = UIntToOH(maskPipeReqReg.decodeResult(Decoder.maskPipeUop))
  // update register
  when(maskReqQueue.deq.fire) {
    maskPipeReqReg     := maskPipeEnqReq
    maskPipeMessageReg := maskReqQueue.deq.bits.maskPipe
    when(maskPipeEnqReq.decodeResult(Decoder.maskPipeUop) === BitPat("b0000?")) {
      crossWriteState := Mux(maskPipeEnqReq.decodeResult(Decoder.maskPipeUop)(0), 0.U, 12.U)
    }
    when(maskPipeEnqReq.instructionIndex =/= maskPipeReqReg.instructionIndex) {
      rxGroupIndex := 0.U
    }
  }
  when(crossWriteDeqFire.orR) {
    crossWriteState := crossWriteState | crossWriteDeqFire
  }

  val extendData2:     UInt      = Mux(
    maskPipeMessageReg.sew1H(2),
    VecInit(
      cutUInt(maskPipeReqReg.data, 16).map(d =>
        changeUIntSizeWidthSign(d, 32, !maskPipeReqReg.decodeResult(Decoder.unsigned1))
      )
    ).asUInt,
    VecInit(
      cutUInt(maskPipeReqReg.data, 8).map(d =>
        changeUIntSizeWidthSign(d, 16, !maskPipeReqReg.decodeResult(Decoder.unsigned1))
      )
    ).asUInt
  )
  val crossWriteData2: Vec[UInt] = Mux(
    maskPipeReqReg.decodeResult(Decoder.extend),
    cutUIntBySize(extendData2, 2),
    maskPipeReqReg.crossWriteData
  )
  // VRF cross write
  crossWritePort2Deq.zipWithIndex.foreach { case (port, index) =>
    port.valid                 := maskPipeValid && !crossWriteState(index) && opcode1H(0)
    port.bits.mask             := cutUIntBySize(maskPipeReqReg.mask, 2)(index)
    port.bits.data             := crossWriteData2(index)
    port.bits.counter          := maskPipeReqReg.groupCounter
    port.bits.instructionIndex := maskPipeReqReg.instructionIndex
    crossWriteFire2(index)     := port.fire
  }

  val extendData4: UInt = VecInit(
    cutUInt(maskPipeReqReg.data, 8).map(d =>
      changeUIntSizeWidthSign(d, 32, !maskPipeReqReg.decodeResult(Decoder.unsigned1))
    )
  ).asUInt
  // VRF cross write
  crossWritePort4Deq.zipWithIndex.foreach { case (port, index) =>
    port.valid                 := maskPipeValid && !crossWriteState(index) && opcode1H(1)
    port.bits.mask             := cutUIntBySize(maskPipeReqReg.mask, 4)(index)
    port.bits.data             := cutUIntBySize(extendData4, 4)(index)
    port.bits.counter          := maskPipeReqReg.groupCounter
    port.bits.instructionIndex := maskPipeReqReg.instructionIndex
    crossWriteFire4(index)     := port.fire
  }

  // cross write rx

  /** queue for cross lane writing. TODO: benchmark the size of the queue
    */
  val crossLaneWriteQueue: Seq[QueueIO[CrossWritePipe]] = Seq.tabulate(4)(i =>
    Queue.io(
      new CrossWritePipe(parameter),
      parameter.crossLaneVRFWriteEscapeQueueSize,
      pipe = true
    )
  )

  val crossWriteDeqRequest: DecoupledIO[LaneStage3Enqueue] = Wire(chiselTypeOf(dequeue))

  val queueDeqValid: Seq[Bool] = Seq.tabulate(4) { portIndex =>
    val queue: QueueIO[CrossWritePipe] = crossLaneWriteQueue(portIndex)
    val indexGrowth = Wire(chiselTypeOf(maskPipeReqReg.groupCounter))
    val maskSelect  = Wire(chiselTypeOf(queue.enq.bits.mask))
    val enqRequest  = if (portIndex < 2) {
      queue.enq.valid                     := crossWritePort2Enq(portIndex).valid || crossWritePort4Enq(portIndex).valid
      assert(!(crossWritePort2Enq(portIndex).valid && crossWritePort4Enq(portIndex).valid))
      crossWritePort2Enq(portIndex).ready := queue.enq.ready
      crossWritePort4Enq(portIndex).ready := queue.enq.ready
      indexGrowth                         := Mux(
        crossWritePort2Enq(portIndex).valid,
        changeUIntSize(crossWritePort2Enq(portIndex).bits.counter ## portIndex.U(1.W), indexGrowth.getWidth),
        changeUIntSize(crossWritePort4Enq(portIndex).bits.counter ## portIndex.U(2.W), indexGrowth.getWidth)
      )
      maskSelect                          := Mux(
        crossWritePort2Enq(portIndex).valid,
        FillInterleaved(2, crossWritePort2Enq(portIndex).bits.mask),
        FillInterleaved(4, crossWritePort4Enq(portIndex).bits.mask)
      )
      Mux(
        crossWritePort2Enq(portIndex).valid,
        crossWritePort2Enq(portIndex).bits,
        crossWritePort4Enq(portIndex).bits
      )
    } else {
      queue.enq.valid                     := crossWritePort4Enq(portIndex).valid
      crossWritePort4Enq(portIndex).ready := queue.enq.ready
      indexGrowth                         := changeUIntSize(
        crossWritePort4Enq(portIndex).bits.counter ## portIndex.U(2.W),
        indexGrowth.getWidth
      )
      maskSelect                          := FillInterleaved(4, crossWritePort4Enq(portIndex).bits.mask)
      crossWritePort4Enq(portIndex).bits
    }
    queue.enq.bits.data := enqRequest.data
    queue.enq.bits.mask         := maskSelect
    queue.enq.bits.groupCounter := indexGrowth
    assert(
      !queue.enq.fire || enqRequest.instructionIndex === maskPipeReqReg.instructionIndex,
      "Only one mask instruction can be executed at a time"
    )

    val groupMatch = queue.deq.bits.groupCounter === rxGroupIndex
    queue.deq.ready := crossWriteDeqRequest.ready && groupMatch
    queue.deq.valid && groupMatch
  }

  crossWriteDeqRequest.valid := VecInit(queueDeqValid).asUInt
  val deqRequestSelect: CrossWritePipe = Mux1H(queueDeqValid, crossLaneWriteQueue.map(_.deq.bits))
  crossWriteDeqRequest.bits                  := DontCare
  crossWriteDeqRequest.bits.data             := deqRequestSelect.data
  crossWriteDeqRequest.bits.mask             := deqRequestSelect.mask
  crossWriteDeqRequest.bits.groupCounter     := deqRequestSelect.groupCounter
  crossWriteDeqRequest.bits.instructionIndex := maskPipeReqReg.instructionIndex
  crossWriteDeqRequest.bits.vd               := maskPipeReqReg.vd
  when(crossWriteDeqRequest.fire) {
    rxGroupIndex := rxGroupIndex + 1.U
  }

  dequeue.valid              := (enqueue.valid && enqSendToDeq) || crossWriteDeqRequest.valid
  dequeue.bits               := Mux(crossWriteDeqRequest.valid, crossWriteDeqRequest.bits, enqueue.bits)
  enqueue.ready              := Mux(enqSendToDeq, dequeue.ready && !crossWriteDeqRequest.valid, maskReq.ready) || enqSendMaskPipe
  crossWriteDeqRequest.ready := dequeue.ready
}
