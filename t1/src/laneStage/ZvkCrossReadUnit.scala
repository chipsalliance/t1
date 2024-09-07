// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import org.chipsalliance.t1.rtl.{LaneParameter, ReadBusData}

class ZvkCrossReadState extends Bundle {
  val sSendCrossReadResult: Vec[Bool] = Vec(4, Bool())
  val wCrossRead:           Vec[Bool] = Vec(4, Bool())
}

@instantiable
class ZvkCrossReadUnit(parameter: LaneParameter) extends Module {
  @public
  val dataInputLSBLSB: DecoupledIO[UInt] = IO(Flipped(Decoupled(UInt(parameter.datapathWidth.W))))
  @public
  val dataInputLSBMSB: DecoupledIO[UInt] = IO(Flipped(Decoupled(UInt(parameter.datapathWidth.W))))
  @public
  val dataInputMSBLSB: DecoupledIO[UInt] = IO(Flipped(Decoupled(UInt(parameter.datapathWidth.W))))
  @public
  val dataInputMSBMSB: DecoupledIO[UInt] = IO(Flipped(Decoupled(UInt(parameter.datapathWidth.W))))
  @public
  val laneIndex:       UInt              = IO(Input(UInt(parameter.laneNumberBits.W)))
  @public
  val dataGroup:       UInt              = IO(Input(UInt(parameter.groupNumberBits.W)))
  @public
  val currentGroup:    UInt              = IO(Output(UInt(parameter.groupNumberBits.W)))

  @public
  val readBusDequeue: Option[Vec[DecoupledIO[ReadBusData]]] =
    Option.when(parameter.zvkEnable)(
      IO(Vec(4, Flipped(Decoupled(new ReadBusData(parameter: LaneParameter)))))
    )
  @public
  val readBusRequest: Option[Vec[DecoupledIO[ReadBusData]]] =
    Option.when(parameter.zvkEnable)(IO(Vec(4, Decoupled(new ReadBusData(parameter)))))

  @public
  val crossReadDequeue:   DecoupledIO[UInt] = IO(Decoupled(UInt((parameter.datapathWidth * 4).W)))
  @public
  val crossReadStageFree: Bool              = IO(Output(Bool()))
  @public
  val crossWriteState = IO(Output(new ZvkCrossReadState))

  val stageValid: Bool = RegInit(false.B)
  val sSendCrossReadResultLSBLSB, sSendCrossReadResultMSBLSB, wCrossReadLSBLSB, wCrossReadMSBLSB = RegInit(true.B)
  val sSendCrossReadResultLSBMSB, sSendCrossReadResultMSBMSB, wCrossReadLSBMSB, wCrossReadMSBMSB = RegInit(true.B)
  val stateVec:       Seq[Bool] = Seq(
    sSendCrossReadResultLSBLSB,
    sSendCrossReadResultLSBMSB,
    sSendCrossReadResultMSBLSB,
    sSendCrossReadResultMSBMSB,
    wCrossReadLSBLSB,
    wCrossReadLSBMSB,
    wCrossReadMSBLSB,
    wCrossReadMSBMSB
  )
  val sendDataVec:    Vec[UInt] = RegInit(VecInit(Seq.fill(4)(0.U(parameter.datapathWidth.W))))
  val groupCounter:   UInt      = RegInit(0.U(parameter.groupNumberBits.W))
  val receiveDataVec: Vec[UInt] = RegInit(VecInit(Seq.fill(4)(0.U(parameter.datapathWidth.W))))
  val sendState    = Seq(
    sSendCrossReadResultLSBLSB,
    sSendCrossReadResultLSBMSB,
    sSendCrossReadResultMSBLSB,
    sSendCrossReadResultMSBMSB
  )
  val receiveState = Seq(
    wCrossReadLSBLSB,
    wCrossReadLSBMSB,
    wCrossReadMSBLSB,
    wCrossReadMSBMSB
  )

  readBusRequest.get.zipWithIndex.foreach { case (port, index) =>
    port.valid     := stageValid && !sendState(index)
    port.bits.data := sendDataVec(index)
    when(port.fire) { sendState(index) := true.B }
  }

  readBusDequeue.get.zipWithIndex.foreach { case (port, index) =>
    when(port.fire) {
      receiveState(index)   := true.B
      receiveDataVec(index) := port.bits.data
    }
    port.ready := !receiveState(index)
  }
  val allStateReady:  Bool = stateVec.reduce(_ && _)
  val stageReady:     Bool = !stageValid || (allStateReady && crossReadDequeue.ready)
  val allSourceValid: Bool = Seq(
    dataInputLSBLSB.valid,
    dataInputLSBMSB.valid,
    dataInputMSBLSB.valid,
    dataInputMSBMSB.valid
  ).reduce(_ && _)
  val enqueueFire:    Bool = stageReady && allSourceValid
  dataInputLSBLSB.ready := allSourceValid && stageReady
  dataInputLSBMSB.ready := allSourceValid && stageReady
  dataInputMSBLSB.ready := allSourceValid && stageReady
  dataInputMSBMSB.ready := allSourceValid && stageReady

  when(enqueueFire ^ crossReadDequeue.fire) {
    stageValid := enqueueFire
  }
  when(enqueueFire) {
    stateVec.foreach(_ := false.B)
    sendDataVec  := VecInit(
      Seq(
        dataInputLSBLSB.bits,
        dataInputLSBMSB.bits,
        dataInputMSBLSB.bits,
        dataInputMSBMSB.bits
      )
    )
    groupCounter := dataGroup
  }
  currentGroup           := groupCounter
  crossReadDequeue.bits  := receiveDataVec.asUInt
  crossReadDequeue.valid := allStateReady && stageValid
  crossReadStageFree     := (!stageValid) && stateVec.reduce(_ && _)

  crossWriteState.sSendCrossReadResult(0) := sSendCrossReadResultLSBLSB
  crossWriteState.sSendCrossReadResult(1) := sSendCrossReadResultLSBMSB
  crossWriteState.sSendCrossReadResult(2) := sSendCrossReadResultMSBLSB
  crossWriteState.sSendCrossReadResult(3) := sSendCrossReadResultMSBMSB
  crossWriteState.wCrossRead(0)           := wCrossReadLSBLSB
  crossWriteState.wCrossRead(1)           := wCrossReadLSBMSB
  crossWriteState.wCrossRead(2)           := wCrossReadMSBLSB
  crossWriteState.wCrossRead(3)           := wCrossReadMSBMSB
}
