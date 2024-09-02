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
  val ffoByOtherLanes:          Bool         = Bool()

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
  val updateLaneState:     LaneStage0StateUpdate    = IO(Output(new LaneStage0StateUpdate(parameter)))
  @public
  val tokenReport:         ValidIO[EnqReportBundle] = IO(Valid(new EnqReportBundle(parameter)))
  val stageWire:           LaneStage0Dequeue        = Wire(new LaneStage0Dequeue(parameter, isLastSlot))
  // 这一组如果全被masked了也不压进流水
  val notMaskedAllElement: Bool                     = Mux1H(
    enqueue.bits.vSew1H,
    Seq(
      stageWire.maskForMaskInput.orR,
      stageWire.maskForMaskInput(1, 0).orR,
      stageWire.maskForMaskInput(0)
    )
  ) || enqueue.bits.maskNotMaskedElement ||
    enqueue.bits.decodeResult(Decoder.maskDestination) || enqueue.bits.decodeResult(Decoder.red) ||
    enqueue.bits.decodeResult(Decoder.readOnly) || enqueue.bits.loadStore || enqueue.bits.decodeResult(
      Decoder.gather
    ) ||
    enqueue.bits.decodeResult(Decoder.crossRead) || enqueue.bits.decodeResult(Decoder.crossWrite)
  // 超出范围的一组不压到流水里面去
  val enqFire:             Bool                     =
    enqueue.fire && (!updateLaneState.outOfExecutionRange || enqueue.bits.additionalRW) && notMaskedAllElement
  val stageDataReg:        Data                     = RegEnable(stageWire, 0.U.asTypeOf(stageWire), enqFire)
  val filterVec:           Seq[(Bool, UInt)]        = Seq(0, 1, 2).map { filterSew =>
    // The lower 'dataGroupIndexSize' bits represent the offsets in the data group
    val dataGroupIndexSize: Int = 2 - filterSew
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
  val nextOrR: Bool = Mux1H(enqueue.bits.vSew1H, filterVec.map(_._1))

  // mask is exhausted
  updateLaneState.maskExhausted := !nextOrR

  /** The mask group will be updated */
  val maskGroupWillUpdate: Bool = enqueue.bits.decodeResult(Decoder.maskLogic) || updateLaneState.maskExhausted

  /** Encoding of different element lengths: 1, 8, 16, 32 */
  val elementLengthOH = Mux(enqueue.bits.decodeResult(Decoder.maskLogic), 1.U, enqueue.bits.vSew1H(2, 0) ## false.B)

  /** Which group of data will be accessed */
  val dataGroupIndex: UInt = Mux1H(
    elementLengthOH,
    Seq(
      enqueue.bits.maskGroupCount,
      enqueue.bits.maskGroupCount ## enqueue.bits.maskIndex(4, 2),
      enqueue.bits.maskGroupCount ## enqueue.bits.maskIndex(4, 1),
      enqueue.bits.maskGroupCount ## enqueue.bits.maskIndex
    )
  )

  /** The next element is out of execution range */
  updateLaneState.outOfExecutionRange := dataGroupIndex > enqueue.bits.lastGroupForInstruction || enqueue.bits.instructionFinished

  val isTheLastGroup: Bool = dataGroupIndex === enqueue.bits.lastGroupForInstruction

  // Correct the mask on the boundary line
  val vlNeedCorrect:     Bool = Mux1H(
    enqueue.bits.vSew1H(1, 0),
    Seq(
      enqueue.bits.csr.vl(1, 0).orR,
      enqueue.bits.csr.vl(0)
    )
  )
  val correctMask:       UInt = Mux1H(
    enqueue.bits.vSew1H(1, 0),
    Seq(
      (scanRightOr(UIntToOH(enqueue.bits.csr.vl(1, 0))) >> 1).asUInt,
      1.U(4.W)
    )
  )
  val needCorrect:       Bool =
    isTheLastGroup &&
      enqueue.bits.isLastLaneForInstruction &&
      vlNeedCorrect
  val maskCorrect:       UInt = Mux(needCorrect, correctMask, 15.U(4.W))
  val crossReadOnlyMask: UInt = Fill(4, !updateLaneState.outOfExecutionRange)

  stageWire.maskForMaskInput       :=
    Mux(
      enqueue.bits.maskType,
      (enqueue.bits.maskForMaskGroup >> enqueue.bits.maskIndex).asUInt(3, 0),
      0.U(4.W)
    )
  stageWire.boundaryMaskCorrection := maskCorrect & crossReadOnlyMask

  /** The index of next element in this mask group.(0-31) */
  updateLaneState.maskIndex := Mux(
    enqueue.bits.decodeResult(Decoder.maskLogic),
    0.U,
    Mux1H(enqueue.bits.vSew1H, filterVec.map(_._2))
  )

  stageWire.groupCounter := dataGroupIndex

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

  stageWire.readFromScalar := Mux1H(
    enqueue.bits.vSew1H,
    Seq(
      Fill(4, enqueue.bits.readFromScalar(7, 0)),
      Fill(2, enqueue.bits.readFromScalar(15, 0)),
      enqueue.bits.readFromScalar
    )
  )

  stageWire.bordersForMaskLogic :=
    stageWire.groupCounter === enqueue.bits.lastGroupForInstruction &&
      enqueue.bits.isLastLaneForInstruction

  when(enqFire ^ dequeue.fire) {
    stageValidReg := enqFire
  }

  dequeue.bits := stageDataReg

  tokenReport.valid                 := enqFire
  tokenReport.bits.decodeResult     := enqueue.bits.decodeResult
  tokenReport.bits.instructionIndex := enqueue.bits.instructionIndex
  tokenReport.bits.sSendResponse    := stageWire.sSendResponse.getOrElse(true.B)
  tokenReport.bits.mask             := stageWire.maskForMaskInput
}
