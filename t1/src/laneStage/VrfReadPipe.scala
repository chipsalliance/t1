// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
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
  val dequeueChoose: Option[Bool] = Option.when(arbitrate)(IO(Output(Bool())))

  // arbitrate
  val reqArbitrate = Module(new RRArbiter(enqEntryType, if(arbitrate) 2 else 1))

  (Seq(enqueue) ++ contender).zip(reqArbitrate.io.in).foreach { case (source, sink) => sink <> source }

  val dataStageFree = Wire(Bool())
  // access read port
  vrfReadRequest.valid := reqArbitrate.io.out.valid && dataStageFree
  vrfReadRequest.bits.vs := reqArbitrate.io.out.bits.vs
  vrfReadRequest.bits.readSource := reqArbitrate.io.out.bits.readSource
  vrfReadRequest.bits.offset := reqArbitrate.io.out.bits.offset
  vrfReadRequest.bits.instructionIndex := reqArbitrate.io.out.bits.instructionIndex
  reqArbitrate.io.out.ready := dataStageFree && vrfReadRequest.ready

  // read pipe stage1
  val readPortFire: Bool = vrfReadRequest.fire
  val stage1Valid: Bool = RegInit(false.B)
  val ReadFireNext: Bool = RegNext(readPortFire)
  val dataReg: UInt = RegEnable(vrfReadResult, 0.U(parameter.datapathWidth.W), ReadFireNext)
  val stage1Choose: Option[Bool] = Option.when(arbitrate)(RegEnable(enqueue.fire, false.B, readPortFire))

  stage1Choose.foreach {d => when(readPortFire) { d := enqueue.fire}}
  when(readPortFire ^ dequeue.fire) {
    stage1Valid := readPortFire
  }

  dataStageFree := !stage1Valid || dequeue.ready

  dequeueChoose.zip(stage1Choose).foreach { case (io, data) => io := data }
  dequeue.valid := stage1Valid
  dequeue.bits := Mux(ReadFireNext, vrfReadResult, dataReg)
}
