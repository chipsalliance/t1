// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import org.chipsalliance.t1.rtl.{ffo, indexToOH, maskAnd, LaneParameter, VRFReadRequest, VRFWriteRequest}
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}

import scala.annotation.unused

/** s0 enqueue read fire raw check: hit s1, hit s2, hit s3
  *
  * s1 wait arbiter(reg) s2 wait sram read(reg) s3 dequeu(reg)
  */
@instantiable
class MaskedWrite(parameter: LaneParameter) extends Module {
  val vrfWriteBundle: VRFWriteRequest              = new VRFWriteRequest(
    parameter.vrfParam.regNumBits,
    parameter.vrfOffsetBits,
    parameter.instructionIndexBits,
    parameter.datapathWidth
  )
  @public
  val enqueue:        DecoupledIO[VRFWriteRequest] = IO(Flipped(Decoupled(vrfWriteBundle)))
  @public
  val dequeue:        DecoupledIO[VRFWriteRequest] = IO(Decoupled(vrfWriteBundle))
  @public
  val vrfReadRequest: DecoupledIO[VRFReadRequest]  = IO(
    Decoupled(
      new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
    )
  )

  /** VRF read result for each slot, 3 is for [[source1]] [[source2]] [[source3]]
    */
  @public
  val vrfReadResult: UInt = IO(Input(UInt(parameter.datapathWidth.W)))

  def address(req: VRFWriteRequest): UInt = req.vd ## req.offset

  val dequeueWire:  DecoupledIO[VRFWriteRequest] = Wire(chiselTypeOf(dequeue))
  val dequeueQueue: QueueIO[VRFWriteRequest]     = Queue.io(chiselTypeOf(dequeue.bits), 1, flow = true)
  dequeueQueue.enq <> dequeueWire
  val s3Valid:      Bool                         = RegInit(false.B)
  val s3Pipe:       VRFWriteRequest              = RegInit(0.U.asTypeOf(enqueue.bits))
  val s3BypassData: UInt                         = RegInit(0.U.asTypeOf(UInt(parameter.datapathWidth.W)))
  val dataInS3:     UInt                         = maskAnd(s3Valid, indexToOH(s3Pipe.instructionIndex, parameter.chainingSize)).asUInt
  val fwd3:         Bool                         = RegInit(false.B)

  val s2Valid:      Bool            = RegInit(false.B)
  val s2Pipe:       VRFWriteRequest = RegInit(0.U.asTypeOf(enqueue.bits))
  val s2BypassData: UInt            = RegInit(0.U.asTypeOf(UInt(parameter.datapathWidth.W)))
  val s2EnqHitS1:   Bool            = RegInit(false.B)
  val dataInS2:     UInt            = maskAnd(s2Valid, indexToOH(s2Pipe.instructionIndex, parameter.chainingSize)).asUInt
  val fwd2:         Bool            = RegInit(false.B)

  val s1Valid:      Bool            = RegInit(false.B)
  val s1Pipe:       VRFWriteRequest = RegInit(0.U.asTypeOf(enqueue.bits))
  val s1BypassData: UInt            = RegInit(0.U.asTypeOf(UInt(parameter.datapathWidth.W)))
  val s1EnqHitS1:   Bool            = RegInit(false.B)
  val s1EnqHitS2:   Bool            = RegInit(false.B)
  val dataInS1:     UInt            = maskAnd(s1Valid, indexToOH(s1Pipe.instructionIndex, parameter.chainingSize)).asUInt
  val fwd1:         Bool            = RegInit(false.B)

  val s3EnqReady: Bool = dequeueWire.ready
  val s3Fire:     Bool = s3EnqReady && s2Valid

  val s2EnqReady: Bool = dequeueWire.ready
  val s2Fire:     Bool = s2EnqReady && s1Valid

  val s1EnqReady: Bool = Wire(Bool())
  enqueue.ready := s1EnqReady
  val s1Fire: Bool = enqueue.fire

  // raw forward
  val enqHitS1: Bool = s1Valid && address(enqueue.bits) === address(s1Pipe)
  val enqHitS2: Bool = s2Valid && address(enqueue.bits) === address(s2Pipe)
  val enqHitS3: Bool = s3Valid && address(enqueue.bits) === address(s3Pipe)
  val hitQueue: Bool = !dequeueQueue.empty &&
    address(enqueue.bits) === address(dequeueQueue.deq.bits)
  val fwd:      Bool = enqHitS1 || enqHitS2 || enqHitS3
  s1EnqReady := dequeueWire.ready && !hitQueue
  val dataInQueue: UInt = maskAnd(
    !dequeueQueue.empty,
    indexToOH(dequeueQueue.deq.bits.instructionIndex, parameter.chainingSize)
  ).asUInt

  val enqNeedRead:     Bool = !enqueue.bits.mask.andR && !fwd
  // 需要这个读端口完全ready
  val readBeforeWrite: Bool = enqueue.fire && enqNeedRead
  vrfReadRequest.valid                 := readBeforeWrite
  vrfReadRequest.bits.vs               := enqueue.bits.vd
  vrfReadRequest.bits.readSource       := 2.U
  vrfReadRequest.bits.offset           := enqueue.bits.offset
  vrfReadRequest.bits.instructionIndex := enqueue.bits.instructionIndex

  val vrfReadPipe: QueueIO[UInt] =
    Queue.io(UInt(parameter.datapathWidth.W), parameter.vrfParam.vrfReadLatency + 2)

  val readDataValid: Bool = Pipe(
    readBeforeWrite,
    false.B,
    parameter.vrfParam.vrfReadLatency
  ).valid

  vrfReadPipe.enq.valid := readDataValid
  vrfReadPipe.enq.bits  := vrfReadResult

  val maskedWrite1H = dataInS3 | dataInS2 | dataInS1 | dataInQueue

  val maskFill: UInt = FillInterleaved(8, s3Pipe.mask)
  val readDataSelect = Mux(fwd3, s3BypassData, vrfReadPipe.deq.bits)
  val s3ReadFromVrf: Bool = !s3Pipe.mask.andR && !fwd3
  dequeueWire.valid     := s3Valid
  dequeueWire.bits      := s3Pipe
  dequeueWire.bits.data := s3Pipe.data & maskFill | (readDataSelect & (~maskFill))
  vrfReadPipe.deq.ready := dequeueWire.fire && s3ReadFromVrf

  // update s1 reg
  when(s1Fire) {
    s1BypassData := dequeueWire.bits.data
    s1EnqHitS1   := enqHitS1
    s1EnqHitS2   := enqHitS2
    fwd1         := fwd
    s1Pipe       := enqueue.bits
  }
  when(s1Fire ^ s2Fire) {
    s1Valid := s1Fire
  }

  // update s2 reg
  when(s2Fire) {
    s2BypassData := Mux(s1EnqHitS2, dequeueWire.bits.data, s1BypassData)
    s2EnqHitS1   := s1EnqHitS1
    fwd2         := fwd1
    s2Pipe       := s1Pipe
  }
  when(s2Fire ^ s3Fire) {
    s2Valid := s2Fire
  }

  // update s3 reg
  when(s3Fire) {
    s3BypassData := Mux(s2EnqHitS1, dequeueWire.bits.data, s2BypassData)
    fwd3         := fwd2
    s3Pipe       := s2Pipe
  }
  when(s3Fire ^ dequeueWire.fire) {
    s3Valid := s3Fire
  }
  dequeue <> dequeueQueue.deq
}
