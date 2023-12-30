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
  val currentGroup: UInt = IO(Output(UInt(parameter.groupNumberBits.W)))

  val readBusDequeue: Vec[DecoupledIO[ReadBusData]] = IO(
    Vec(2, Flipped(Decoupled(new ReadBusData(parameter: LaneParameter)))
  ))

  val readBusRequest: Vec[DecoupledIO[ReadBusData]] =
    IO(Vec(2, Decoupled(new ReadBusData(parameter))))

  val crossReadDequeue: DecoupledIO[UInt] = IO(Decoupled(UInt((parameter.datapathWidth * 2).W)))
  val crossReadStageFree: Bool = IO(Output(Bool()))
  val crossWriteState = IO(Output(new CrossReadState))

  val stageValid: Bool = RegInit(false.B)
  val sSendCrossReadResultLSB, sSendCrossReadResultMSB, wCrossReadLSB, wCrossReadMSB = RegInit(true.B)
  val stateVec: Seq[Bool] = Seq(sSendCrossReadResultLSB, sSendCrossReadResultMSB, wCrossReadLSB, wCrossReadMSB)
  val sendDataVec: Vec[UInt] = RegInit(VecInit(Seq.fill(2)(0.U(parameter.datapathWidth.W))))
  val groupCounter: UInt = RegInit(0.U(parameter.groupNumberBits.W))
  val receiveDataVec: Vec[UInt] = RegInit(VecInit(Seq.fill(2)(0.U(parameter.datapathWidth.W))))
  val sendState = Seq(sSendCrossReadResultLSB, sSendCrossReadResultMSB)
  val receiveState = Seq(wCrossReadLSB, wCrossReadMSB)

  readBusRequest.zipWithIndex.foreach { case (port, index) =>
    port.valid := stageValid && !sendState(index)
    port.bits.data := sendDataVec(index)
    when(port.fire) { sendState(index) := true.B}
  }

  readBusDequeue.zipWithIndex.foreach { case (port, index) =>
    when(port.fire) {
      receiveState(index) := true.B
      receiveDataVec(index) := port.bits.data
    }
    port.ready := !receiveState(index)
  }

  val allStateReady: Bool = stateVec.reduce(_ && _)
  val stageReady: Bool = !stageValid || (allStateReady && crossReadDequeue.ready)
  val allSourceValid: Bool = dataInputLSB.valid && dataInputMSB.valid
  val enqueueFire: Bool = stageReady && allSourceValid
  dataInputLSB.ready := allSourceValid && stageReady
  dataInputMSB.ready := allSourceValid && stageReady

  when(enqueueFire ^ crossReadDequeue.fire) {
    stageValid := enqueueFire
  }
  when(enqueueFire) {
    stateVec.foreach(_ := false.B)
    sendDataVec := VecInit(Seq(dataInputLSB.bits, dataInputMSB.bits))
    groupCounter := dataGroup
  }
  currentGroup := groupCounter
  crossReadDequeue.bits := receiveDataVec.asUInt
  crossReadDequeue.valid := allStateReady && stageValid
  crossReadStageFree := (!stageValid) && stateVec.reduce(_ && _)

  crossWriteState.sSendCrossReadResultLSB := sSendCrossReadResultLSB
  crossWriteState.sSendCrossReadResultMSB := sSendCrossReadResultMSB
  crossWriteState.wCrossReadLSB := wCrossReadLSB
  crossWriteState.wCrossReadMSB := wCrossReadMSB
}
