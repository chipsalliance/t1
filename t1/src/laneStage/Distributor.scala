// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import org.chipsalliance.t1.rtl.{cutUInt, cutUIntBySize, ffo, maskAnd, SlotRequestToVFU, VFUResponseToSlot}
import org.chipsalliance.dwbb.stdlib.queue.Queue

@instantiable
class Distributor[T <: SlotRequestToVFU, B <: VFUResponseToSlot](enqueue: T, dequeue: B)(multiCycle: Boolean = false)
    extends Module {
  // request to vfu
  @public
  val requestToVfu:    DecoupledIO[SlotRequestToVFU] = IO(Decoupled(enqueue))
  // response from vfu
  @public
  val responseFromVfu: ValidIO[VFUResponseToSlot]    = IO(Flipped(Valid(dequeue)))
  // request from LaneExecutionBridge
  @public
  val requestFromSlot: DecoupledIO[SlotRequestToVFU] = IO(Flipped(Decoupled(enqueue)))
  // response to LaneExecutionBridge
  @public
  val responseToSlot:  ValidIO[VFUResponseToSlot]    = IO(Valid(dequeue))

  val responseWire:     ValidIO[VFUResponseToSlot] = WireDefault(0.U.asTypeOf(responseToSlot))
  val requestReg:       ValidIO[SlotRequestToVFU]  = RegInit(0.U.asTypeOf(Valid(enqueue)))
  val sendRequestValid: Bool                       = RegInit(false.B)
  val ffoSuccess:       UInt                       = RegInit(0.U(dequeue.ffoSuccess.getWidth.W))
  val vxsatResult = RegInit(false.B)
  val responseData: UInt = RegInit(0.U(enqueue.src.head.getWidth.W))

  val executeSize:    Int = enqueue.src.head.getWidth / 8
  val executeSizeBit: Int = log2Ceil(executeSize)
  val executeIndex = RegInit(0.U(executeSizeBit.W))

  // Data path width is always 32 when fusion
  val vSew1HReq: UInt = UIntToOH(requestFromSlot.bits.vSew)(2, 0)
  val vSew1H = UIntToOH(requestReg.bits.vSew)(2, 0)

  when(requestFromSlot.fire ^ responseWire.valid) {
    requestReg.valid := requestFromSlot.fire
  }
  val lastRequestFire = Wire(Bool())
  when(requestFromSlot.fire || lastRequestFire) {
    sendRequestValid := requestFromSlot.fire
  }

  // enqueue fire
  when(requestFromSlot.fire) {
    requestReg.bits := requestFromSlot.bits
  }

  // update execute index
  val firstIndex = OHToUInt(ffo(requestFromSlot.bits.executeMask))

  // current one hot depends on execute index
  val currentOHForExecuteGroup: UInt = UIntToOH(executeIndex)
  // Remaining to be requested
  // todo: Whether to process sew=16 mask=0xff?
  val remainder:                UInt = requestReg.bits.executeMask & (~scanRightOr(currentOHForExecuteGroup)).asUInt
  // Finds the first unfiltered execution.
  val nextIndex:                UInt = OHToUInt(ffo(remainder))

  when(requestToVfu.fire || requestFromSlot.fire) {
    executeIndex := Mux(requestFromSlot.fire, firstIndex, nextIndex)
  }

  scanRightOr
  val byteMaskForExecution = Mux1H(
    vSew1H,
    Seq(
      currentOHForExecuteGroup,
      FillInterleaved(2, cutUIntBySize(currentOHForExecuteGroup, 2).head),
      FillInterleaved(4, cutUIntBySize(currentOHForExecuteGroup, 4).head)
    )
  )

  /** the bit-level mask of current execution. */
  val bitMaskForExecution: UInt = FillInterleaved(8, byteMaskForExecution)

  def CollapseOperand(data: UInt, sign: Bool = false.B): UInt      = {
    val dataMasked: UInt = data & bitMaskForExecution
    val dw        = data.getWidth - (data.getWidth % 32)
    // when sew = 0
    val collapse0 = Seq.tabulate(dw / 8)(i => dataMasked(8 * i + 7, 8 * i)).reduce(_ | _)
    // when sew = 1
    val collapse1 = Seq.tabulate(dw / 16)(i => dataMasked(16 * i + 15, 16 * i)).reduce(_ | _)
    val collapse2 = Seq.tabulate(dw / 32)(i => dataMasked(32 * i + 31, 32 * i)).reduce(_ | _)
    Mux1H(
      vSew1H,
      Seq(
        Fill(25, sign && collapse0(7)) ## collapse0,
        Fill(17, sign && collapse1(15)) ## collapse1,
        (sign && collapse2(31)) ## collapse2
      )
    )
  }
  val signSeq:                                           Seq[Bool] = Seq(requestReg.bits.sign0, requestReg.bits.sign, false.B, false.B)

  requestToVfu.valid             := sendRequestValid
  requestToVfu.bits              := requestReg.bits
  requestToVfu.bits.executeIndex := executeIndex
  requestToVfu.bits.src          := VecInit(
    requestReg.bits.src.zip(signSeq).map { case (d, s) => CollapseOperand(d, s) }
  )
  requestToVfu.bits.mask         := (requestReg.bits.mask & currentOHForExecuteGroup).orR
  requestToVfu.bits.popInit      := cutUIntBySize(requestReg.bits.popInit, requestReg.bits.mask.getWidth / 4)(executeIndex)
  // 5: log2ceil(elen)
  requestToVfu.bits.shifterSize  := Mux1H(currentOHForExecuteGroup, cutUInt(requestReg.bits.shifterSize, 5))

  val eSize:          Int  = requestReg.bits.mask.getWidth
  val isLastRequest:  Bool = Mux1H(
    vSew1H,
    Seq(
      !remainder(eSize - 1, 0).orR,
      !remainder((eSize / 2) - 1, 0).orR,
      if (eSize <= 4) true.B else !remainder((eSize / 4) - 1, 0).orR
    )
  )
  val writeIndex:     UInt = Wire(UInt(executeSizeBit.W))
  val isLastResponse: Bool = Wire(Bool())
  lastRequestFire := isLastRequest && requestToVfu.fire
  writeIndex      := executeIndex
  isLastResponse  := isLastRequest
  if (multiCycle) {
    val executeQueue = Queue.io(UInt((executeSizeBit + 1).W), 4)
    executeQueue.enq.bits  := isLastRequest ## executeIndex
    executeQueue.enq.valid := requestToVfu.fire
    executeQueue.deq.ready := responseFromVfu.fire
    writeIndex             := executeQueue.deq.bits(executeSizeBit - 1, 0)
    isLastResponse         := executeQueue.deq.bits(executeSizeBit) && responseFromVfu.valid && executeQueue.deq.valid
  }

  val writeElement1H: UInt = UIntToOH(writeIndex)

  /** VRF byte level mask */
  val writeMaskInByte: UInt = Mux1H(
    vSew1H(2, 0),
    Seq(
      writeElement1H,
      FillInterleaved(2, cutUIntBySize(writeElement1H, 2).head),
      FillInterleaved(4, cutUIntBySize(writeElement1H, 4).head)
    )
  ).asUInt

  /** VRF bit level mask */
  val writeMaskInBit: UInt = FillInterleaved(8, writeMaskInByte)

  val executeResult: UInt = Mux1H(
    vSew1H(2, 0),
    Seq(
      Fill(responseData.getWidth / 8, responseFromVfu.bits.data(7, 0)),
      Fill(responseData.getWidth / 16, responseFromVfu.bits.data(15, 0)),
      Fill(responseData.getWidth / 32, responseFromVfu.bits.data(31, 0))
    )
  ).asUInt

  // execute 1,2,4 times based on SEW, only write VRF when 32 bits is ready.
  val resultUpdate: UInt = (executeResult & writeMaskInBit) | (responseData & (~writeMaskInBit).asUInt)

  when(responseFromVfu.fire) {
    responseData := resultUpdate
  }
  val newFfoSuccess = (responseFromVfu.bits.ffoSuccess << writeIndex(0)).asUInt
  val updateFFO     = newFfoSuccess | ffoSuccess
  when(responseFromVfu.fire || requestFromSlot.fire) {
    ffoSuccess := maskAnd(!requestFromSlot.fire, updateFFO)
  }
  val updateVxsat   = (responseFromVfu.bits.vxsat ## responseFromVfu.bits.clipFail).orR || vxsatResult
  when(responseFromVfu.fire || requestFromSlot.fire) {
    vxsatResult := updateVxsat && !requestFromSlot.fire
  }

  requestFromSlot.ready := !requestReg.valid || isLastResponse

  responseWire.valid           := isLastResponse && requestReg.valid
  responseWire.bits.data       := resultUpdate
  responseWire.bits.ffoSuccess := updateFFO
  responseWire.bits.tag        := requestReg.bits.tag
  responseWire.bits.vxsat      := updateVxsat

  val pipeResponse: ValidIO[VFUResponseToSlot] = RegNext(responseWire, 0.U.asTypeOf(responseToSlot))
  responseToSlot <> pipeResponse
}
