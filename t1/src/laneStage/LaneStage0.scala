// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle
import org.chipsalliance.t1.rtl._
import org.chipsalliance.t1.rtl.decoder.Decoder
import upickle.core.Annotator.Checker.Val

// stage 0
class LaneStage0Enqueue(parameter: LaneParameter) extends Bundle {
  val maskIndex: UInt = UInt(log2Ceil(parameter.maskGroupWidth).W)
  val maskForMaskGroup: UInt = UInt(parameter.datapathWidth.W)
  val maskGroupCount: UInt = UInt(parameter.maskGroupSizeBits.W)
}

class LaneStage0StateUpdate(parameter: LaneParameter) extends Bundle {
  val maskGroupCount: UInt = UInt(parameter.maskGroupSizeBits.W)
  val maskIndex: UInt = UInt(log2Ceil(parameter.maskGroupWidth).W)
  val outOfExecutionRange: Bool = Bool()
  val maskExhausted: Bool = Bool()
}

class LaneStage0Dequeue(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  val maskForMaskInput: UInt = UInt((parameter.datapathWidth/8).W)
  val boundaryMaskCorrection: UInt = UInt((parameter.datapathWidth/8).W)
  val sSendResponse: Option[Bool] =  Option.when(isLastSlot)(Bool())
  val groupCounter: UInt = UInt(parameter.groupNumberBits.W)
  val crossWrite: Bool = Bool()
}

/** 这一级由 lane slot 里的 maskIndex maskGroupCount 来计算对应的 data group counter
  * 同时也会维护指令的结束与mask的更新
  */
@instantiable
class LaneStage0(parameter: LaneParameter, isLastSlot: Boolean) extends
  LaneStage(true)(
    new LaneStage0Enqueue(parameter),
    new LaneStage0Dequeue(parameter, isLastSlot)
  ) {
  @public
  val state: LaneState = IO(Input(new LaneState(parameter)))
  @public
  val updateLaneState: LaneStage0StateUpdate = IO(Output(new LaneStage0StateUpdate(parameter)))

  val stageWire: LaneStage0Dequeue = Wire(new LaneStage0Dequeue(parameter, isLastSlot))
  // 这一组如果全被masked了也不压进流水
  val notMaskedAllElement: Bool = Mux1H(state.vSew1H, Seq(
    stageWire.maskForMaskInput.orR,
    stageWire.maskForMaskInput(1, 0).orR,
    stageWire.maskForMaskInput(0),
  )) || state.maskNotMaskedElement ||
    state.decodeResult(Decoder.maskDestination) || state.decodeResult(Decoder.red) ||
    state.decodeResult(Decoder.readOnly) ||  state.loadStore || state.decodeResult(Decoder.gather) ||
    state.decodeResult(Decoder.crossRead)
  // 超出范围的一组不压到流水里面去
  val enqFire: Bool = enqueue.fire && (!updateLaneState.outOfExecutionRange || state.additionalRead) && notMaskedAllElement
  val stageDataReg: Data = RegEnable(stageWire, 0.U.asTypeOf(stageWire), enqFire)
  val filterVec: Seq[(Bool, UInt)] = Seq(0, 1, 2).map { filterSew =>
    // The lower 'dataGroupIndexSize' bits represent the offsets in the data group
    val dataGroupIndexSize: Int = 2 - filterSew
    // each group has '2 ** dataGroupIndexSize' elements
    val dataGroupSize = 1 << dataGroupIndexSize
    // The data group index of last data group
    val groupIndex = (enqueue.bits.maskIndex >> dataGroupIndexSize).asUInt
    // Filtering data groups
    val groupFilter: UInt = scanLeftOr(UIntToOH(groupIndex)) ## false.B
    // Whether there are element in the data group that have not been masked
    // TODO: use 'record.maskGroupedOrR' & update it
    val maskForDataGroup: UInt =
    VecInit(state.maskForMaskGroup.asBools.grouped(dataGroupSize).map(_.reduce(_ || _)).toSeq).asUInt
    val groupFilterByMask = maskForDataGroup & groupFilter
    // ffo next group
    val nextDataGroupOH: UInt = ffo(groupFilterByMask)
    // This mask group has the next data group to execute
    val hasNextDataGroup = nextDataGroupOH.orR
    val nextElementBaseIndex: UInt = (OHToUInt(nextDataGroupOH) << dataGroupIndexSize).asUInt
    (hasNextDataGroup, nextElementBaseIndex)
  }

  /** is there any data left in this group? */
  val nextOrR: Bool = Mux1H(state.vSew1H, filterVec.map(_._1))

  // mask is exhausted
  updateLaneState.maskExhausted := !nextOrR

  /** The mask group will be updated */
  val maskGroupWillUpdate: Bool = state.decodeResult(Decoder.maskLogic) || updateLaneState.maskExhausted

  /** Encoding of different element lengths: 1, 8, 16, 32 */
  val elementLengthOH = Mux(state.decodeResult(Decoder.maskLogic), 1.U, state.vSew1H(2, 0) ## false.B)

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
  updateLaneState.outOfExecutionRange := dataGroupIndex > state.lastGroupForInstruction || state.instructionFinished

  val isTheLastGroup: Bool = dataGroupIndex === state.lastGroupForInstruction

  // Correct the mask on the boundary line
  val vlNeedCorrect: Bool = Mux1H(
    state.vSew1H(1, 0),
    Seq(
      state.csr.vl(1, 0).orR,
      state.csr.vl(0)
    )
  )
  val correctMask: UInt = Mux1H(
    state.vSew1H(1, 0),
    Seq(
      (scanRightOr(UIntToOH(state.csr.vl(1, 0))) >> 1).asUInt,
      1.U(4.W)
    )
  )
  val needCorrect: Bool =
    isTheLastGroup &&
      state.isLastLaneForInstruction &&
      vlNeedCorrect
  val maskCorrect: UInt = Mux(needCorrect, correctMask, 15.U(4.W))
  val crossReadOnlyMask: UInt = Fill(4, !updateLaneState.outOfExecutionRange)

  stageWire.maskForMaskInput := (state.mask.bits >> enqueue.bits.maskIndex).asUInt(3, 0)
  stageWire.boundaryMaskCorrection := maskCorrect & crossReadOnlyMask

  /** The index of next element in this mask group.(0-31) */
  updateLaneState.maskIndex := Mux(
    state.decodeResult(Decoder.maskLogic),
    0.U,
    Mux1H(state.vSew1H, filterVec.map(_._2))
  )

  stageWire.groupCounter := dataGroupIndex
  stageWire.crossWrite := state.decodeResult(Decoder.crossWrite)

  /** next mask group */
  updateLaneState.maskGroupCount := enqueue.bits.maskGroupCount + maskGroupWillUpdate

  stageWire.sSendResponse.foreach { data =>
    data := !(Seq(
      state.loadStore,
      state.decodeResult(Decoder.readOnly),
      state.decodeResult(Decoder.red) && isTheLastGroup,
      state.decodeResult(Decoder.maskDestination) && (maskGroupWillUpdate || isTheLastGroup),
      state.decodeResult(Decoder.ffo)
    ) ++ Option.when(parameter.fpuEnable)(state.decodeResult(Decoder.orderReduce))).reduce(_ || _)
  }
  when(enqFire ^ dequeue.fire) {
    stageValidReg := enqFire
  }

  dequeue.bits := stageDataReg
}
