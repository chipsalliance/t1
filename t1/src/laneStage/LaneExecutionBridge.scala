// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle
import chisel3.ltl._
import chisel3.ltl.Sequence._
import org.chipsalliance.t1.rtl.{
  cutUInt,
  getExecuteUnitTag,
  CSRInterface,
  ExecutionUnitRecord,
  LaneParameter,
  SlotRequestToVFU,
  VFUResponseToSlot
}
import org.chipsalliance.t1.rtl.decoder.Decoder
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}

class LaneExecuteRequest(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  val src:                 Vec[UInt]    = Vec(3, UInt(parameter.datapathWidth.W))
  val crossReadSource:     Option[UInt] = Option.when(isLastSlot)(UInt((parameter.datapathWidth * 2).W))
  val bordersForMaskLogic: Bool         = Bool()
  val mask:                UInt         = UInt((parameter.datapathWidth / 8).W)
  val maskForFilter:       UInt         = UInt((parameter.datapathWidth / 8).W)
  val groupCounter:        UInt         = UInt(parameter.groupNumberBits.W)
  val sSendResponse:       Option[Bool] = Option.when(isLastSlot)(Bool())
  // pipe state
  val decodeResult:        DecodeBundle = Decoder.bundle(parameter.decoderParam)
  val vSew1H:              UInt         = UInt(3.W)
  val csr:                 CSRInterface = new CSRInterface(parameter.vlMaxBits)
  val maskType:            Bool         = Bool()
  // Newly added in LaneExecutionBridge
  val laneIndex:           UInt         = UInt(parameter.laneNumberBits.W)
  val instructionIndex:    UInt         = UInt(parameter.instructionIndexBits.W)
}

class LaneExecuteResponse(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  val data:           UInt              = UInt(parameter.datapathWidth.W)
  val ffoIndex:       UInt              = UInt(log2Ceil(parameter.vLen / parameter.laneNumber).W)
  val crossWriteData: Option[Vec[UInt]] = Option.when(isLastSlot)(Vec(2, UInt(parameter.datapathWidth.W)))
  val ffoSuccess:     Option[Bool]      = Option.when(isLastSlot)(Bool())
  val fpReduceValid:  Option[Bool]      = Option.when(parameter.fpuEnable && isLastSlot)(Bool())
}

class ExecutionBridgeRecordQueue(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  val bordersForMaskLogic: Bool         = Bool()
  val maskForFilter:       UInt         = UInt((parameter.datapathWidth / 8).W)
  val groupCounter:        UInt         = UInt(parameter.groupNumberBits.W)
  val sSendResponse:       Option[Bool] = Option.when(isLastSlot)(Bool())
  val executeIndex:        Bool         = Bool()
  val source2:             UInt         = UInt(parameter.datapathWidth.W)
  // pipe state
  val decodeResult:        DecodeBundle = Decoder.bundle(parameter.decoderParam)
  val vSew1H:              UInt         = UInt(3.W)
  val instructionIndex:    UInt         = UInt(parameter.instructionIndexBits.W)
}

@instantiable
class LaneExecutionBridge(parameter: LaneParameter, isLastSlot: Boolean, slotIndex: Int) extends Module {
  // request from lane slot
  @public
  val enqueue:      DecoupledIO[LaneExecuteRequest]  = IO(Flipped(Decoupled(new LaneExecuteRequest(parameter, isLastSlot))))
  // request from lane slot
  @public
  val dequeue:      DecoupledIO[LaneExecuteResponse] = IO(Decoupled(new LaneExecuteResponse(parameter, isLastSlot)))
  // request to vfu
  @public
  val vfuRequest:   DecoupledIO[SlotRequestToVFU]    = IO(Decoupled(new SlotRequestToVFU(parameter)))
  // response from vfu
  @public
  val dataResponse: ValidIO[VFUResponseToSlot]       = IO(Flipped(Valid(new VFUResponseToSlot(parameter))))

  @public
  val ffoByOtherLanes: Bool = IO(Input(Bool()))
  @public
  val selfCompleted:   Bool = IO(Input(Bool()))

