// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import chisel3.ltl._
import chisel3.ltl.Sequence._

import org.chipsalliance.t1.rtl.{LaneParameter, VRFReadQueueEntry, VRFReadRequest}

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
  val contenderDequeue: Option[DecoupledIO[UInt]] =
    Option.when(arbitrate)(IO(Decoupled(UInt(parameter.datapathWidth.W))))

  // arbitrate
  val reqArbitrate = Module(new ReadStageRRArbiter(enqEntryType, if(arbitrate) 2 else 1))

  (Seq(enqueue) ++ contender).zip(reqArbitrate.io.in).zip(Seq(dequeue) ++ contenderDequeue).foreach {
    case ((source, sink), deq) =>
      sink <> source
      source.ready := sink.ready && deq.ready
      sink.valid := source.valid && deq.ready
  }

  // access read port
  vrfReadRequest.valid := reqArbitrate.io.out.valid
  vrfReadRequest.bits.vs := reqArbitrate.io.out.bits.vs
  vrfReadRequest.bits.readSource := reqArbitrate.io.out.bits.readSource
  vrfReadRequest.bits.offset := reqArbitrate.io.out.bits.offset
  vrfReadRequest.bits.instructionIndex := reqArbitrate.io.out.bits.instructionIndex
  reqArbitrate.io.out.ready := vrfReadRequest.ready

  val vrfReadLatency = parameter.vrfParam.vrfReadLatency
  val dataQueue: Queue[UInt] = Module(new Queue(UInt(parameter.datapathWidth.W), vrfReadLatency + 2))
  val contenderDataQueue: Option[Queue[UInt]] = Option.when(arbitrate)(
    Module(new Queue(UInt(parameter.datapathWidth.W), vrfReadLatency + 2))
  )
  val enqFirePipe = Pipe(vrfReadRequest.fire, enqueue.fire, vrfReadLatency)

  dataQueue.io.enq.valid := enqFirePipe.valid && enqFirePipe.bits
  dataQueue.io.enq.bits := vrfReadResult
  AssertProperty(BoolSequence(!dataQueue.io.enq.valid || dataQueue.io.enq.ready))
  dequeue.valid := dataQueue.io.deq.valid
  dequeue.bits := dataQueue.io.deq.bits
  dataQueue.io.deq.ready := dequeue.ready

  contenderDataQueue.foreach { queue =>
    queue.io.enq.valid := enqFirePipe.valid && !enqFirePipe.bits
    queue.io.enq.bits := vrfReadResult
    AssertProperty(BoolSequence(!queue.io.enq.valid || queue.io.enq.ready))
    contenderDequeue.get.valid := queue.io.deq.valid
    contenderDequeue.get.bits := queue.io.deq.bits
    queue.io.deq.ready := contenderDequeue.get.ready
  }
}
