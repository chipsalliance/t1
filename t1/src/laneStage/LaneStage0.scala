// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle
import org.chipsalliance.t1.rtl._
import org.chipsalliance.t1.rtl.decoder.Decoder

// stage 0
class LaneStage0Enqueue(parameter: LaneParameter) extends Bundle {
  val maskIndex:        UInt = UInt(log2Ceil(parameter.maskGroupWidth).W)
  val maskForMaskGroup: UInt = UInt(parameter.datapathWidth.W)
  val maskGroupCount:   UInt = UInt(parameter.maskGroupSizeBits.W)
  val readFromScalar:   UInt = UInt(parameter.datapathWidth.W)

  // pipe all state
  val vSew1H:       UInt         = UInt(3.W)
  val vSew:         UInt         = UInt(2.W)
  val loadStore:    Bool         = Bool()
  val laneIndex:    UInt         = UInt(parameter.laneNumberBits.W)
  val decodeResult: DecodeBundle = Decoder.bundle(parameter.decoderParam)

  /** which group is the last group for instruction. */
  val lastGroupForInstruction:  UInt         = UInt(parameter.groupNumberBits.W)
  val isLastLaneForInstruction: Bool         = Bool()
  val instructionFinished:      Bool         = Bool()
  val csr:                      CSRInterface = new CSRInterface(parameter.vlMaxBits)
  // vm = 0
  val maskType:                 Bool         = Bool()
  val maskNotMaskedElement:     Bool         = Bool()

  /** vs1 or imm */
  val vs1: UInt = UInt(5.W)

  /** vs2 or rs2 */
  val vs2: UInt = UInt(5.W)

  /** vd or rd */
  val vd: UInt = UInt(5.W)

  val instructionIndex: UInt = UInt(parameter.instructionIndexBits.W)
  val additionalRW:     Bool = Bool()
  // skip vrf read in stage 1?
  val skipRead:         Bool = Bool()
  // vm will skip element?
  val skipEnable:       Bool = Bool()
  val maskE0:           Bool = Bool()
}

class LaneStage0StateUpdate(parameter: LaneParameter) extends Bundle {
  val maskGroupCount:      UInt = UInt(parameter.maskGroupSizeBits.W)
  val maskIndex:           UInt = UInt(log2Ceil(parameter.maskGroupWidth).W)
  val outOfExecutionRange: Bool = Bool()
  val maskExhausted:       Bool = Bool()
}

class LaneStage0Dequeue(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  val maskForMaskInput:       UInt         = UInt((parameter.datapathWidth / 8).W)
  val boundaryMaskCorrection: UInt         = UInt((parameter.datapathWidth / 8).W)
  val sSendResponse:          Option[Bool] = Option.when(isLastSlot)(Bool())
  val groupCounter:           UInt         = UInt(parameter.groupNumberBits.W)
  val readFromScalar:         UInt         = UInt(parameter.datapathWidth.W)

  // pipe state
  val instructionIndex:     UInt         = UInt(parameter.instructionIndexBits.W)
  val decodeResult:         DecodeBundle = Decoder.bundle(parameter.decoderParam)
  val laneIndex:            UInt         = UInt(parameter.laneNumberBits.W)
  // skip vrf read in stage 1?
  val skipRead:             Bool         = Bool()
  val vs1:                  UInt         = UInt(5.W)
  val vs2:                  UInt         = UInt(5.W)
  val vd:                   UInt         = UInt(5.W)
  val vSew1H:               UInt         = UInt(3.W)
  val maskNotMaskedElement: Bool         = Bool()

  // pipe state
  val csr:                 CSRInterface = new CSRInterface(parameter.vlMaxBits)
  val maskType:            Bool         = Bool()
  val loadStore:           Bool         = Bool()
  val bordersForMaskLogic: Bool         = Bool()

  // pipe for mask stage
  val secondPipe:        Option[Bool]              = Option.when(isLastSlot)(Bool())
  val emptyPipe:         Option[Bool]              = Option.when(isLastSlot)(Bool())
  val pipeForSecondPipe: Option[PipeForSecondPipe] = Option.when(isLastSlot)(
    new PipeForSecondPipe(
      parameter.datapathWidth,
      parameter.groupNumberBits,
      parameter.laneNumberBits,
      parameter.eLen
    )
  )
  val maskE0:            Bool                      = Bool()
}

