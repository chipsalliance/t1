// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v

import chisel3._
import tilelink._
import chisel3.util._

class InstructionQueueBundle(parameter: VParameter) extends Bundle {
  val instruction = new VRequest(parameter.xLen)
  val csrInterface = new CSRInterface(parameter.laneParam.vlMaxBits)
}

class VectorWrapper(parameter: VParameter) extends Module {
  val request: DecoupledIO[VRequest] = IO(Flipped(Decoupled(new VRequest(parameter.xLen))))
  val response: ValidIO[VResponse] = IO(Valid(new VResponse(parameter.xLen)))
  val csrInterface: CSRInterface = IO(Input(new CSRInterface(parameter.laneParam.vlMaxBits)))
  val storeBufferClear: Bool = IO(Input(Bool()))
  val memoryPorts: Vec[TLBundle] = IO(Vec(parameter.memoryBankSize, parameter.tlParam.bundle()))

  // v主体
  val vector: V = Module(new V(parameter))
  // 先忽视v set 类型的指令
  val vSetInstruction: Bool = (request.bits.instruction(6, 0) === "0b1010111".U) && (request.bits.instruction(14, 12) === 7.U)
  val instructionQueue: Queue[InstructionQueueBundle] = Module(new Queue(new InstructionQueueBundle(parameter), parameter.instructionQueueSize))
  // queue 入口的连接,csr信息伴随指令走
  instructionQueue.io.enq.valid := request.valid && !vSetInstruction
  request.ready := instructionQueue.io.enq.ready || vSetInstruction
  instructionQueue.io.enq.bits.instruction := request.bits
  instructionQueue.io.enq.bits.csrInterface := csrInterface

  memoryPorts.zip(vector.memoryPorts).foreach {case (sink, source) => sink <> source}
  vector.request.valid := instructionQueue.io.deq.valid
  vector.request.bits := instructionQueue.io.deq.bits.instruction
  instructionQueue.io.deq.ready := vector.request.ready
  vector.csrInterface := instructionQueue.io.deq.bits.csrInterface

  response := vector.response
  vector.storeBufferClear := storeBufferClear
}
