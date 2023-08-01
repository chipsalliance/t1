package v

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle

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
  val mask: UInt = UInt((parameter.datapathWidth/8).W)
  val sSendResponse: Option[Bool] =  Option.when(isLastSlot)(Bool())
  val groupCounter: UInt = UInt(parameter.groupNumberBits.W)
}

/** 这一级由 lane slot 里的 maskIndex maskGroupCount 来计算对应的 data group counter
  * 同时也会维护指令的结束与mask的更新
  */
class LaneStage0(parameter: LaneParameter, isLastSlot: Boolean) extends
  LaneStage(true)(
    new LaneStage0Enqueue(parameter),
    new LaneStage0Dequeue(parameter, isLastSlot)
  ) {
  val state: LaneState = IO(Input(new LaneState(parameter)))
  val updateLaneState: LaneStage0StateUpdate = IO(Output(new LaneStage0StateUpdate(parameter)))

  // 超出范围的一组不压到流水里面去
  val enqFire: Bool = enqueue.fire && !updateLaneState.outOfExecutionRange
  val stageWire: LaneStage0Dequeue = Wire(new LaneStage0Dequeue(parameter, isLastSlot))
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

  /** The index of next execute element in whole instruction */
  val elementIndexForInstruction = enqueue.bits.maskGroupCount ## Mux1H(
    state.vSew1H,
    Seq(
      enqueue.bits.maskIndex(parameter.datapathWidthBits - 1, 2) ## state.laneIndex ##  enqueue.bits.maskIndex(1, 0),
      enqueue.bits.maskIndex(parameter.datapathWidthBits - 1, 1) ## state.laneIndex ##  enqueue.bits.maskIndex(0),
      enqueue.bits.maskIndex ## state.laneIndex
    )
  )

  /** The next element is out of execution range */
  updateLaneState.outOfExecutionRange := Mux(
    state.decodeResult(Decoder.maskLogic),
    (enqueue.bits.maskGroupCount > state.lastGroupForInstruction),
    elementIndexForInstruction >= state.csr.vl
  ) || state.instructionFinished

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

  val isTheLastGroup = dataGroupIndex === state.lastGroupForInstruction

  stageWire.mask := (state.mask.bits >> enqueue.bits.maskIndex).asUInt(3, 0)

  /** The index of next element in this mask group.(0-31) */
  updateLaneState.maskIndex := Mux(
    state.decodeResult(Decoder.maskLogic),
    0.U,
    Mux1H(state.vSew1H, filterVec.map(_._2))
  )

  stageWire.groupCounter := dataGroupIndex

  /** next mask group */
  updateLaneState.maskGroupCount := enqueue.bits.maskGroupCount + maskGroupWillUpdate

  stageWire.sSendResponse.foreach { data =>
    data :=
      !(state.loadStore ||
        state.decodeResult(Decoder.readOnly) ||
        (state.decodeResult(Decoder.red) && isTheLastGroup) ||
        (state.decodeResult(Decoder.maskDestination) && (maskGroupWillUpdate || isTheLastGroup)) ||
        state.decodeResult(Decoder.ffo))
  }
  when(enqFire ^ dequeue.fire) {
    stageValidReg := enqFire
  }

  dequeue.bits := stageDataReg
}