/** 这一级由 lane slot 里的 maskIndex maskGroupCount 来计算对应的 data group counter 同时也会维护指令的结束与mask的更新
  */
@instantiable
class LaneStage0(parameter: LaneParameter, isLastSlot: Boolean)
    extends LaneStage(true)(
      new LaneStage0Enqueue(parameter),
      new LaneStage0Dequeue(parameter, isLastSlot)
    ) {
  @public
  val updateLaneState: LaneStage0StateUpdate    = IO(Output(new LaneStage0StateUpdate(parameter)))
  @public
  val tokenReport:     ValidIO[EnqReportBundle] = IO(Valid(new EnqReportBundle(parameter)))

  @public
  val freeCrossReqEnq: Option[DecoupledIO[FreeWriteBusRequest]] = Option.when(isLastSlot) {
    IO(
      Flipped(
        Decoupled(
          new FreeWriteBusRequest(
            parameter.datapathWidth,
            parameter.groupNumberBits,
            parameter.laneNumberBits
          )
        )
      )
    )
  }

  @public
  val maskPipeRelease: Option[MaskExchangeRelease] = Option.when(isLastSlot)(IO(Input(new MaskExchangeRelease)))

  @public
  val lsuLastReport: Option[UInt] = Option.when(isLastSlot)(IO(Input(UInt(parameter.chaining1HBits.W))))

  val sourceSew1HSelect = Mux(enqueue.bits.decodeResult(Decoder.gather16), 2.U(3.W), enqueue.bits.vSew1H(2, 0))

  val slideBase:           UInt              = Option
    .when(isLastSlot) {
      val base: UInt = Wire(UInt(parameter.groupNumberBits.W))

      val isSlide = enqueue.bits.decodeResult(Decoder.maskPipeUop) === BitPat("b001??")
      // 001 xy => slide  x?up:down   y?s:1           [4,7]
      val slideDown:      Bool = !enqueue.bits.decodeResult(Decoder.maskPipeUop)(1)
      val source1IsScala: Bool = enqueue.bits.decodeResult(Decoder.maskPipeUop)(0)
      val slideSize:      UInt = Mux(
        source1IsScala,
        Mux(
          enqueue.bits.decodeResult(Decoder.itype),
          enqueue.bits.readFromScalar(4, 0),
          enqueue.bits.readFromScalar
        ),
        1.U
      )
      val lagerThanVL:    Bool = (slideSize >> parameter.vlMaxBits).asUInt.orR
      val baseGroup = ((slideSize << enqueue.bits.vSew) >> log2Ceil(parameter.dByte)).asUInt
      // You need to skip reading only when you slide down
      base := Mux(isSlide && slideDown && !lagerThanVL, baseGroup, 0.U)
      base
    }
    .getOrElse(0.U)
  val stageWire:           LaneStage0Dequeue = Wire(new LaneStage0Dequeue(parameter, isLastSlot))
  // 这一组如果全被masked了也不压进流水
  val notMaskedAllElement: Bool              = Mux1H(
    sourceSew1HSelect,
    Seq(
      stageWire.maskForMaskInput.orR,
      cutUIntBySize(stageWire.maskForMaskInput, 2).head.orR,
      cutUIntBySize(stageWire.maskForMaskInput, 4).head.orR
    )
  ) || enqueue.bits.maskNotMaskedElement ||
    enqueue.bits.decodeResult(Decoder.maskDestination) || enqueue.bits.decodeResult(Decoder.red) ||
    enqueue.bits.decodeResult(Decoder.readOnly) || enqueue.bits.loadStore ||
    enqueue.bits.decodeResult(Decoder.crossRead) || enqueue.bits.decodeResult(Decoder.crossWrite)
  val slideExecuteGroup = Wire(UInt(parameter.groupNumberBits.W))
  val firstSlideDownGroup: Bool = (enqueue.bits.decodeResult(Decoder.maskPipeUop) === BitPat("b0010?")) &&
    (slideExecuteGroup === 0.U)
  val normalDeqValid =
    (!updateLaneState.outOfExecutionRange || enqueue.bits.additionalRW || firstSlideDownGroup) && notMaskedAllElement
  val emptyValid:   Bool              = Wire(Bool())
  // 超出范围的一组不压到流水里面去
  val enqFire:      Bool              = enqueue.fire && (normalDeqValid || emptyValid)
  val stageDataReg: LaneStage0Dequeue = RegEnable(stageWire, 0.U.asTypeOf(stageWire), enqFire)
  val filterVec:    Seq[(Bool, UInt)] = Seq(0, 1, 2).map { filterSew =>
    // The lower 'dataGroupIndexSize' bits represent the offsets in the data group
    val dataGroupIndexSize: Int = log2Ceil(parameter.datapathWidth / 8) - filterSew
    // each group has '2 ** dataGroupIndexSize' elements
    val dataGroupSize = 1 << dataGroupIndexSize
    // The data group index of last data group
    val groupIndex    = (enqueue.bits.maskIndex >> dataGroupIndexSize).asUInt
    // Filtering data groups
    val groupFilter: UInt = scanLeftOr(UIntToOH(groupIndex)) ## false.B
    // Whether there are element in the data group that have not been masked
    val maskCorrection = Mux(
      enqueue.bits.skipEnable,
      enqueue.bits.maskForMaskGroup,
      (-1.S(parameter.datapathWidth.W)).asUInt
    )
    val maskForDataGroup: UInt =
      VecInit(maskCorrection.asBools.grouped(dataGroupSize).map(_.reduce(_ || _)).toSeq).asUInt
    val groupFilterByMask = maskForDataGroup & groupFilter
    // ffo next group
    val nextDataGroupOH: UInt = ffo(groupFilterByMask)
    // This mask group has the next data group to execute
    val hasNextDataGroup = nextDataGroupOH.orR
    val nextElementBaseIndex: UInt = (OHToUInt(nextDataGroupOH) << dataGroupIndexSize).asUInt
    (hasNextDataGroup, nextElementBaseIndex)
  }

  /** is there any data left in this group? */
  val nextOrR: Bool = Mux1H(sourceSew1HSelect, filterVec.map(_._1))

  // mask is exhausted
  updateLaneState.maskExhausted := !nextOrR

  /** The mask group will be updated */
  val maskGroupWillUpdate: Bool = enqueue.bits.decodeResult(Decoder.maskLogic) || updateLaneState.maskExhausted

  /** Encoding of different element lengths: 1, 8, 16, 32 */
  val elementLengthOH = Mux(enqueue.bits.decodeResult(Decoder.maskLogic), 1.U, sourceSew1HSelect(2, 0) ## false.B)

  /** Which group of data will be accessed */
  val dataGroupIndex: UInt = Mux1H(
    elementLengthOH,
    Seq(
      enqueue.bits.maskGroupCount,
      enqueue.bits.maskGroupCount ## enqueue.bits
        .maskIndex(log2Ceil(parameter.maskGroupWidth) - 1, log2Ceil(parameter.datapathWidth / 8)),
      enqueue.bits.maskGroupCount ## enqueue.bits
        .maskIndex(log2Ceil(parameter.maskGroupWidth) - 1, log2Ceil(parameter.datapathWidth / 8) - 1),
      enqueue.bits.maskGroupCount ## enqueue.bits
        .maskIndex(log2Ceil(parameter.maskGroupWidth) - 1, log2Ceil(parameter.datapathWidth / 8) - 2)
    )
  )

  /** The next element is out of execution range */
  updateLaneState.outOfExecutionRange := dataGroupIndex > enqueue.bits.lastGroupForInstruction || enqueue.bits.instructionFinished

  val isTheLastGroup: Bool = dataGroupIndex === enqueue.bits.lastGroupForInstruction

  // Correct the mask on the boundary line
  val vlNeedCorrect:     Bool = Mux1H(
    sourceSew1HSelect,
    Seq(
      enqueue.bits.csr.vl(parameter.dataPathByteBits - 1, 0).orR,
      enqueue.bits.csr.vl(parameter.dataPathByteBits - 2, 0).orR,
      if (parameter.dataPathByteBits - 3 >= 0) enqueue.bits.csr.vl(parameter.dataPathByteBits - 3, 0).orR else false.B
    )
  )
  val correctMask:       UInt = Mux1H(
    sourceSew1HSelect,
    Seq(
      (scanRightOr(UIntToOH(enqueue.bits.csr.vl(parameter.dataPathByteBits - 1, 0))) >> 1).asUInt,
      (scanRightOr(UIntToOH(enqueue.bits.csr.vl(parameter.dataPathByteBits - 2, 0))) >> 1).asUInt,
      if (parameter.dataPathByteBits - 3 >= 0)
        (scanRightOr(UIntToOH(enqueue.bits.csr.vl(parameter.dataPathByteBits - 3, 0))) >> 1).asUInt
      else
        0.U(parameter.dataPathByteWidth.W)
    )
  )
  val needCorrect:       Bool =
    isTheLastGroup &&
      enqueue.bits.isLastLaneForInstruction &&
      vlNeedCorrect && !enqueue.bits.decodeResult(Decoder.maskLogic) && !enqueue.bits.decodeResult(Decoder.slid)
  val maskCorrect:       UInt = Mux(needCorrect, correctMask, -1.S(parameter.dataPathByteWidth.W).asUInt)
  val crossReadOnlyMask: UInt =
    Fill(parameter.dataPathByteWidth, !updateLaneState.outOfExecutionRange || firstSlideDownGroup)

  stageWire.maskForMaskInput       :=
    Mux(
      enqueue.bits.maskType,
      (enqueue.bits.maskForMaskGroup >> enqueue.bits.maskIndex).asUInt(parameter.dataPathByteWidth - 1, 0),
      0.U(parameter.dataPathByteWidth.W)
    )
  stageWire.boundaryMaskCorrection := maskCorrect & crossReadOnlyMask

  /** The index of next element in this mask group.(0-31) */
  updateLaneState.maskIndex := Mux(
    enqueue.bits.decodeResult(Decoder.maskLogic),
    0.U,
    Mux1H(sourceSew1HSelect, filterVec.map(_._2))
  )

  stageWire.groupCounter := dataGroupIndex + slideBase
  slideExecuteGroup      := dataGroupIndex

  /** next mask group */
  updateLaneState.maskGroupCount := enqueue.bits.maskGroupCount + maskGroupWillUpdate

  stageWire.sSendResponse.foreach { data =>
    data := !(Seq(
      enqueue.bits.loadStore,
      enqueue.bits.decodeResult(Decoder.readOnly),
      enqueue.bits.decodeResult(Decoder.red) && isTheLastGroup,
      enqueue.bits.decodeResult(Decoder.maskDestination) && (maskGroupWillUpdate || isTheLastGroup),
      enqueue.bits.decodeResult(Decoder.ffo)
    ) ++ Option.when(parameter.fpuEnable)(enqueue.bits.decodeResult(Decoder.orderReduce))).reduce(_ || _)
  }
  // pipe all state
  stageWire.elements.foreach { case (k, d) =>
    enqueue.bits.elements
      .get(k)
      .foreach(pipeData =>
        d match {
          case data: Data => data := pipeData
          case _ =>
        }
      )
  }

  val source1Fill: UInt = Fill(
    parameter.datapathWidth / parameter.eLen,
    Mux1H(
      enqueue.bits.vSew1H,
      Seq(
        Fill(4, enqueue.bits.readFromScalar(7, 0)),
        Fill(2, enqueue.bits.readFromScalar(15, 0)),
        enqueue.bits.readFromScalar(31, 0)
      )
    )
  )
  stageWire.readFromScalar := Mux(enqueue.bits.decodeResult(Decoder.slid), enqueue.bits.readFromScalar, source1Fill)

  stageWire.bordersForMaskLogic :=
    dataGroupIndex === enqueue.bits.lastGroupForInstruction &&
      enqueue.bits.isLastLaneForInstruction
  // for mask pipe stage
  stageWire.secondPipe.foreach(_ := false.B)
  stageWire.emptyPipe.foreach(_ := emptyValid)
  // todo: why only extend
  emptyValid                    := !normalDeqValid && stageWire.groupCounter === 0.U &&
    enqueue.bits.decodeResult(Decoder.maskPipeType) &&
    enqueue.bits.decodeResult(Decoder.maskPipeUop) === BitPat("b000??") &&
    enqueue.bits.csr.vl.orR
  stageWire.pipeForSecondPipe.foreach(_ := DontCare)

  when(enqFire ^ (dequeue.fire && !bypassDeqValid)) {
    stageValidReg := enqFire
  }

  val deqWire: LaneStage0Dequeue = WireDefault(stageDataReg)
  dequeue.bits := deqWire

  tokenReport.valid                 := enqFire && normalDeqValid
  tokenReport.bits.decodeResult     := enqueue.bits.decodeResult
  tokenReport.bits.instructionIndex := enqueue.bits.instructionIndex
  tokenReport.bits.sSendResponse    := stageWire.sSendResponse.getOrElse(true.B)
  tokenReport.bits.maskPipe         := enqueue.bits.decodeResult(Decoder.maskPipeType)
  tokenReport.bits.mask             := stageWire.maskForMaskInput
  tokenReport.bits.groupCounter     := stageWire.groupCounter

  if (isLastSlot) {
    // add toke
    val pipeDeqFire          = dequeue.fire && !bypassDeqValid && dequeue.bits.decodeResult(Decoder.maskPipeType)
    val pipeDeqRelease       = maskPipeRelease.get.maskPipe
    val pipeDeqTokenAllocate = pipeToken(parameter.maskRequestQueueSize)(pipeDeqFire, pipeDeqRelease)

    val bypassDeqFire       = dequeue.fire && bypassDeqValid
    val bypassDeqRelease    = maskPipeRelease.get.secondPipe
    val bypassTokenAllocate = pipeToken(parameter.secondQueueSize)(bypassDeqFire, bypassDeqRelease)

    bypassDeqValid            := bypassTokenAllocate && freeCrossReqEnq.get.valid
    freeCrossReqEnq.get.ready := bypassTokenAllocate && dequeue.ready
    when(bypassDeqValid) {
      deqWire.secondPipe.get                     := true.B
      deqWire.pipeForSecondPipe.get.readOffset   := freeCrossReqEnq.get.bits.readOffset
      deqWire.pipeForSecondPipe.get.writeSink    := freeCrossReqEnq.get.bits.writeSink
      deqWire.pipeForSecondPipe.get.writeCounter := freeCrossReqEnq.get.bits.writeCounter
      deqWire.pipeForSecondPipe.get.writeOffset  := freeCrossReqEnq.get.bits.writeOffset
      deqWire.groupCounter                       := freeCrossReqEnq.get.bits.readCounter
      // second pipe for gather need read vs2.
      deqWire.decodeResult(Decoder.vtype)        := true.B
    }

    val stateGather         = RegInit(false.B)
    val gatherIndex         = RegInit(0.U(parameter.instructionIndexBits.W))
    val lastReportHitGather = ohCheck(
      lsuLastReport.get,
      gatherIndex,
      parameter.chainingSize
    )
    val gatherEnq           = enqFire && enqueue.bits.decodeResult(Decoder.maskPipeUop) === BitPat("b0001?")
    when(gatherEnq || lastReportHitGather) {
      stateGather := gatherEnq
      gatherIndex := enqueue.bits.instructionIndex
    }
    stageEnqAllocate := enqueue.bits.instructionIndex === gatherIndex || !stateGather
    stageDeqAllocate := (!bypassDeqValid && pipeDeqTokenAllocate)
  }
}
