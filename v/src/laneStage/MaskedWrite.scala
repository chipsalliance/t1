// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v
import chisel3._
import chisel3.util._

class MaskedWrite(parameter: LaneParameter) extends Module {
  val vrfWriteBundle: VRFWriteRequest = new VRFWriteRequest(
    parameter.vrfParam.regNumBits,
    parameter.vrfOffsetBits,
    parameter.instructionIndexBits,
    parameter.datapathWidth
  )
  val enqueue: DecoupledIO[VRFWriteRequest] = IO(Flipped(Decoupled(vrfWriteBundle)))
  val dequeue: DecoupledIO[VRFWriteRequest] = IO(Decoupled(vrfWriteBundle))
  val vrfReadRequest: DecoupledIO[VRFReadRequest] = IO(Decoupled(
    new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
  ))
  val maskedWrite1H: UInt = IO(Output(UInt(parameter.chainingSize.W)))
  /** VRF read result for each slot,
   * 3 is for [[source1]] [[source2]] [[source3]]
   */
  val vrfReadResult: UInt = IO(Input(UInt(parameter.datapathWidth.W)))

  // 需要这个读端口完全ready
  val readBeforeWrite: Bool = enqueue.fire && !enqueue.bits.mask.andR
  vrfReadRequest.valid := readBeforeWrite
  vrfReadRequest.bits.vs := enqueue.bits.vd
  vrfReadRequest.bits.offset := enqueue.bits.offset
  vrfReadRequest.bits.instructionIndex := enqueue.bits.instructionIndex
  // latch data
  val readNext: Bool = RegNext(readBeforeWrite)
  val dataReg: UInt = RegEnable(vrfReadResult, 0.U(parameter.datapathWidth.W), readNext)
  val dataSelect: UInt = Mux(readNext, vrfReadResult, dataReg)

  val pipeValid: Bool = RegInit(false.B)
  val enqueuePipe: VRFWriteRequest = RegEnable(enqueue.bits, 0.U.asTypeOf(enqueue.bits), enqueue.fire)
  val writeQueue: Queue[VRFWriteRequest] = Module(new Queue(chiselTypeOf(enqueue.bits), entries = 1, flow = true))
  dequeue <> writeQueue.io.deq
  writeQueue.io.enq.valid := pipeValid
  writeQueue.io.enq.bits := enqueuePipe
  val maskFill: UInt = FillInterleaved(8, enqueuePipe.mask)
  writeQueue.io.enq.bits.data := enqueuePipe.data & maskFill | (dataSelect & (~maskFill))
  maskedWrite1H :=
    Mux(writeQueue.io.deq.valid, indexToOH(writeQueue.io.deq.bits.instructionIndex, parameter.chainingSize), 0.U) |
      Mux(pipeValid, indexToOH(enqueuePipe.instructionIndex, parameter.chainingSize), 0.U)
  enqueue.ready := !pipeValid || writeQueue.io.enq.ready
  when(enqueue.fire ^ writeQueue.io.enq.fire) {
    pipeValid := enqueue.fire
  }
}
