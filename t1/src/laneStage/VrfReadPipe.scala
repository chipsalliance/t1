// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import chisel3.ltl._
import chisel3.ltl.Sequence._

import org.chipsalliance.t1.rtl.{LaneParameter, VRFReadQueueEntry, VRFReadRequest}
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}

@instantiable
class VrfReadPipe(parameter: LaneParameter, arbitrate: Boolean = false) extends Module {

  val enqEntryType: VRFReadQueueEntry              = new VRFReadQueueEntry(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits)
  // for vs2 | vd
  @public
  val enqueue:      DecoupledIO[VRFReadQueueEntry] = IO(Flipped(Decoupled(enqEntryType)))

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
  val contenderDequeue: Option[DecoupledIO[UInt]] =
    Option.when(arbitrate)(IO(Decoupled(UInt(parameter.datapathWidth.W))))

  // arbitrate
  val reqArbitrate = Module(new ReadStageRRArbiter(enqEntryType, if (arbitrate) 2 else 1))

  (Seq(enqueue) ++ contender).zip(reqArbitrate.io.in).zip(Seq(dequeue) ++ contenderDequeue).foreach {
    case ((source, sink), deq) =>
      sink <> source
      source.ready := sink.ready && deq.ready
      sink.valid   := source.valid && deq.ready
  }

  // access read port
  vrfReadRequest.valid                 := reqArbitrate.io.out.valid
  vrfReadRequest.bits.vs               := reqArbitrate.io.out.bits.vs
  vrfReadRequest.bits.readSource       := reqArbitrate.io.out.bits.readSource
  vrfReadRequest.bits.offset           := reqArbitrate.io.out.bits.offset
  vrfReadRequest.bits.instructionIndex := reqArbitrate.io.out.bits.instructionIndex
  reqArbitrate.io.out.ready            := vrfReadRequest.ready

  val vrfReadLatency = parameter.vrfParam.vrfReadLatency
  val dataQueue:          QueueIO[UInt]         = Queue.io(UInt(parameter.datapathWidth.W), vrfReadLatency + 2)
  val contenderDataQueue: Option[QueueIO[UInt]] = Option.when(arbitrate)(
    Queue.io(UInt(parameter.datapathWidth.W), vrfReadLatency + 2)
  )
  val enqFirePipe = Pipe(vrfReadRequest.fire, enqueue.fire, vrfReadLatency)

  dataQueue.enq.valid := enqFirePipe.valid && enqFirePipe.bits
  dataQueue.enq.bits  := vrfReadResult
  AssertProperty(BoolSequence(!dataQueue.enq.valid || dataQueue.enq.ready))
  dequeue.valid       := dataQueue.deq.valid
  dequeue.bits        := dataQueue.deq.bits
  dataQueue.deq.ready := dequeue.ready

  contenderDataQueue.foreach { queue =>
    queue.enq.valid            := enqFirePipe.valid && !enqFirePipe.bits
    queue.enq.bits             := vrfReadResult
    AssertProperty(BoolSequence(!queue.enq.valid || queue.enq.ready))
    contenderDequeue.get.valid := queue.deq.valid
    contenderDequeue.get.bits  := queue.deq.bits
    queue.deq.ready            := contenderDequeue.get.ready
  }
}
