// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import org.chipsalliance.t1.rtl._

@instantiable
class MaskExchangeUnit(parameter: LaneParameter) extends Module {
  @public
  val enqueue: DecoupledIO[LaneStage3Enqueue] =
    IO(Flipped(Decoupled(new LaneStage3Enqueue(parameter, true))))

  @public
  val dequeue: DecoupledIO[LaneStage3Enqueue] =
    IO(Decoupled(new LaneStage3Enqueue(parameter, true)))

  @public
  val maskReq: DecoupledIO[MaskUnitExeReq] = IO(Decoupled(new MaskUnitExeReq(parameter)))

  @public
  val maskRequestToLSU: Bool = IO(Output(Bool()))

  @public
  val maskUnitResponse: ValidIO[MaskUnitExeResponse] = IO(Flipped(Valid(new MaskUnitExeResponse(parameter))))

  // pipe reg
  val requestPipeReq:   LaneStage3Enqueue = RegInit(0.U.asTypeOf(enqueue.bits))
  val pipeValid:        Bool              = RegInit(false.B)
  // todo: sSendResponse -> sendResponse
  val enqIsMaskRequest: Bool              = !enqueue.bits.sSendResponse

  // todo: connect mask request & response
  maskReq.valid             := enqIsMaskRequest && enqueue.valid
  maskReq.bits.source1      := enqueue.bits.pipeData
  maskReq.bits.source2      := enqueue.bits.data
  maskReq.bits.groupCounter := enqueue.bits.groupCounter
  maskReq.bits.index        := enqueue.bits.instructionIndex

  maskRequestToLSU := enqueue.bits.loadStore

  // type change MaskUnitExeResponse -> LaneStage3Enqueue
  val maskUnitResponsePipeType: LaneStage3Enqueue = WireDefault(requestPipeReq)
  maskUnitResponsePipeType.groupCounter     := maskUnitResponse.bits.writeData.groupCounter
  maskUnitResponsePipeType.data             := maskUnitResponse.bits.writeData.data
  maskUnitResponsePipeType.mask             := maskUnitResponse.bits.writeData.mask
  maskUnitResponsePipeType.instructionIndex := maskUnitResponse.bits.index
  maskUnitResponsePipeType.ffoByOtherLanes  := enqueue.bits.ffoByOtherLanes

  val regEnq:      Bool = (enqueue.fire && !enqIsMaskRequest) || maskUnitResponse.valid
  val pipeRequest: Bool = enqueue.fire || maskUnitResponse.valid
  when(pipeRequest) {
    requestPipeReq := Mux(maskUnitResponse.valid, maskUnitResponsePipeType, enqueue.bits)
  }
  when(regEnq ^ dequeue.fire) {
    pipeValid := regEnq
  }

  enqueue.ready := ((!pipeValid || dequeue.ready) && !maskUnitResponse.valid) || enqIsMaskRequest
  dequeue.valid := pipeValid
  dequeue.bits  := requestPipeReq
}
