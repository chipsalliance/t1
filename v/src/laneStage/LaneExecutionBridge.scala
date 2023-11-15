// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle

class LaneExecuteRequest(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  val src: Vec[UInt] = Vec(3, UInt(parameter.datapathWidth.W))
  val crossReadSource: Option[UInt] = Option.when(isLastSlot)(UInt((parameter.datapathWidth * 2).W))
  val bordersForMaskLogic: Bool = Bool()
  val mask: UInt = UInt((parameter.datapathWidth / 8).W)
  val groupCounter: UInt = UInt(parameter.groupNumberBits.W)
  val sSendResponse: Option[Bool] = Option.when(isLastSlot)(Bool())
}

class LaneExecuteResponse(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  val data: UInt = UInt(parameter.datapathWidth.W)
  val ffoIndex: UInt = UInt(log2Ceil(parameter.vLen / 8).W)
  val crossWriteData: Option[Vec[UInt]] = Option.when(isLastSlot)(Vec(2, UInt(parameter.datapathWidth.W)))
  val ffoSuccess: Option[Bool] = Option.when(isLastSlot)(Bool())
}

class ExecutionBridgeRecordQueue(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  val bordersForMaskLogic: Bool = Bool()
  val mask: UInt = UInt((parameter.datapathWidth / 8).W)
  val groupCounter: UInt = UInt(parameter.groupNumberBits.W)
  val sSendResponse: Option[Bool] = Option.when(isLastSlot)(Bool())
  val executeIndex: Bool = Bool()
  val source2: UInt = UInt(parameter.datapathWidth.W)
}

class LaneExecutionBridge(parameter: LaneParameter, isLastSlot: Boolean) extends Module {
  // request from lane slot
  val enqueue: DecoupledIO[LaneExecuteRequest] = IO(Flipped(Decoupled(new LaneExecuteRequest(parameter, isLastSlot))))
  // request from lane slot
  val dequeue: DecoupledIO[LaneExecuteResponse] = IO(Decoupled(new LaneExecuteResponse(parameter, isLastSlot)))
  // request to vfu
  val vfuRequest: DecoupledIO[SlotRequestToVFU] = IO(Decoupled(new SlotRequestToVFU(parameter)))
  // response from vfu
  val dataResponse: ValidIO[VFUResponseToSlot] = IO(Flipped(Valid(new VFUResponseToSlot(parameter))))
  val state: LaneState = IO(Input(new LaneState(parameter)))
  val ffoByOtherLanes: Bool = IO(Input(Bool()))
  val selfCompleted: Bool = IO(Input(Bool()))

  val decodeResult: DecodeBundle = state.decodeResult
  val notExecute: Bool = decodeResult(Decoder.dontNeedExecuteInLane)

  val executionRecord: ExecutionUnitRecord = RegInit(0.U.asTypeOf(new ExecutionUnitRecord(parameter)(isLastSlot)))
  val executionRecordValid = RegInit(false.B)

