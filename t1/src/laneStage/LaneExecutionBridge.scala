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
  changeUIntSize,
  cutUInt,
  cutUIntBySize,
  getExecuteUnitTag,
  maskAnd,
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
  val ffoIndex:       UInt              = UInt(
    (parameter.laneScale * log2Ceil(parameter.vLen / parameter.laneNumber / parameter.laneScale)).W
  )
  val crossWriteData: Option[Vec[UInt]] = Option.when(isLastSlot)(Vec(2, UInt(parameter.datapathWidth.W)))
  val ffoSuccess:     Option[UInt]      = Option.when(isLastSlot)(UInt((parameter.datapathWidth / parameter.eLen).W))
  val fpReduceValid:  Option[UInt]      =
    Option.when(parameter.fpuEnable && isLastSlot)(UInt((parameter.datapathWidth / parameter.eLen).W))
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
  val executeDecode:  DecodeBundle = IO(Output(Decoder.bundle(parameter.decoderParam)))
  @public
  val responseDecode: DecodeBundle = IO(Output(Decoder.bundle(parameter.decoderParam)))
  @public
  val responseIndex:  UInt         = IO(Output(UInt(parameter.instructionIndexBits.W)))

  val executionRecord: ExecutionUnitRecord = RegInit(0.U.asTypeOf(new ExecutionUnitRecord(parameter)(isLastSlot)))
  val executionRecordValid = RegInit(false.B)

  /** result of reduce instruction. */
  val reduceResult: Option[Vec[UInt]] = Option.when(isLastSlot)(
    RegInit(VecInit(Seq.tabulate(parameter.datapathWidth / parameter.eLen) { _ => 0.U(parameter.eLen.W) }))
  )
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

  val waitFirstValidFire: Option[Vec[Bool]] = Option.when(parameter.fpuEnable && isLastSlot) {
    VecInit(Seq.tabulate(parameter.datapathWidth / parameter.eLen) { i =>
      RegEnable(
        (enqueue.bits.groupCounter === 0.U) && !enqueue.bits.maskForFilter(i),
        false.B,
        enqueue.fire && ((enqueue.bits.groupCounter === 0.U) || enqueue.bits.maskForFilter(i))
      )
    })
  }

  val firstRequestFire: Option[Vec[Bool]] = Option.when(parameter.fpuEnable && isLastSlot) {
    VecInit(Seq.tabulate(parameter.datapathWidth / parameter.eLen) { i =>
      enqueue.fire && (enqueue.bits.groupCounter === 0.U || waitFirstValidFire.get(i)) && enqueue.bits.maskForFilter(i)
    })
  }

  // Whether cross-lane reading or cross-lane writing requires double execution
  // data request in executionRecord need double execution
  val doubleExecutionInRecord: Bool =
    executionRecord.decodeResult(Decoder.crossWrite) ||
      executionRecord.decodeResult(Decoder.crossRead) ||
      executionRecord.decodeResult(Decoder.widenReduce)

  // data in executionRecord is narrow type
  val narrowInRecord: Bool = !executionRecord.decodeResult(Decoder.crossWrite) &&
    executionRecord.decodeResult(Decoder.crossRead)
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
      cutUIntBySize(enqueue.bits.maskForFilter, 2).head.orR,
      cutUIntBySize(enqueue.bits.maskForFilter, 4).head.orR
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
    val sewIsZero    = executionRecord.vSew1H(0)
    val sourceExtend = Mux(
      sewIsZero,
      VecInit(cutUInt(data, 8).map(d => Fill(8, d(7) && sign) ## d)).asUInt,
      VecInit(cutUInt(data, 16).map(d => Fill(16, d(15) && sign) ## d)).asUInt
    )

    Mux(
      executionRecord.executeIndex,
      cutUIntBySize(sourceExtend, 2)(1),
      cutUIntBySize(sourceExtend, 2)(0)
    )
  }
  val extendSource1:                      UInt = dataExtend(executionRecord.source.head, !executionRecord.decodeResult(Decoder.unsigned0))
  val extendSource2:                      UInt = dataExtend(executionRecord.source(1), !executionRecord.decodeResult(Decoder.unsigned1))

  /** src1 for the execution src1 has three types: V, I, X.
    */
  val normalSource1: UInt =
    Mux(
      executionRecord.decodeResult(Decoder.red) && !executionRecord.decodeResult(Decoder.maskLogic),
      reduceResult.map(_.asUInt).getOrElse(0.U),
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

  /** src2 for the execution, need to take care of cross read.
    */
  val finalSource2: UInt = if (isLastSlot) {
    Mux(
      executionRecord.crossReadVS2,
      doubleCollapse.get,
      Mux(
        executionRecord.decodeResult(Decoder.crossWrite) || executionRecord.decodeResult(Decoder.widenReduce),
        extendSource2,
        executionRecord.source(1)
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

  val maskForAdder = Mux1H(
    executionRecord.vSew1H,
    Seq(
      executionRecord.maskForMaskInput,
      //                                           1/2 mask for sew=1 |
      //                                              2 bit for sew=1 per elen |
      //                                                           extend 2 bit to 4 (elen max) |
      VecInit(cutUInt(cutUIntBySize(executionRecord.maskForMaskInput, 2).head, 2).map(m => Fill(2, m))).asUInt,
      VecInit(cutUInt(cutUIntBySize(executionRecord.maskForMaskInput, 4).head, 1).map(m => Fill(4, m))).asUInt
    )
  )
  vfuRequest.bits.src         := VecInit(Seq(finalSource1, finalSource2, finalSource3, maskCorrect))
  vfuRequest.bits.opcode      := executionRecord.decodeResult(Decoder.uop)
  vfuRequest.bits.mask        := Mux(
    executionRecord.decodeResult(Decoder.adder),
    Mux(
      executionRecord.decodeResult(Decoder.maskSource),
      maskForAdder,
      0.U((parameter.datapathWidth / 8).W)
    ),
    executionRecord.maskForMaskInput | Fill(parameter.datapathWidth / 8, !executionRecord.maskType)
  )
  vfuRequest.bits.executeMask := Mux(
    executionRecord.executeIndex,
    Mux(
      executionRecord.vSew1H(0),
      cutUIntBySize(executionRecord.maskForFilter, 2)(1),
      cutUIntBySize(executionRecord.maskForFilter, 4)(1)
    ),
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
  vfuRequest.bits.shifterSize := Mux1H(
    (executionRecord.vSew1H << doubleExecutionInRecord).asUInt(2, 0),
    Seq(8, 16, 32).map { s =>
      VecInit(
        cutUInt(finalSource1, s).map(data =>
          Mux1H(
            shifterSizeBit,
            Seq(false.B ## data(3), data(4, 3))
          ) ## data(2, 0)
        )
      ).asUInt
    }
  )

  vfuRequest.bits.rem          := executionRecord.decodeResult(Decoder.uop)(0)
  vfuRequest.bits.executeIndex := executionRecord.executeIndex
  vfuRequest.bits.popInit      := reduceResult.map { rv =>
    VecInit(rv.map(_(parameter.vlMaxBits - 1, 0))).asUInt
  }.getOrElse(0.U)
  vfuRequest.bits.groupIndex   := executionRecord.groupCounter
  vfuRequest.bits.laneIndex    := executionRecord.laneIndex
  vfuRequest.bits.complete     := false.B
  vfuRequest.bits.maskType     := executionRecord.maskType
  vfuRequest.bits.narrow       := narrowInRecord
  vfuRequest.bits.unitSelet.foreach(_ := executionRecord.decodeResult(Decoder.fpExecutionType))
  vfuRequest.bits.floatMul.foreach(_ := executionRecord.decodeResult(Decoder.floatMul))
  vfuRequest.bits.tag          := slotIndex.U

  // from float csr
  vfuRequest.bits.roundingMode.foreach(_ := executionRecord.csr.frm)
  executeDecode := executionRecord.decodeResult

  vfuRequest.valid := (if (isLastSlot) {
                         executionRecordValid && (responseFinish || !executionRecord.decodeResult(Decoder.red))
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

  val newNarrowDataDeq: UInt = Mux(
    recordQueue.deq.bits.vSew1H(0),
    // 16 -> 8
    VecInit(cutUInt(dataDequeue, 16).map(_(7, 0))).asUInt,
    // 32 -> 16
    VecInit(cutUInt(dataDequeue, 32).map(_(15, 0))).asUInt
  )
  // vsew = 8     -> d2 ## d0
  // vasew = 16   -> d1 ## d0
  val narrowUpdate = (newNarrowDataDeq ## executionResult) >> (parameter.datapathWidth / 2)
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
    val maskResult: UInt = Mux1H(
      recordQueue.deq.bits.vSew1H(2, 0),
      Seq(
        dataResponse.bits.adderMaskResp,
        VecInit(cutUInt(dataResponse.bits.adderMaskResp, parameter.eLen / 8).map(d => cutUIntBySize(d, 2).head)).asUInt,
        VecInit(cutUInt(dataResponse.bits.adderMaskResp, parameter.eLen / 8).map(d => cutUIntBySize(d, 4).head)).asUInt
      )
    )

    val maxMaskResultSize = parameter.datapathWidth / 8
    val maxMaskResultBits = log2Ceil(maxMaskResultSize)

    /** update value for [[maskFormatResultUpdate]], it comes from ALU.
      */
    val elementMaskFormatResult = Mux1H(
      recordQueue.deq.bits.vSew1H(2, 0),
      Seq(
        // 32bit, 4 bit per data group, it will had 8 data groups -> executeIndex1H << 4 * groupCounter(2, 0)
        maskResult << (recordQueue.deq.bits.groupCounter(parameter.datapathWidthBits - maxMaskResultBits - 1, 0) ## 0.U(
          maxMaskResultBits.W
        )),
        // 2 bit per data group, it will had 16 data groups -> executeIndex1H << 2 * groupCounter(3, 0)
        maskResult <<
          (recordQueue.deq.bits.groupCounter(parameter.datapathWidthBits - maxMaskResultBits, 0) ## 0.U(
            (maxMaskResultBits - 1).W
          )),
        // 1 bit per data group, it will had 32 data groups -> executeIndex1H << 1 * groupCounter(4, 0)
        maskResult << (recordQueue.deq.bits.groupCounter(
          (parameter.datapathWidthBits - maxMaskResultBits + 1).min(parameter.groupNumberBits - 1),
          0
        ) ## 0.U((maxMaskResultBits - 2).W))
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
        FillInterleaved(2, cutUIntBySize(recordQueue.deq.bits.maskForFilter, 2).head),
        FillInterleaved(4, cutUIntBySize(recordQueue.deq.bits.maskForFilter, 4).head)
      )
    )
    val widenReduceMask  = Mux1H(
      recordQueue.deq.bits.vSew1H(1, 0),
      Seq(
        FillInterleaved(
          2,
          Mux(
            dataResponse.bits.executeIndex(0),
            cutUIntBySize(recordQueue.deq.bits.maskForFilter, 2).last,
            cutUIntBySize(recordQueue.deq.bits.maskForFilter, 2).head
          )
        ),
        FillInterleaved(
          4,
          Mux(
            dataResponse.bits.executeIndex(0),
            cutUIntBySize(recordQueue.deq.bits.maskForFilter, 4)(1),
            cutUIntBySize(recordQueue.deq.bits.maskForFilter, 4).head
          )
        )
      )
    )
    // masked element don't update 'reduceResult'
    val reduceUpdateByteMask: UInt =
      Mux(recordQueue.deq.bits.decodeResult(Decoder.widenReduce), widenReduceMask, normalReduceMask)
    updateReduceResult.get := {
      val dataVec   = cutUInt(dataDequeue, 8)
      val ResultVec = cutUInt(reduceResult.get.asUInt, 8)
      VecInit(dataVec.zipWithIndex.map { case (d, i) => Mux(reduceUpdateByteMask(i), d, ResultVec(i)) }).asUInt
    }
    // update `reduceResult`
    when((dataResponse.valid && recordQueue.deq.bits.decodeResult(Decoder.red)) || updateMaskResult.get) {
      reduceResult.get := cutUIntBySize(
        Mux(updateMaskResult.get, 0.U, updateReduceResult.get),
        parameter.datapathWidth / parameter.eLen
      )
    }
    firstRequestFire.foreach { fr =>
      fr.zipWithIndex.foreach { case (f, i) =>
        when(enqueue.fire && f && enqueue.bits.decodeResult(Decoder.float)) {
          reduceResult.foreach { red =>
            red(i) := cutUInt(
              Mux(enqueue.bits.decodeResult(Decoder.fpExecutionType).orR, enqueue.bits.src(1), 0.U),
              parameter.eLen
            )(i)
          }
        }
      }
    }

    // reduce state machine
    val idle :: wLastResponse :: Nil = Enum(2)
    val redState                     = RegInit(idle)
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
        redState           := idle
        reduceLastResponse := true.B
      }.otherwise {
        redState := wLastResponse
      }
    }
    when(redState === wLastResponse && dataResponse.valid) {
      redState           := idle
      reduceLastResponse := true.B
    }
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
  queue.enq.bits.ffoIndex := VecInit(Seq.tabulate(parameter.laneScale) { si =>
    val indexData = recordQueue.deq.bits.groupCounter ##
      dataResponse.bits.data(parameter.eLen * si + 4, parameter.eLen * si + 0)
    changeUIntSize(indexData, queue.enq.bits.ffoIndex.getWidth / parameter.laneScale)
  }).asUInt

  queue.enq.bits.crossWriteData.foreach(_ := VecInit((crossWriteLSB ++ Seq(dataDequeue)).toSeq))
  queue.enq.bits.ffoSuccess.foreach(_ := dataResponse.bits.ffoSuccess)
  queue.enq.bits.fpReduceValid.foreach(_ := (~waitFirstValidFire.get.asUInt).asUInt)
  recordQueue.deq.ready := dataResponse.valid || (recordNotExecute && queue.enq.ready)
  responseDecode        := recordQueue.deq.bits.decodeResult
  responseIndex         := recordQueue.deq.bits.instructionIndex
  queue.enq.valid       :=
    (recordQueue.deq.valid &&
      ((dataResponse.valid &&
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