  @public
  val executeDecode:  DecodeBundle = IO(Output(Decoder.bundle(parameter.decoderParam)))
  @public
  val responseDecode: DecodeBundle = IO(Output(Decoder.bundle(parameter.decoderParam)))
  @public
  val responseIndex:  UInt         = IO(Output(UInt(parameter.instructionIndexBits.W)))

  val executionRecord: ExecutionUnitRecord = RegInit(0.U.asTypeOf(new ExecutionUnitRecord(parameter)(isLastSlot)))
  val executionRecordValid = RegInit(false.B)

  /** result of reduce instruction. */
  val reduceResult: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))
  // execution result from execute unit
  val executionResult = RegInit(0.U(parameter.datapathWidth.W))
  val crossWriteLSB:          Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))
  val outStandingRequestSize: Int          = 4.max(parameter.vfuInstantiateParameter.maxLatency + 3)
  val outStanding:            UInt         = RegInit(0.U(log2Ceil(outStandingRequestSize).W))
  val outStandingUpdate:      UInt         = Mux(vfuRequest.fire, 1.U(outStanding.getWidth.W), (-1.S(outStanding.getWidth.W)).asUInt)
  val responseFinish:         Bool         = outStanding === 0.U
  when(vfuRequest.fire ^ dataResponse.fire) {
    outStanding := outStanding + outStandingUpdate
  }

  /** mask format result for current `mask group` */
  val maskFormatResultForGroup: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(parameter.maskGroupWidth.W)))

  val waitFirstValidFire: Option[Bool] = Option.when(parameter.fpuEnable && isLastSlot) {
    RegEnable(
      (enqueue.bits.groupCounter === 0.U) && !enqueue.bits.maskForFilter(0),
      false.B,
      enqueue.fire && ((enqueue.bits.groupCounter === 0.U) || enqueue.bits.maskForFilter(0))
    )
  }

  val firstRequestFire: Option[Bool] = Option.when(parameter.fpuEnable && isLastSlot) {
    enqueue.fire && (enqueue.bits.groupCounter === 0.U || waitFirstValidFire.get) && enqueue.bits.maskForFilter(0)
  }

  // Whether cross-lane reading or cross-lane writing requires double execution
  // data request in executionRecord need double execution
  val doubleExecutionInRecord: Bool =
    executionRecord.decodeResult(Decoder.crossWrite) ||
      executionRecord.decodeResult(Decoder.crossRead) ||
      executionRecord.decodeResult(Decoder.widenReduce)

  // data in executionRecord is narrow type
  val narrowInRecord: Bool         = !executionRecord.decodeResult(Decoder.crossWrite) &&
    executionRecord.decodeResult(Decoder.crossRead)
  // reduceReady is false: Need to collapse the results of combined calculations
  val reduceReady:    Bool         = WireDefault(true.B)
  val sendFoldReduce: Option[Bool] = Option.when(isLastSlot)(Wire(Bool()))
  val recordQueueReadyForNoExecute = Wire(Bool())
  val recordDequeueReady: Bool = (if (isLastSlot) {
                                    vfuRequest.ready && (!doubleExecutionInRecord || executionRecord.executeIndex)
                                  } else {
                                    vfuRequest.ready
                                  }) || recordQueueReadyForNoExecute
  val recordDequeueFire:  Bool = executionRecordValid && recordDequeueReady
  when(enqueue.fire ^ recordDequeueFire) {
    executionRecordValid := enqueue.fire
  }
  if (isLastSlot) {
    val firstGroupNotExecute = enqueue.bits.decodeResult(Decoder.crossWrite) && !Mux(
      enqueue.bits.vSew1H(0),
      // sew = 8, 2 mask bit / group
      enqueue.bits.maskForFilter(1, 0).orR,
      enqueue.bits.maskForFilter(0)
    )
    // update execute index
    when(enqueue.fire || vfuRequest.fire) {
      // Mux(enqueue.fire, firstGroupNotExecute, true.B)
      // executeIndex = 0 when enqueue.fire && widenReduce
      executionRecord.executeIndex := firstGroupNotExecute || !enqueue.fire
    }
  }

  when(enqueue.fire) {
    executionRecord.crossReadVS2        := enqueue.bits.decodeResult(Decoder.crossRead) && !enqueue.bits.decodeResult(
      Decoder.vwmacc
    )
    executionRecord.bordersForMaskLogic := enqueue.bits.bordersForMaskLogic && enqueue.bits.decodeResult(
      Decoder.maskLogic
    )
    executionRecord.maskForMaskInput    := enqueue.bits.mask
    executionRecord.maskForFilter       := enqueue.bits.maskForFilter
    executionRecord.source              := enqueue.bits.src
    executionRecord.crossReadSource.foreach(_ := enqueue.bits.crossReadSource.get)
    executionRecord.sSendResponse.foreach(_ := enqueue.bits.sSendResponse.get)
    executionRecord.groupCounter        := enqueue.bits.groupCounter
    executionRecord.decodeResult        := enqueue.bits.decodeResult
    executionRecord.vSew1H              := enqueue.bits.vSew1H
    executionRecord.csr                 := enqueue.bits.csr
    executionRecord.maskType            := enqueue.bits.maskType
    executionRecord.laneIndex           := enqueue.bits.laneIndex
    executionRecord.instructionIndex    := enqueue.bits.instructionIndex
  }

  /** collapse the dual SEW size operand for cross read. it can be vd or src2.
    */
  val doubleCollapse: Option[UInt] = Option.when(isLastSlot) {
    val cutCrossReadData: Vec[UInt] = cutUInt(executionRecord.crossReadSource.get, parameter.datapathWidth)
    Mux(executionRecord.executeIndex, cutCrossReadData(1), cutCrossReadData(0))
  }

  // For cross read, extend 32 bit source1 to 64 bit, then select by executeIndex
  def dataExtend(data: UInt, sign: Bool): UInt = {
    val dataVec: Vec[UInt] = cutUInt(data, 8)
    val signVec: Seq[UInt] = dataVec.map(d => Fill(8, d(7) && sign))
    val sewIsZero    = executionRecord.vSew1H(0)
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
  val extendSource1:                      UInt = dataExtend(executionRecord.source.head, !executionRecord.decodeResult(Decoder.unsigned0))
  val extendSource2:                      UInt = dataExtend(executionRecord.source(1), !executionRecord.decodeResult(Decoder.unsigned1))

  /** src1 for the execution src1 has three types: V, I, X.
    */
  val normalSource1: UInt =
    Mux(
      executionRecord.decodeResult(Decoder.red) && !executionRecord.decodeResult(Decoder.maskLogic),
      reduceResult.getOrElse(0.U),
      executionRecord.source.head
    )
  val finalSource1:  UInt = if (isLastSlot) {
    Mux(
      executionRecord.decodeResult(Decoder.crossWrite) || narrowInRecord,
      extendSource1,
      normalSource1
    )
  } else {
    normalSource1
  }

  val reduceFoldSource2: Option[UInt] = Option.when(isLastSlot)(Wire(UInt(parameter.datapathWidth.W)))

  /** src2 for the execution, need to take care of cross read.
    */
  val finalSource2: UInt = if (isLastSlot) {
    Mux(
      executionRecord.crossReadVS2,
      doubleCollapse.get,
      Mux(
        executionRecord.decodeResult(Decoder.crossWrite) || (executionRecord.decodeResult(
          Decoder.widenReduce
        ) && !sendFoldReduce.get),
        extendSource2,
        Mux(
          sendFoldReduce.get,
          reduceFoldSource2.get,
          executionRecord.source(1)
        )
      )
    )
  } else {
    executionRecord.source(1)
  }

  /** source3 有两种：adc & ma, c等处理mask的时候再处理 two types of source3:
    *   - multiplier accumulate
    *   - the third input of add with carry
    *
    * this line only handle the first type.
    */
  val finalSource3: UInt = if (isLastSlot) {
    Mux(
      executionRecord.decodeResult(Decoder.vwmacc),
      doubleCollapse.get,
      executionRecord.source(2)
    )
  } else {
    executionRecord.source(2)
  }

  /** use mask to fix the case that `vl` is not in the multiple of [[parameter.datapathWidth]]. it will fill the LSB of
    * mask to `0`, mask it to not execute those elements.
    */
  val lastGroupMask: Bits = scanRightOr(UIntToOH(executionRecord.csr.vl(parameter.datapathWidthBits - 1, 0))) >> 1

  val fullMask: UInt = (-1.S(parameter.datapathWidth.W)).asUInt

  /** if [[executionRecord.bordersForMaskLogic]], use [[lastGroupMask]] to mask the result otherwise use [[fullMask]].
    */
  val maskCorrect: Bits = Mux(executionRecord.bordersForMaskLogic, lastGroupMask, fullMask)

  val maskExtend = Mux(
    executionRecord.vSew1H(1),
    FillInterleaved(2, executionRecord.maskForMaskInput(1, 0)),
    executionRecord.maskForMaskInput
  )
  vfuRequest.bits.src         := VecInit(Seq(finalSource1, finalSource2, finalSource3, maskCorrect))
  vfuRequest.bits.opcode      := executionRecord.decodeResult(Decoder.uop)
  vfuRequest.bits.mask        := Mux(
    executionRecord.decodeResult(Decoder.adder),
    Mux(executionRecord.decodeResult(Decoder.maskSource), executionRecord.maskForMaskInput, 0.U(4.W)),
    maskExtend | Fill(4, !executionRecord.maskType)
  )
  vfuRequest.bits.executeMask := Mux(
    executionRecord.executeIndex,
    0.U(2.W) ## executionRecord.maskForFilter(3, 2),
    executionRecord.maskForFilter
  )
  vfuRequest.bits.sign0       := !executionRecord.decodeResult(Decoder.unsigned0)
  vfuRequest.bits.sign        := !executionRecord.decodeResult(Decoder.unsigned1)
  vfuRequest.bits.reverse     := executionRecord.decodeResult(Decoder.reverse)
  vfuRequest.bits.average     := executionRecord.decodeResult(Decoder.average)
  vfuRequest.bits.saturate    := executionRecord.decodeResult(Decoder.saturate)
  vfuRequest.bits.vxrm        := executionRecord.csr.vxrm
  vfuRequest.bits.vSew        := Mux(
    doubleExecutionInRecord,
    executionRecord.csr.vSew + 1.U,
    executionRecord.csr.vSew
  )
  val shifterSizeBit = Mux(executionRecord.crossReadVS2, executionRecord.vSew1H(1, 0), executionRecord.vSew1H(2, 1))
  vfuRequest.bits.shifterSize := VecInit(
    cutUInt(finalSource1, 8).map(data =>
      Mux1H(
        shifterSizeBit,
        Seq(false.B ## data(3), data(4, 3))
      ) ## data(2, 0)
    )
  ).asUInt

  vfuRequest.bits.rem          := executionRecord.decodeResult(Decoder.uop)(0)
  vfuRequest.bits.executeIndex := executionRecord.executeIndex
  vfuRequest.bits.popInit      := reduceResult.getOrElse(0.U)
  vfuRequest.bits.groupIndex   := executionRecord.groupCounter
  vfuRequest.bits.laneIndex    := executionRecord.laneIndex
  vfuRequest.bits.complete     := ffoByOtherLanes || selfCompleted
  vfuRequest.bits.maskType     := executionRecord.maskType
  vfuRequest.bits.narrow       := narrowInRecord
  vfuRequest.bits.unitSelet.foreach(_ := executionRecord.decodeResult(Decoder.fpExecutionType))
  vfuRequest.bits.floatMul.foreach(_ := executionRecord.decodeResult(Decoder.floatMul))
  vfuRequest.bits.tag          := slotIndex.U

  // from float csr
  vfuRequest.bits.roundingMode.foreach(_ := executionRecord.csr.vxrm)
  executeDecode := executionRecord.decodeResult

  vfuRequest.valid := (if (isLastSlot) {
                         (executionRecordValid || sendFoldReduce.get) && (responseFinish || !executionRecord
                           .decodeResult(Decoder.red))
                       } else {
                         executionRecordValid
                       }) && !executionRecord.decodeResult(Decoder.dontNeedExecuteInLane)
  // --- record <-> vfu end ---
  //                        --- record <-> record pipe queue <-> response stage
  val recordQueue = Queue.io(
    new ExecutionBridgeRecordQueue(parameter, isLastSlot),
    entries = 4.max(parameter.vfuInstantiateParameter.maxLatency + 3),
    flow = true
  )
  AssertProperty(BoolSequence(!vfuRequest.fire || recordQueue.enq.ready))
  val enqNotExecute: Bool = executionRecord.decodeResult(Decoder.dontNeedExecuteInLane)
  recordQueueReadyForNoExecute             := enqNotExecute && recordQueue.enq.ready
  recordQueue.enq.valid                    := executionRecordValid && (vfuRequest.ready || enqNotExecute)
  recordQueue.enq.bits.bordersForMaskLogic := executionRecord.bordersForMaskLogic
  recordQueue.enq.bits.maskForFilter       := executionRecord.maskForFilter
  recordQueue.enq.bits.groupCounter        := executionRecord.groupCounter
  recordQueue.enq.bits.executeIndex        := executionRecord.executeIndex
  recordQueue.enq.bits.source2             := executionRecord.source(1)
  recordQueue.enq.bits.sSendResponse.zip(executionRecord.sSendResponse).foreach { case (sink, source) =>
    sink := source
  }
  recordQueue.enq.bits.decodeResult        := executionRecord.decodeResult
  recordQueue.enq.bits.vSew1H              := executionRecord.vSew1H
  recordQueue.enq.bits.instructionIndex    := executionRecord.instructionIndex
  // --- vfu <-> write queue start ---

  /** same as [[doubleExecutionInRecord]] data request in [[recordQueue.deq]] need double execution
    */
  val doubleExecutionInQueue: Bool =
    recordQueue.deq.bits.decodeResult(Decoder.crossWrite) ||
      recordQueue.deq.bits.decodeResult(Decoder.crossRead) ||
      recordQueue.deq.bits.decodeResult(Decoder.widenReduce)
  val recordNotExecute = recordQueue.deq.bits.decodeResult(Decoder.dontNeedExecuteInLane)

  /** select from VFU, send to [[executionResult]], [[crossWriteLSB]], [[crossWriteMSB]]. */
  val dataDequeue: UInt = Mux(recordNotExecute, recordQueue.deq.bits.source2, dataResponse.bits.data)
  val deqVec = cutUInt(dataDequeue, 8)

  // vsew = 8     -> d2 ## d0
  // vasew = 16   -> d1 ## d0
  val narrowUpdate = (Mux(recordQueue.deq.bits.vSew1H(0), deqVec(2), deqVec(1)) ## deqVec(
    0
  ) ## executionResult) >> (parameter.datapathWidth / 2)
  // data group in recordQueue.deq is narrow type
  val narrowInQueue:      Bool = !recordQueue.deq.bits.decodeResult(Decoder.crossWrite) &&
    recordQueue.deq.bits.decodeResult(Decoder.crossRead)
  val lastSlotDataUpdate: UInt = Mux(narrowInQueue, narrowUpdate.asUInt, dataDequeue)
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
  val updateMaskResult:   Option[Bool] = Option.when(isLastSlot)(Wire(Bool()))
  val reduceLastResponse = WireDefault(false.B)
  // update mask result
  if (isLastSlot) {
    val maskResult: UInt = dataResponse.bits.adderMaskResp

    /** update value for [[maskFormatResultUpdate]], it comes from ALU.
      */
    val elementMaskFormatResult = Mux1H(
      recordQueue.deq.bits.vSew1H(2, 0),
      Seq(
        // 32bit, 4 bit per data group, it will had 8 data groups -> executeIndex1H << 4 * groupCounter(2, 0)
        maskResult << (recordQueue.deq.bits.groupCounter(2, 0) ## 0.U(2.W)),
        // 2 bit per data group, it will had 16 data groups -> executeIndex1H << 2 * groupCounter(3, 0)
        maskResult(1, 0) <<
          (recordQueue.deq.bits.groupCounter(3, 0) ## false.B),
        // 1 bit per data group, it will had 32 data groups -> executeIndex1H << 1 * groupCounter(4, 0)
        maskResult(0) << recordQueue.deq.bits.groupCounter(4, 0)
      )
    ).asUInt

    maskFormatResultUpdate.get := maskFormatResultForGroup.get | elementMaskFormatResult

    // update `maskFormatResultForGroup`
    when(dataResponse.valid || updateMaskResult.get) {
      maskFormatResultForGroup.foreach(_ := Mux(updateMaskResult.get, 0.U, maskFormatResultUpdate.get))
    }
    val normalReduceMask = Mux1H(
      recordQueue.deq.bits.vSew1H,
      Seq(
        recordQueue.deq.bits.maskForFilter,
        FillInterleaved(2, recordQueue.deq.bits.maskForFilter(1, 0)),
        FillInterleaved(4, recordQueue.deq.bits.maskForFilter(0))
      )
    )
    val widenReduceMask  = Mux1H(
      recordQueue.deq.bits.vSew1H(1, 0),
      Seq(
        FillInterleaved(
          2,
          Mux(
            dataResponse.bits.executeIndex(0),
            recordQueue.deq.bits.maskForFilter(3, 2),
            recordQueue.deq.bits.maskForFilter(1, 0)
          )
        ),
        FillInterleaved(
          4,
          Mux(
            dataResponse.bits.executeIndex(0),
            recordQueue.deq.bits.maskForFilter(1),
            recordQueue.deq.bits.maskForFilter(0)
          )
        )
      )
    )
    // masked element don't update 'reduceResult'
    val reduceUpdateByteMask: UInt =
      Mux(recordQueue.deq.bits.decodeResult(Decoder.widenReduce), widenReduceMask, normalReduceMask)
    val foldUpdateMask = Wire(UInt(4.W))
    updateReduceResult.get := {
      val dataVec   = cutUInt(dataDequeue, 8)
      val ResultVec = cutUInt(reduceResult.get, 8)
      VecInit(dataVec.zipWithIndex.map { case (d, i) =>
        Mux1H(
          Seq(
            Mux(sendFoldReduce.get, foldUpdateMask(i), reduceUpdateByteMask(i)),
            !sendFoldReduce.get && !reduceUpdateByteMask(i)
          ),
          Seq(d, ResultVec(i))
        )
      }).asUInt
    }
    // update `reduceResult`
    when((dataResponse.valid && recordQueue.deq.bits.decodeResult(Decoder.red)) || updateMaskResult.get) {
      reduceResult.get := Mux(updateMaskResult.get, 0.U, updateReduceResult.get)
    }
    // 前面的可能会有mask = 0 但是还试图更新reduce result 的情况, todo: 去掉mask = 0 时的执行
    when(enqueue.fire) {
      // red max min 的第一次不能和上一个指令的reduce结果比, 只能和自己比
      firstRequestFire.foreach { first =>
        when(first && enqueue.bits.decodeResult(Decoder.float)) {
          reduceResult.foreach {
            // flot compare compare init as src1 else init as 0
            _ := Mux(enqueue.bits.decodeResult(Decoder.fpExecutionType).orR, enqueue.bits.src(1), 0.U)
          }
        }
      }
    }

    // reduce state machine
    // widenReduce    false     true
    // sew = 8        1         0
    // sew = 16       0         -
    // sew = 32       -         -
    val nextFoldCount = recordQueue.deq.bits.vSew1H(0) && !recordQueue.deq.bits.decodeResult(Decoder.widenReduce)
    val needFold      = recordQueue.deq.bits
      .vSew1H(0) || (recordQueue.deq.bits.vSew1H(1) && !recordQueue.deq.bits.decodeResult(Decoder.widenReduce))
    val reduceFoldCount: Bool = RegInit(false.B)
    val idle :: wLastResponse :: fold :: waitFoldResponse :: Nil = Enum(4)
    val redState                                                 = RegInit(idle)
    val stateFold                                                = redState === fold
    when(
      // vfu  request fire
      vfuRequest.fire &&
        // is last group for this instruction
        !recordQueue.deq.bits.sSendResponse.get
        // reduce type
        && recordQueue.deq.bits.decodeResult(Decoder.red) &&
        // last execute for this group(widen need 2 execute/group)
        (!doubleExecutionInQueue || recordQueue.deq.bits.executeIndex) &&
        (redState === idle)
    ) {
      when(responseFinish && dataResponse.valid) {
        when(needFold) {
          redState        := fold
          reduceFoldCount := nextFoldCount
        }.otherwise {
          redState := idle
        }
      }.otherwise {
        redState := wLastResponse
      }
    }
    when(redState === wLastResponse && dataResponse.valid) {
      when(needFold) {
        redState        := fold
        reduceFoldCount := nextFoldCount
      }.otherwise {
        redState := idle
      }
    }
    when(stateFold && reduceFoldCount === 0.U && vfuRequest.ready) {
      // latency = 0
      when(responseFinish && dataResponse.valid) {
        redState           := idle
        reduceLastResponse := true.B
      }.otherwise {
        redState := waitFoldResponse
      }
    }
    when(redState === waitFoldResponse && dataResponse.valid) {
      redState           := idle
      reduceLastResponse := true.B
    }
    when(stateFold && dataResponse.valid) {
      reduceFoldCount := false.B
    }
    sendFoldReduce.get := stateFold || redState === waitFoldResponse
    reduceReady := (stateFold && reduceFoldCount === 0.U) || redState === idle ||
      (redState === wLastResponse && !needFold)
    val reduceDataVec = cutUInt(reduceResult.get, 8)
    // reduceFoldCount = false => abcd -> xxab | xxcd -> mask 0011
    // reduceFoldCount = true =>  abcd -> xaxc | xbxd -> mask 0101
    reduceFoldSource2.get := Mux(
      reduceFoldCount,
      reduceDataVec(3) ## reduceDataVec(3) ## reduceDataVec(1),
      reduceDataVec(3) ## reduceDataVec(3) ## reduceDataVec(2)
    )

    val foldMask = Mux(reduceFoldCount, 5.U, 3.U)
    foldUpdateMask := Mux(stateFold, foldMask, 15.U)
  }

  // queue before dequeue
  val queue: QueueIO[LaneExecuteResponse] =
    Queue.io(new LaneExecuteResponse(parameter, isLastSlot), 4)
  if (isLastSlot) {
    queue.enq.bits.data := Mux(
      recordQueue.deq.bits.decodeResult(Decoder.maskDestination),
      maskFormatResultUpdate.get,
      Mux(
        recordQueue.deq.bits.decodeResult(Decoder.red),
        updateReduceResult.get,
        lastSlotDataUpdate
      )
    )
  } else {
    queue.enq.bits.data := dataDequeue
  }
  queue.enq.bits.ffoIndex := recordQueue.deq.bits.groupCounter ## dataResponse.bits.data(4, 0)
  queue.enq.bits.crossWriteData.foreach(_ := VecInit((crossWriteLSB ++ Seq(dataDequeue)).toSeq))
  queue.enq.bits.ffoSuccess.foreach(_ := dataResponse.bits.ffoSuccess)
  queue.enq.bits.fpReduceValid.foreach(_ := !waitFirstValidFire.get)
  recordQueue.deq.ready := dataResponse.valid || (recordNotExecute && queue.enq.ready)
  responseDecode  := recordQueue.deq.bits.decodeResult
  responseIndex   := recordQueue.deq.bits.instructionIndex
  queue.enq.valid :=
    (recordQueue.deq.valid &&
      ((dataResponse.valid && reduceReady &&
        (!doubleExecutionInQueue || recordQueue.deq.bits.executeIndex)) ||
        recordNotExecute)) || reduceLastResponse
  AssertProperty(BoolSequence(!queue.enq.valid || queue.enq.ready))
  dequeue <> queue.deq
  updateMaskResult.foreach(
    _ :=
      (!recordQueue.deq.bits.sSendResponse.get && queue.enq.fire) ||
        (enqueue.fire && enqueue.bits.groupCounter === 0.U)
  )
  val executionTypeInRecord: UInt = getExecuteUnitTag(parameter)(executionRecord.decodeResult)
  val enqType:   UInt = getExecuteUnitTag(parameter)(enqueue.bits.decodeResult)
  val typeCheck: Bool = (executionTypeInRecord === enqType) || !(executionRecordValid || recordQueue.deq.valid)
  enqueue.ready := (!executionRecordValid || recordDequeueReady) && typeCheck
}