  /** result of reduce instruction. */
  val reduceResult: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))
  // execution result from execute unit
  val executionResult = RegInit(0.U(parameter.datapathWidth.W))
  val crossWriteLSB: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))

  /** mask format result for current `mask group` */
  val maskFormatResultForGroup: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(parameter.maskGroupWidth.W)))

  // Type widenReduce instructions occupy double the data registers because they need to retain the carry bit.
  val widenReduce: Bool = decodeResult(Decoder.widenReduce)
  // Whether cross-lane reading or cross-lane writing requires double execution
  val doubleExecution = decodeResult(Decoder.crossWrite) || decodeResult(Decoder.crossRead) || widenReduce
  // narrow type
  val narrow: Bool = !decodeResult(Decoder.crossWrite) && decodeResult(Decoder.crossRead)
  // todo: Need to collapse the results of combined calculations
  val reduceReady: Bool = true.B
  val recordQueueReadyForNoExecute = Wire(Bool())
  val recordDequeueReady: Bool = (if (isLastSlot) {
    vfuRequest.ready && (!doubleExecution || executionRecord.executeIndex)
  } else {
    vfuRequest.ready
  }) || recordQueueReadyForNoExecute
  val recordDequeueFire: Bool = executionRecordValid && recordDequeueReady
  when(enqueue.fire ^ recordDequeueFire) {
    executionRecordValid := enqueue.fire
  }
  if (isLastSlot) {
    val firstGroupNotExecute = decodeResult(Decoder.crossWrite) && !state.maskNotMaskedElement && !Mux(
      state.vSew1H(0),
      // sew = 8, 2 mask bit / group
      enqueue.bits.mask(1, 0).orR,
      enqueue.bits.mask(0)
    )
    // update execute index
    when(enqueue.fire || vfuRequest.fire) {
      // Mux(enqueue.fire, firstGroupNotExecute, true.B)
      // executeIndex = 0 when enqueue.fire && widenReduce
      executionRecord.executeIndex := firstGroupNotExecute || !enqueue.fire
    }
  }
  enqueue.ready := !executionRecordValid || recordDequeueReady

  when(enqueue.fire) {
    executionRecord.crossReadVS2 := decodeResult(Decoder.crossRead) && !decodeResult(Decoder.vwmacc)
    executionRecord.bordersForMaskLogic := enqueue.bits.bordersForMaskLogic
    executionRecord.mask := enqueue.bits.mask
    executionRecord.source := enqueue.bits.src
    executionRecord.crossReadSource.foreach(_ := enqueue.bits.crossReadSource.get)
    executionRecord.sSendResponse.foreach(_ := enqueue.bits.sSendResponse.get)
    executionRecord.groupCounter := enqueue.bits.groupCounter
    // 不满写的先读后写
    executionResult := enqueue.bits.src.last
  }

  /** collapse the dual SEW size operand for cross read.
   * it can be vd or src2.
   */
  val doubleCollapse: Option[UInt] = Option.when(isLastSlot) {
    val cutCrossReadData: Vec[UInt] = cutUInt(executionRecord.crossReadSource.get, parameter.datapathWidth)
    Mux(executionRecord.executeIndex, cutCrossReadData(1), cutCrossReadData(0))
  }

  // For cross read, extend 32 bit source1 to 64 bit, then select by executeIndex
  def dataExtend(data: UInt, sign: Bool): UInt = {
    val dataVec: Vec[UInt] = cutUInt(data, 8)
    val signVec: Seq[UInt] = dataVec.map(d => Fill(8, d(7) && sign))
    val sewIsZero = state.vSew1H(0)
    // sew = 0 把source1里的每一个 element 从 8bit 扩展成16bit
    //  -> s3 ## d3 ## s2 ## d2 ## s1 ## d1 ## s0 ## d0
    // sew = 1 把source1里的每一个 element 从 16bit 扩展成32bit
    //  -> s3 ## s3 ## d3 ## d2 ## s1 ## s1 ## d1 ## d0
    val sourceExtend = signVec(3) ## Mux(sewIsZero, dataVec(3), signVec(3)) ##
      Mux(sewIsZero, signVec(2), dataVec(3)) ## dataVec(2) ##
      signVec(1) ## Mux(sewIsZero, dataVec(1), signVec(1)) ##
      Mux(sewIsZero, signVec.head, dataVec(1)) ## dataVec(0)
    Mux(
      executionRecord.executeIndex,
      cutUInt(sourceExtend, 32)(1),
      cutUInt(sourceExtend, 32)(0)
    )
  }
  val extendSource1: UInt = dataExtend(executionRecord.source.head, !decodeResult(Decoder.unsigned0))
  val extendSource2: UInt = dataExtend(executionRecord.source(1), !decodeResult(Decoder.unsigned1))
  /** src1 for the execution
   * src1 has three types: V, I, X.
   */
  val normalSource1: UInt =
    Mux(
      decodeResult(Decoder.red) && !decodeResult(Decoder.maskLogic),
      reduceResult.getOrElse(0.U),
      executionRecord.source.head
    )
  val finalSource1: UInt = if (isLastSlot) {
    Mux(
      decodeResult(Decoder.crossWrite),
      extendSource1,
      normalSource1
    )
  } else {
    normalSource1
  }

  /** src2 for the execution,
   * need to take care of cross read.
   */
  val finalSource2: UInt = if (isLastSlot) {
    Mux(
      executionRecord.crossReadVS2,
      doubleCollapse.get,
      Mux(
        decodeResult(Decoder.crossWrite) || widenReduce,
        extendSource2,
        executionRecord.source(1)
      )
    )
  } else {
    executionRecord.source(1)
  }

  /** source3 有两种：adc & ma, c等处理mask的时候再处理
   * two types of source3:
   * - multiplier accumulate
   * - the third input of add with carry
   *
   * this line only handle the first type.
   */
  val finalSource3: UInt = if (isLastSlot) {
    Mux(
      decodeResult(Decoder.vwmacc),
      doubleCollapse.get,
      executionRecord.source(2)
    )
  } else {
    executionRecord.source(2)
  }

  /** use mask to fix the case that `vl` is not in the multiple of [[parameter.datapathWidth]].
   * it will fill the LSB of mask to `0`, mask it to not execute those elements.
   */
  val lastGroupMask: Bits = scanRightOr(UIntToOH(state.csr.vl(parameter.datapathWidthBits - 1, 0))) >> 1

  val fullMask: UInt = (-1.S(parameter.datapathWidth.W)).asUInt
  /** if [[executionRecord.bordersForMaskLogic]],
   * use [[lastGroupMask]] to mask the result otherwise use [[fullMask]]. */
  val maskCorrect: Bits = Mux(executionRecord.bordersForMaskLogic, lastGroupMask, fullMask)

  val maskExtend = Mux(state.vSew1H(1), FillInterleaved(2, executionRecord.mask(1, 0)), executionRecord.mask)
  vfuRequest.bits.src := VecInit(Seq(finalSource1, finalSource2, finalSource3, maskCorrect))
  vfuRequest.bits.opcode := decodeResult(Decoder.uop)
  vfuRequest.bits.mask := Mux(
    decodeResult(Decoder.adder),
    Mux(decodeResult(Decoder.maskSource), executionRecord.mask, 0.U(4.W)),
    maskExtend | Fill(4, !state.maskType)
  )
  val executeMask = executionRecord.mask | FillInterleaved(4, state.maskNotMaskedElement)
  vfuRequest.bits.executeMask := Mux(
    executionRecord.executeIndex,
    0.U(2.W) ## executeMask(3, 2),
    executeMask
  )
  vfuRequest.bits.sign0 := !decodeResult(Decoder.unsigned0)
  vfuRequest.bits.sign := !decodeResult(Decoder.unsigned1)
  vfuRequest.bits.reverse := decodeResult(Decoder.reverse)
  vfuRequest.bits.average := decodeResult(Decoder.average)
  vfuRequest.bits.saturate := decodeResult(Decoder.saturate)
  vfuRequest.bits.vxrm := state.csr.vxrm
  vfuRequest.bits.vSew := Mux(
    doubleExecution,
    state.csr.vSew + 1.U,
    state.csr.vSew
  )
  val shifterSizeBit = Mux(executionRecord.crossReadVS2, state.vSew1H(1, 0), state.vSew1H(2, 1))
  vfuRequest.bits.shifterSize := VecInit(cutUInt(finalSource1, 8).map(data =>
    Mux1H(
      shifterSizeBit,
      Seq(false.B ## data(3), data(4, 3))
    ) ## data(2, 0)
  )).asUInt

  vfuRequest.bits.rem := decodeResult(Decoder.uop)(0)
  vfuRequest.bits.executeIndex := executionRecord.executeIndex
  vfuRequest.bits.popInit := reduceResult.getOrElse(0.U)
  vfuRequest.bits.groupIndex := executionRecord.groupCounter
  vfuRequest.bits.laneIndex := state.laneIndex
  vfuRequest.bits.complete := ffoByOtherLanes || selfCompleted
  vfuRequest.bits.maskType := state.maskType
  vfuRequest.bits.narrow := narrow
  vfuRequest.bits.unitSelet.foreach(_ := decodeResult(Decoder.fpExecutionType))
  vfuRequest.bits.floatMul.foreach(_ := decodeResult(Decoder.floatMul))

  // from float csr
  vfuRequest.bits.roundingMode.foreach(_ := state.csr.vxrm)

  vfuRequest.valid := executionRecordValid
  //--- record <-> vfu end ---
  //                        --- record <-> record pipe queue <-> response stage
  val recordQueue = Module(
    new Queue(new ExecutionBridgeRecordQueue(parameter, isLastSlot), 2, flow = true)
  )
  assert(!vfuRequest.fire || recordQueue.io.enq.ready)
  recordQueueReadyForNoExecute := notExecute && recordQueue.io.enq.ready
  recordQueue.io.enq.valid := vfuRequest.valid && (vfuRequest.ready || notExecute)
  recordQueue.io.enq.bits.bordersForMaskLogic := executionRecord.bordersForMaskLogic
  recordQueue.io.enq.bits.mask := executionRecord.mask
  recordQueue.io.enq.bits.groupCounter := executionRecord.groupCounter
  recordQueue.io.enq.bits.executeIndex := executionRecord.executeIndex
  recordQueue.io.enq.bits.source2 := executionRecord.source(1)
  recordQueue.io.enq.bits.sSendResponse.zip(executionRecord.sSendResponse).foreach {
    case (sink, source) => sink := source
  }
  //--- vfu <-> write queue start ---

  /** select from VFU, send to [[executionResult]], [[crossWriteLSB]], [[crossWriteMSB]]. */
  val dataDequeue: UInt = Mux(notExecute, recordQueue.io.deq.bits.source2, dataResponse.bits.data)
  val deqVec = cutUInt(dataDequeue, 8)

  // vsew = 8     -> d2 ## d0
  // vasew = 16   -> d1 ## d0
  val narrowUpdate = (Mux(state.vSew1H(0), deqVec(2), deqVec(1)) ## deqVec(0) ## executionResult) >> (parameter.datapathWidth / 2)
  val lastSlotDataUpdate: UInt = Mux(narrow, narrowUpdate.asUInt, dataDequeue)
  // update execute result
  when(dataResponse.valid) {
    // update the [[executionResult]]
    executionResult := lastSlotDataUpdate

    crossWriteLSB.foreach { crossWriteData =>
      crossWriteData := dataDequeue
    }
  }

  /** update value for [[maskFormatResultForGroup]] */
  val maskFormatResultUpdate: Option[UInt] = Option.when(isLastSlot)(Wire(UInt(parameter.datapathWidth.W)))

  val updateReduceResult: Option[UInt] = Option.when(isLastSlot)(Wire(UInt(parameter.datapathWidth.W)))
  // update mask result
  if (isLastSlot) {
    val maskResult: UInt = dataResponse.bits.adderMaskResp
    /** update value for [[maskFormatResultUpdate]],
     * it comes from ALU.
     */
    val elementMaskFormatResult = Mux1H(
      state.vSew1H(2, 0),
      Seq(
        // 32bit, 4 bit per data group, it will had 8 data groups -> executeIndex1H << 4 * groupCounter(2, 0)
        maskResult << (recordQueue.io.deq.bits.groupCounter(2, 0) ## 0.U(2.W)),
        // 2 bit per data group, it will had 16 data groups -> executeIndex1H << 2 * groupCounter(3, 0)
        maskResult(1, 0) <<
          (recordQueue.io.deq.bits.groupCounter(3, 0) ## false.B),
        // 1 bit per data group, it will had 32 data groups -> executeIndex1H << 1 * groupCounter(4, 0)
        maskResult(0) << recordQueue.io.deq.bits.groupCounter(4, 0)
      )
    ).asUInt

    maskFormatResultUpdate.get := maskFormatResultForGroup.get | elementMaskFormatResult

    val updateMaskResult: Option[Bool] = recordQueue.io.deq.bits.sSendResponse.map(!_ && dequeue.fire)

    // update `maskFormatResultForGroup`
    when(dataResponse.valid || updateMaskResult.get) {
      maskFormatResultForGroup.foreach(_ := Mux(dataResponse.valid, maskFormatResultUpdate.get, 0.U))
    }
    // masked element don't update 'reduceResult'
    val reduceUpdateByteMask: UInt = Mux1H(
      state.vSew1H,
      Seq(
        recordQueue.io.deq.bits.mask,
        FillInterleaved(2, recordQueue.io.deq.bits.mask(1, 0)),
        FillInterleaved(4, recordQueue.io.deq.bits.mask(0))
      )
    )
    val reduceUpdateBitMask = FillInterleaved(8, reduceUpdateByteMask)
    updateReduceResult.get := (dataDequeue & reduceUpdateBitMask) | (reduceResult.get & ~reduceUpdateBitMask)
    // update `reduceResult`
    when(dataResponse.valid || updateMaskResult.get) {
      reduceResult.get := Mux(dataResponse.valid && decodeResult(Decoder.red), updateReduceResult.get, 0.U)
    }
  }

  // queue before dequeue
  val queue: Queue[LaneExecuteResponse] = Module(new Queue(new LaneExecuteResponse(parameter, isLastSlot), 4))
  if (isLastSlot) {
    queue.io.enq.bits.data := Mux(
      decodeResult(Decoder.maskDestination),
      maskFormatResultUpdate.get,
      Mux(
        decodeResult(Decoder.red),
        updateReduceResult.get,
        lastSlotDataUpdate
      )
    )
  } else {
    queue.io.enq.bits.data := dataDequeue
  }
  queue.io.enq.bits.ffoIndex := recordQueue.io.deq.bits.groupCounter ## dataResponse.bits.data(4, 0)
  queue.io.enq.bits.crossWriteData.foreach(_ := VecInit((crossWriteLSB ++ Seq(dataDequeue)).toSeq))
  queue.io.enq.bits.ffoSuccess.foreach(_ := dataResponse.bits.ffoSuccess)
  recordQueue.io.deq.ready := dataResponse.valid || (notExecute && queue.io.enq.ready)
  queue.io.enq.valid :=
    recordQueue.io.deq.valid &&
      ((dataResponse.valid && reduceReady &&
        (!doubleExecution || recordQueue.io.deq.bits.executeIndex)) ||
      notExecute)
  assert(!queue.io.enq.valid || queue.io.enq.ready)
  dequeue <> queue.io.deq
}
