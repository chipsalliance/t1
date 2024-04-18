// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import org.chipsalliance.t1.rtl.{DataPipeInReadStage, LaneParameter, VRFReadQueueEntry, VRFReadRequest}

@instantiable
class VrfReadPipe(parameter: LaneParameter, arbitrate: Boolean = false) extends Module {

  val enqEntryType: VRFReadQueueEntry = new VRFReadQueueEntry(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits)
  // for vs2 | vd
  @public
  val enqueue: DecoupledIO[VRFReadQueueEntry] = IO(Flipped(Decoupled(enqEntryType)))

  // for cross read
  @public
  val contender: Option[DecoupledIO[VRFReadQueueEntry]] = Option.when(arbitrate)(IO(Flipped(Decoupled(enqEntryType))))

  // read port
  @public
  val vrfReadRequest: DecoupledIO[VRFReadRequest] = IO(
    Decoupled(
      new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
    )
  )

  // read result
  @public
  val vrfReadResult: UInt = IO(Input(UInt(parameter.datapathWidth.W)))

  @public
  val dequeue: DecoupledIO[UInt] = IO(Decoupled(UInt(parameter.datapathWidth.W)))
  @public
  val dequeueChoose: Option[Bool] = Option.when(arbitrate)(IO(Output(Bool())))

  // arbitrate
  val reqArbitrate = Module(new RRArbiter(enqEntryType, if(arbitrate) 2 else 1))

  (Seq(enqueue) ++ contender).zip(reqArbitrate.io.in).foreach { case (source, sink) => sink <> source }

  // access read port
  vrfReadRequest.valid := reqArbitrate.io.out.valid && dequeue.ready
  vrfReadRequest.bits.vs := reqArbitrate.io.out.bits.vs
  vrfReadRequest.bits.readSource := reqArbitrate.io.out.bits.readSource
  vrfReadRequest.bits.offset := reqArbitrate.io.out.bits.offset
  vrfReadRequest.bits.instructionIndex := reqArbitrate.io.out.bits.instructionIndex
  reqArbitrate.io.out.ready := dequeue.ready && vrfReadRequest.ready

  val vrfReadLatency = parameter.vrfParam.vrfReadLatency
  val dataQueue = Module(new Queue(new DataPipeInReadStage(parameter.datapathWidth, arbitrate), vrfReadLatency + 2))
  val dataResponsePipe = Pipe(vrfReadRequest.fire, enqueue.fire, vrfReadLatency)

  dataQueue.io.enq.valid := dataResponsePipe.valid
  dataQueue.io.enq.bits.data := vrfReadResult
  dataQueue.io.enq.bits.choose.foreach(_ := dataResponsePipe.bits)
  assert(!dataQueue.io.enq.valid || dataQueue.io.enq.ready, "queue overflow")

  dequeueChoose.foreach { _ := dataQueue.io.deq.bits.choose.get }
  dequeue.valid := dataQueue.io.deq.valid
  dequeue.bits := dataQueue.io.deq.bits.data
  dataQueue.io.deq.ready := dequeue.ready
}
