// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import org.chipsalliance.t1.rtl._
import org.chipsalliance.t1.rtl.decoder.Decoder

@instantiable
class MaskExchangeUnit(parameter: LaneParameter) extends Module {
  @public
  val enqueue: DecoupledIO[LaneStage3Enqueue] =
    IO(Flipped(Decoupled(new LaneStage3Enqueue(parameter, true))))

  @public
  val dequeue: DecoupledIO[LaneStage3Enqueue] =
    IO(Decoupled(new LaneStage3Enqueue(parameter, true)))

  @public
  val maskReq: ValidIO[MaskUnitExeReq] = IO(Valid(new MaskUnitExeReq(parameter)))

  @public
  val maskRequestToLSU: Bool = IO(Output(Bool()))

  @public
  val tokenIO: LaneTokenBundle = IO(new LaneTokenBundle)

  // todo: sSendResponse -> sendResponse
  val enqIsMaskRequest: Bool = !enqueue.bits.sSendResponse
  // not maskUnit && not send out
  val enqSendToDeq:     Bool = !enqueue.bits.decodeResult(Decoder.maskUnit) && enqueue.bits.sSendResponse
  val enqFFoIndex:      Bool = enqueue.bits.decodeResult(Decoder.ffo) &&
    enqueue.bits.decodeResult(Decoder.targetRd)

  val maskRequestAllow: Bool =
    pipeToken(parameter.maskRequestQueueSize)(maskReq.valid, tokenIO.maskRequestRelease)
  // todo: connect mask request & response
  maskReq.valid        := enqIsMaskRequest && enqueue.valid && maskRequestAllow
  maskReq.bits.source1 := enqueue.bits.pipeData
  maskReq.bits.source2 := Mux(
    enqFFoIndex,
    enqueue.bits.ffoIndex,
    enqueue.bits.data
  )
  maskReq.bits.index   := enqueue.bits.instructionIndex
  maskReq.bits.ffo     := enqueue.bits.ffoSuccess

  maskReq.bits.fpReduceValid.zip(enqueue.bits.fpReduceValid).foreach { case (sink, source) => sink := source }

  maskRequestToLSU := enqueue.bits.loadStore

  val maskRequestEnqReady: Bool = !enqIsMaskRequest || maskRequestAllow

  dequeue.valid := enqueue.valid && enqSendToDeq
  dequeue.bits  := enqueue.bits
  enqueue.ready := Mux(enqSendToDeq, dequeue.ready, maskRequestEnqReady)
}
