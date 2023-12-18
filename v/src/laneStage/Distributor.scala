// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v

import chisel3._
import chisel3.util._


class Distributor[T <: SlotRequestToVFU, B <: VFUResponseToSlot](enqueue: T, dequeue: B)
                                                                (multiCycle: Boolean = false) extends Module {
  // request to vfu
  val requestToVfu: DecoupledIO[SlotRequestToVFU] = IO(Decoupled(enqueue))
  // response from vfu
  val responseFromVfu: ValidIO[VFUResponseToSlot] = IO(Flipped(Valid(dequeue)))
  // request from LaneExecutionBridge
  val requestFromSlot: DecoupledIO[SlotRequestToVFU] = IO(Flipped(Decoupled(enqueue)))
  // response to LaneExecutionBridge
  val responseToSlot: ValidIO[VFUResponseToSlot] = IO(Valid(dequeue))

  val requestReg: ValidIO[SlotRequestToVFU] = RegInit(0.U.asTypeOf(Valid(enqueue)))
  val sendRequestValid: Bool = RegInit(false.B)
  val ffoSuccess: Bool = RegInit(false.B)
  val responseData: UInt = RegInit(0.U(enqueue.src.head.getWidth.W))
  val executeIndex = RegInit(0.U(2.W))

  // Data path width is always 32 when fusion
  val vSew1HReq: UInt = UIntToOH(requestFromSlot.bits.vSew)(2, 0)
  val vSew1H = UIntToOH(requestReg.bits.vSew)(2, 0)

  when(requestFromSlot.fire ^ responseToSlot.fire) {
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
  val nextExecuteIndexForNextGroup: UInt = Mux1H(
    vSew1HReq(1, 0),
    Seq(
      OHToUInt(ffo(requestFromSlot.bits.executeMask)),
      !requestFromSlot.bits.executeMask(0) ## false.B,
    )
  )

  // current one hot depends on execute index
  val currentOHForExecuteGroup: UInt = UIntToOH(executeIndex)
  // Remaining to be requested
  val remainder: UInt = requestReg.bits.executeMask & (~scanRightOr(currentOHForExecuteGroup)).asUInt
  // Finds the first unfiltered execution.
  val nextIndex1H: UInt = ffo(remainder)
  val nextExecuteIndex: UInt = Mux1H(
    vSew1H(1, 0),
    Seq(
      OHToUInt(nextIndex1H),
      2.U
    )
  )

  when(requestToVfu.fire || requestFromSlot.fire) {
    executeIndex := Mux(requestFromSlot.fire, nextExecuteIndexForNextGroup, nextExecuteIndex)
  }

  val byteMaskForExecution = Mux1H(
    vSew1H,
    Seq(
      currentOHForExecuteGroup,
      executeIndex(1) ## executeIndex(1) ##
        !executeIndex(1) ## !executeIndex(1),
      15.U(4.W)
    )
  )

  /** the bit-level mask of current execution. */
  val bitMaskForExecution: UInt = FillInterleaved(8, byteMaskForExecution)

  def CollapseOperand(data: UInt, sign: Bool = false.B): UInt = {
    val dataMasked: UInt = data & bitMaskForExecution
    // when sew = 0
    val collapse0 = Seq.tabulate(4)(i => dataMasked(8 * i + 7, 8 * i)).reduce(_ | _)
    // when sew = 1
    val collapse1 = Seq.tabulate(2)(i => dataMasked(16 * i + 15, 16 * i)).reduce(_ | _)
    Mux1H(
      vSew1H,
      Seq(
        Fill(25, sign && collapse0(7)) ## collapse0,
        Fill(17, sign && collapse1(15)) ## collapse1,
        (sign && data(31)) ## data
      )
    )
  }
  val signSeq: Seq[Bool] = Seq(requestReg.bits.sign0, requestReg.bits.sign, false.B, false.B)

  requestToVfu.valid := sendRequestValid
  requestToVfu.bits := requestReg.bits
  requestToVfu.bits.executeIndex := executeIndex
  requestToVfu.bits.src := VecInit(
    requestReg.bits.src.zip(signSeq).map{ case (d, s) => CollapseOperand(d, s) }
  )
  requestToVfu.bits.mask := (requestReg.bits.mask & currentOHForExecuteGroup).orR
  requestToVfu.bits.shifterSize := Mux1H(currentOHForExecuteGroup, cutUInt(requestReg.bits.shifterSize, 5))

  val isLastRequest: Bool = Mux1H(vSew1H, Seq(!remainder.orR, !remainder(1, 0).orR, true.B))
  val writeIndex: UInt = Wire(UInt(4.W))
  val isLastResponse: Bool = Wire(Bool())
  lastRequestFire := isLastRequest && requestToVfu.fire
  writeIndex := executeIndex
  isLastResponse := isLastRequest
  if (multiCycle) {
    val executeQueue = Module(new Queue(UInt(3.W), 4))
    executeQueue.io.enq.bits := isLastRequest ## executeIndex
    executeQueue.io.enq.valid := requestToVfu.fire
    executeQueue.io.deq.ready := responseFromVfu.fire
    writeIndex := executeQueue.io.deq.bits(1, 0)
    isLastResponse := executeQueue.io.deq.bits(2) && responseFromVfu.valid && executeQueue.io.deq.valid
  }

  // update result
  val writeIndex1H = UIntToOH(writeIndex)

  /** VRF byte level mask */
  val writeMaskInByte = Mux1H(
    vSew1H(2, 0),
    Seq(
      writeIndex1H,
      writeIndex(1) ## writeIndex(1) ## !writeIndex(1) ## !writeIndex(1),
      "b1111".U(4.W)
    )
  )

  /** VRF bit level mask */
  val writeMaskInBit: UInt = FillInterleaved(8, writeMaskInByte)

  /** output of execution unit need to align to VRF in bit level(used in dynamic shift)
   * TODO: fix me
   */
  val dataOffset: UInt = writeIndex ## 0.U(3.W)

  // TODO: this is a dynamic shift logic, but if we switch to parallel execution unit, we don't need it anymore.
  val executeResult = (responseFromVfu.bits.data << dataOffset).asUInt(enqueue.src.head.getWidth - 1, 0)

  // execute 1,2,4 times based on SEW, only write VRF when 32 bits is ready.
  val resultUpdate: UInt = (executeResult & writeMaskInBit) | (responseData & (~writeMaskInBit).asUInt)

  when(responseFromVfu.fire) {
    responseData := resultUpdate
  }
  val updateFFO = responseFromVfu.bits.ffoSuccess || ffoSuccess
  when(responseFromVfu.fire || requestFromSlot.fire) {
    ffoSuccess := updateFFO && !requestFromSlot.fire
  }

  requestFromSlot.ready := !requestReg.valid || isLastResponse
  responseToSlot.valid := isLastResponse && requestReg.valid
  // todo: clipFail ffoSuccess vxsat exceptionFlags?
  responseToSlot.bits := DontCare
  responseToSlot.bits.data := resultUpdate
  responseToSlot.bits.ffoSuccess := updateFFO
  responseToSlot.bits.tag := requestReg.bits.tag
}
