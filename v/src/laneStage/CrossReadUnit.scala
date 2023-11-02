// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v
import chisel3._
import chisel3.util._

class CrossReadState extends Bundle {
  val sSendCrossReadResultLSB: Bool = Bool()
  val sSendCrossReadResultMSB: Bool = Bool()
  val wCrossReadLSB: Bool = Bool()
  val wCrossReadMSB: Bool = Bool()
}
class CrossReadUnit(parameter: LaneParameter) extends Module {
  val dataInputLSB: DecoupledIO[UInt] = IO(Flipped(Decoupled(UInt(parameter.datapathWidth.W))))
  val dataInputMSB: DecoupledIO[UInt] = IO(Flipped(Decoupled(UInt(parameter.datapathWidth.W))))
  val laneIndex: UInt = IO(Input(UInt(parameter.laneNumberBits.W)))
  val dataGroup: UInt = IO(Input(UInt(parameter.groupNumberBits.W)))

  val readBusDequeue: ValidIO[ReadBusData] = IO(
    Flipped(Valid(new ReadBusData(parameter: LaneParameter))
  ))

  val readBusRequest: DecoupledIO[ReadBusData] =
    IO(Decoupled(new ReadBusData(parameter)))

  val crossReadDequeue: DecoupledIO[UInt] = IO(Decoupled(UInt((parameter.datapathWidth * 2).W)))
  val crossReadStageFree: Bool = IO(Output(Bool()))
  val crossWriteState = IO(Output(new CrossReadState))

  val stageValid: Bool = RegInit(false.B)
  val sSendCrossReadResultLSB, sSendCrossReadResultMSB, wCrossReadLSB, wCrossReadMSB = RegInit(true.B)
  val stateVec: Seq[Bool] = Seq(sSendCrossReadResultLSB, sSendCrossReadResultMSB, wCrossReadLSB, wCrossReadMSB)
  val sendDataVec: Vec[UInt] = RegInit(VecInit(Seq.fill(2)(0.U(parameter.datapathWidth.W))))
  val groupCounter: UInt = RegInit(0.U(parameter.groupNumberBits.W))
  val receiveDataVec: Vec[UInt] = RegInit(VecInit(Seq.fill(2)(0.U(parameter.datapathWidth.W))))

  readBusRequest.valid := stageValid && !(sSendCrossReadResultLSB && sSendCrossReadResultMSB)
  readBusRequest.bits.sinkIndex := sSendCrossReadResultLSB ## laneIndex(parameter.laneNumberBits - 1, 1)
  readBusRequest.bits.isTail := laneIndex(0)
  readBusRequest.bits.sourceIndex := laneIndex
  readBusRequest.bits.instructionIndex := DontCare
  readBusRequest.bits.counter := groupCounter
  readBusRequest.bits.data := Mux(sSendCrossReadResultLSB, sendDataVec.last, sendDataVec.head)

  when(readBusRequest.fire) {
    when(!sSendCrossReadResultLSB) {
      sSendCrossReadResultLSB := true.B
    }.otherwise {
      sSendCrossReadResultMSB := true.B
    }
  }

  when(readBusDequeue.valid) {
    when(readBusDequeue.bits.isTail) {
      wCrossReadMSB := true.B
      receiveDataVec.last := readBusDequeue.bits.data
    }.otherwise {
      wCrossReadLSB := true.B
      receiveDataVec.head := readBusDequeue.bits.data
    }
  }

  val deqQueue: Queue[UInt] = Module(new Queue(UInt((parameter.datapathWidth * 2).W), 1))
  val allStateReady: Bool = stateVec.reduce(_ && _)
  val stageReady: Bool = !stageValid || (allStateReady && deqQueue.io.enq.ready)
  val allSourceValid: Bool = dataInputLSB.valid && dataInputMSB.valid
  val enqueueFire: Bool = stageReady && allSourceValid
  dataInputLSB.ready := allSourceValid && stageReady
  dataInputMSB.ready := allSourceValid && stageReady

  when(enqueueFire ^ deqQueue.io.enq.fire) {
    stageValid := enqueueFire
  }
  when(enqueueFire) {
    stateVec.foreach(_ := false.B)
    sendDataVec := VecInit(Seq(dataInputLSB.bits, dataInputMSB.bits))
  }
  deqQueue.io.enq.bits := receiveDataVec.asUInt
  deqQueue.io.enq.valid := allStateReady && stageValid
  crossReadStageFree := (!stageValid) && stateVec.reduce(_ && _) && !deqQueue.io.deq.valid
  crossReadDequeue <> deqQueue.io.deq

  crossWriteState.sSendCrossReadResultLSB := sSendCrossReadResultLSB
  crossWriteState.sSendCrossReadResultMSB := sSendCrossReadResultMSB
  crossWriteState.wCrossReadLSB := wCrossReadLSB
  crossWriteState.wCrossReadMSB := wCrossReadMSB
}
