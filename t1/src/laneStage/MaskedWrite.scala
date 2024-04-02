// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import org.chipsalliance.t1.rtl.{LaneParameter, VRFReadRequest, VRFWriteRequest, ffo, indexToOH}

@instantiable
class MaskedWrite(parameter: LaneParameter) extends Module {
  val vrfWriteBundle: VRFWriteRequest = new VRFWriteRequest(
    parameter.vrfParam.regNumBits,
    parameter.vrfOffsetBits,
    parameter.instructionIndexBits,
    parameter.datapathWidth
  )
  @public
  val enqueue: DecoupledIO[VRFWriteRequest] = IO(Flipped(Decoupled(vrfWriteBundle)))
  @public
  val dequeue: DecoupledIO[VRFWriteRequest] = IO(Decoupled(vrfWriteBundle))
  @public
  val vrfReadRequest: DecoupledIO[VRFReadRequest] = IO(Decoupled(
    new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
  ))
  @public
  val maskedWrite1H: UInt = IO(Output(UInt(parameter.chainingSize.W)))
  /** VRF read result for each slot,
   * 3 is for [[source1]] [[source2]] [[source3]]
   */
  @public
  val vrfReadResult: UInt = IO(Input(UInt(parameter.datapathWidth.W)))
  // raw forward
  val hitWrite: Bool = Wire(Bool())

  // 需要这个读端口完全ready
  val readBeforeWrite: Bool = enqueue.fire && !enqueue.bits.mask.andR
  vrfReadRequest.valid := readBeforeWrite && !hitWrite
  vrfReadRequest.bits.vs := enqueue.bits.vd
  vrfReadRequest.bits.readSource := 2.U
  vrfReadRequest.bits.offset := enqueue.bits.offset
  vrfReadRequest.bits.instructionIndex := enqueue.bits.instructionIndex
  // latch data
  val readNext: Bool = RegNext(readBeforeWrite, false.B)
  val dataFromWrite: Bool = RegNext(hitWrite, false.B)
  val writeNext: UInt = RegNext(dequeue.bits.data, 0.U)
  val readDataSelect: UInt = Mux(dataFromWrite, writeNext, vrfReadResult)
  val dataReg: UInt = RegEnable(readDataSelect, 0.U(parameter.datapathWidth.W), readNext)
  val latchDataSelect: UInt = Mux(readNext, readDataSelect, dataReg)

  val pipeValid: Bool = RegInit(false.B)
  val enqueuePipe: VRFWriteRequest = RegEnable(enqueue.bits, 0.U.asTypeOf(enqueue.bits), enqueue.fire)
  val writeQueue: Queue[VRFWriteRequest] = Module(new Queue(chiselTypeOf(enqueue.bits), entries = 1, flow = true))
  dequeue <> writeQueue.io.deq
  writeQueue.io.enq.valid := pipeValid
  writeQueue.io.enq.bits := enqueuePipe
  val maskFill: UInt = FillInterleaved(8, enqueuePipe.mask)
  writeQueue.io.enq.bits.data := enqueuePipe.data & maskFill | (latchDataSelect & (~maskFill))
  maskedWrite1H :=
    Mux(writeQueue.io.deq.valid, indexToOH(writeQueue.io.deq.bits.instructionIndex, parameter.chainingSize), 0.U) |
      Mux(pipeValid, indexToOH(enqueuePipe.instructionIndex, parameter.chainingSize), 0.U)
  enqueue.ready := !pipeValid || writeQueue.io.enq.ready
  when(enqueue.fire ^ writeQueue.io.enq.fire) {
    pipeValid := enqueue.fire
  }
  hitWrite := writeQueue.io.deq.valid &&
    (writeQueue.io.deq.bits.vd === enqueue.bits.vd) &&
    (writeQueue.io.deq.bits.offset === enqueue.bits.offset)
}
