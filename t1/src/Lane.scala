// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy._
import chisel3.experimental._
import chisel3.ltl._
import chisel3.ltl.Sequence._
import chisel3.probe.{define, Probe, ProbeValue}
import chisel3.properties.{AnyClassType, ClassType, Path, Property}
import chisel3.util.{BitPat, _}
import chisel3.util.experimental.decode.DecodeBundle
import org.chipsalliance.t1.rtl.decoder.{Decoder, DecoderParam}
import org.chipsalliance.t1.rtl.lane._
import org.chipsalliance.t1.rtl.vrf.{RamType, VRF, VRFParam, VRFProbe}
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}
import org.chipsalliance.stdlib.GeneralOM

// 1. Coverage
// 2. Performance signal via XMR
// 3. Arch Review.

@instantiable
class LaneOM(parameter: LaneParameter) extends GeneralOM[LaneParameter, Lane](parameter) {
  val vfus   = IO(Output(Property[Seq[AnyClassType]]()))
  @public
  val vfusIn = IO(Input(Property[Seq[AnyClassType]]()))
  vfus := vfusIn
  val vrf   = IO(Output(Property[AnyClassType]()))
  @public
  val vrfIn = IO(Input(Property[AnyClassType]()))
  vrf := vrfIn
}

class LaneSlotProbe(instructionIndexBits: Int, datapathWidth: Int) extends Bundle {
  val stage0EnqueueReady:             Bool = Bool()
  val stage0EnqueueValid:             Bool = Bool()
  val changingMaskSet:                Bool = Bool()
  val slotActive:                     Bool = Bool()
  val slotOccupied:                   Bool = Bool()
  val pipeFinish:                     Bool = Bool()
  val slotShiftValid:                 Bool = Bool()
  val decodeResultIsCrossReadOrWrite: Bool = Bool()
  val decodeResultIsScheduler:        Bool = Bool()
  val executionUnitVfuRequestReady:   Bool = Bool()
  val executionUnitVfuRequestValid:   Bool = Bool()
  val stage3VrfWriteReady:            Bool = Bool()
  val stage3VrfWriteValid:            Bool = Bool()

  // write queue enq for lane
  val writeQueueEnq: Bool = Bool()
  val writeTag:      UInt = UInt(instructionIndexBits.W)
  val writeMask:     UInt = UInt((datapathWidth / 8).W)
}

class LaneWriteProbe(instructionIndexBits: Int, datapathWidth: Int) extends Bundle {
  val writeTag:  UInt = UInt(instructionIndexBits.W)
  val writeMask: UInt = UInt((datapathWidth / 8).W)
}

class LaneProbe(parameter: LaneParameter) extends Bundle {
  val slots = Vec(parameter.chainingSize, new LaneSlotProbe(parameter.instructionIndexBits, parameter.datapathWidth))
  val laneRequestStall:    Bool = Bool()
  // @todo @Clo91eaf change to occupied for each slot.
  val lastSlotOccupied:    Bool = Bool()
  val instructionFinished: UInt = UInt(parameter.chainingSize.W)
  val instructionValid:    UInt = UInt(parameter.chaining1HBits.W)

  val crossWriteProbe: Vec[ValidIO[LaneWriteProbe]] =
    Vec(2, Valid(new LaneWriteProbe(parameter.instructionIndexBits, parameter.datapathWidth)))

  val vrfProbe: VRFProbe = new VRFProbe(parameter.vrfParam)
}

object LaneParameter {
  implicit def rwP: upickle.default.ReadWriter[LaneParameter] = upickle.default.macroRW
}

/** Parameter for [[Lane]].
  * @param vLen
  *   VLEN
  * @param dataPathWidth
  *   width of data path, can be 32 or 64, decides the memory bandwidth.
  * @param laneNumber
  *   how many lanes in the vector processor
  * @param chainingSize
  *   how many instructions can be chained
  * @param crossLaneVRFWriteEscapeQueueSize
  *   The cross lane write queue designed for latch data from cross lane interconnection, if VRF backpress on ring, this
  *   queue will be used for latch data from ring, in case of additional latency from ring. TODO: cover the queue full.
  */
case class LaneParameter(
  vLen:                             Int,
  eLen:                             Int,
  datapathWidth:                    Int,
  laneNumber:                       Int,
  chainingSize:                     Int,
  crossLaneVRFWriteEscapeQueueSize: Int,
  fpuEnable:                        Boolean,
  portFactor:                       Int,
  maskRequestLatency:               Int,
  vrfRamType:                       RamType,
  decoderParam:                     DecoderParam,
  vfuInstantiateParameter:          VFUInstantiateParameter)
    extends SerializableModuleParameter {
  val maskUnitVefWriteQueueSize: Int = 8

  val laneScale: Int = datapathWidth / eLen

  val chaining1HBits: Int = 2 << log2Ceil(chainingSize)

  /** 1 in MSB for instruction order. */
  val instructionIndexBits: Int = log2Ceil(chainingSize) + 1

  /** maximum of lmul, defined in spec. */
  val lmulMax: Int = 8

  /** see [[T1Parameter.sewMin]] */
  val sewMin: Int = 8

  /** The datapath is divided into groups based on SEW. The current implement is calculating them in multiple cycles.
    * TODO: we must parallelize it as much as possible since it highly affects the computation bandwidth.
    */
  val dataPathByteWidth: Int = datapathWidth / sewMin

  val dataPathByteBits: Int = log2Ceil(dataPathByteWidth)

  /** maximum of vl. */
  val vlMax: Int = vLen * lmulMax / sewMin

  /** width of [[vlMax]] `+1` is for vl being 0 to vlMax(not vlMax - 1). we use less than for comparing, rather than
    * less equal.
    */
  val vlMaxBits: Int = log2Ceil(vlMax) + 1

  /** how many group does a single register have.
    *
    * in each lane, for one vector register, it is divided into groups with size of [[datapathWidth]]
    */
  val singleGroupSize: Int = vLen / datapathWidth / laneNumber

  /** for each instruction, the maximum number of groups to execute. */
  val groupNumberMax: Int = singleGroupSize * lmulMax

  /** used as the LSB index of VRF access
    *
    * TODO: uarch doc the arrangement of VRF: {reg index, offset}
    *
    * for each number in table below, it represent a [[datapathWidth]]
    * {{{
    *         lane0 | lane1 | ...                                   | lane8
    * offset0    0  |    1  |    2  |    3  |    4  |    5  |    6  |    7
    * offset1    8  |    9  |   10  |   11  |   12  |   13  |   14  |   15
    * offset2   16  |   17  |   18  |   19  |   20  |   21  |   22  |   23
    * offset3   24  |   25  |   26  |   27  |   28  |   29  |   30  |   31
    * }}}
    */
  val vrfOffsetBits: Int = log2Ceil(singleGroupSize)

  /** +1 in MSB for comparing to next group number. we don't use `vl-1` is because if the mask of last group is all 0,
    * it should be jumped through. so we directly compare the next group number with the MSB of `vl`.
    */
  val groupNumberBits: Int = log2Ceil(groupNumberMax + 1)

  /** Half of [[datapathWidth]], this is used in the cross-lane accessing logic. */
  val halfDatapathWidth: Int = datapathWidth / 2

  /** uarch TODO: instantiate logic, add to each slot logic, add, shift, multiple, divide, other
    *
    * TODO: use Seq().size to calculate
    */
  val executeUnitNum: Int = 6

  /** hardware width of [[laneNumber]]. */
  val laneNumberBits: Int = 1.max(log2Ceil(laneNumber))

  /** hardware width of [[datapathWidth]]. */
  val datapathWidthBits: Int = log2Ceil(datapathWidth)

  /** see [[T1Parameter.maskGroupWidth]] */
  val maskGroupWidth: Int = datapathWidth

  /** see [[T1Parameter.maskGroupSize]] */
  val maskGroupSize: Int = vLen / maskGroupWidth

  /** hardware width of [[maskGroupSize]]. */
  val maskGroupSizeBits: Int = log2Ceil(maskGroupSize)

  /** Size of the queue for storing execution information todo: Determined by the longest execution unit
    */
  val executionQueueSize: Int = 4

  // outstanding of MaskExchangeUnit.maskReq
  val maskRequestQueueSize: Int = 8
  // outstanding of second pipe in MaskExchangeUnit
  val secondQueueSize:      Int = 8

  val lsuSize = 1
  // lane + lsu + top + mask unit
  val idWidth: Int = log2Ceil(laneNumber + lsuSize + 1 + 1)

  // dLen as Byte
  val dByte: Int = laneNumber * datapathWidth / 8

  /** Parameter for [[VRF]] */
  def vrfParam: VRFParam = VRFParam(vLen, laneNumber, datapathWidth, chainingSize, portFactor, vrfRamType)
}

/** Instantiate [[Lane]] from [[T1]],
  *   - [[VRF]] is designed for store the vector register of the processor.
  *   - datapath units: [[MaskedLogic]], [[LaneAdder]], [[LaneShifter]], [[LaneMul]], [[LaneDiv]], [[OtherUnit]]
  * @todo
  *   \@sequencer change it to public module.
  */
@instantiable
class Lane(val parameter: LaneParameter) extends Module with SerializableModule[LaneParameter] {
  val omInstance: Instance[LaneOM]    = Instantiate(new LaneOM(parameter))
  val omType:     ClassType           = omInstance.toDefinition.getClassType
  @public
  val om:         Property[ClassType] = IO(Output(Property[omType.Type]()))
  om := omInstance.getPropertyReference

  /** laneIndex is a IO constant for D/I and physical implementations.
    * @todo
    *   \@sequencer use Const here to mark this as Const.
    */
  @public
  val laneIndex: UInt = IO(Input(UInt(parameter.laneNumberBits.W)))
  // constant parameter for physical implementations.
  // @todo remove it.
  dontTouch(laneIndex)

  // @todo: change io to channels to data, control, etc,
  //        we need to define the opcode and dependency for these channels
  //        and eventually use NoC-like interconnect for Lane -> Sequencer -> LSU region.
  /** Cross lane VRF Read Interface. only used for `narrow` an `widen` TODO: benchmark the usecase for tuning the Ring
    * Bus width. find a real world case for using `narrow` and `widen` aggressively.
    */
  @public
  val readBusPort: Vec[RingPort[ReadBusData]] = IO(
    Vec(2, new RingPort(new ReadBusData(parameter.datapathWidth)))
  )

  /** VRF Write Interface. only used for `narrow` an `widen` TODO: benchmark the usecase for tuning the Ring Bus width.
    * find a real world case for using `narrow` and `widen` aggressively.
    */
  @public
  val writeBusPort2: Vec[RingPort[WriteBusData]] = IO(
    Vec(
      2,
      new RingPort(
        new WriteBusData(
          parameter.datapathWidth,
          parameter.instructionIndexBits,
          parameter.groupNumberBits
        )
      )
    )
  )

  @public
  val writeBusPort4: Vec[RingPort[WriteBusData]] = IO(
    Vec(
      4,
      new RingPort(
        new WriteBusData(
          parameter.datapathWidth,
          parameter.instructionIndexBits,
          parameter.groupNumberBits
        )
      )
    )
  )

  /** request from [[T1.decode]] to [[Lane]]. */
  @public
  val laneRequest: DecoupledIO[LaneRequest] = IO(
    Flipped(
      Decoupled(
        new LaneRequest(
          parameter.instructionIndexBits,
          parameter.decoderParam,
          parameter.datapathWidth,
          parameter.vlMaxBits,
          parameter.laneNumber,
          parameter.dataPathByteWidth
        )
      )
    )
  )

  @public
  val maskUnitRequest: DecoupledIO[MaskUnitExeReq] = IO(
    Decoupled(
      new MaskUnitExeReq(parameter.eLen, parameter.datapathWidth, parameter.instructionIndexBits, parameter.fpuEnable)
    )
  )

  /** for LSU and V accessing lane, this is not a part of ring, but a direct connection. */
  @public
  val vrfReadAddressChannel: DecoupledIO[VRFReadRequest] = IO(
    Flipped(
      Decoupled(
        new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
      )
    )
  )
  @public
  val vrfReadDataChannel:    UInt                        = IO(Output(UInt(parameter.datapathWidth.W)))

  val vrfWriteType: VRFWriteRequest = new VRFWriteRequest(
    parameter.vrfParam.regNumBits,
    parameter.vrfOffsetBits,
    parameter.instructionIndexBits,
    parameter.datapathWidth
  )

  @public
  val vrfWriteChannel: DecoupledIO[VRFWriteRequest] = IO(Flipped(Decoupled(vrfWriteType)))

  @public
  val writeFromMask: Bool = IO(Input(Bool()))

  /** for each instruction in the slot, response to top when instruction is finished in this lane. */
  @public
  val instructionFinished: UInt = IO(Output(UInt(parameter.chaining1HBits.W)))
  @public
  val vxsatReport:         UInt = IO(Output(UInt(parameter.chaining1HBits.W)))

  /** V0 update in the lane should also update [[T1.v0]] */
  @public
  val v0Update: ValidIO[V0Update] = IO(Valid(new V0Update(parameter.datapathWidth, parameter.vrfOffsetBits)))

  /** input of mask data */
  @public
  val maskInput: UInt = IO(Input(UInt(parameter.maskGroupWidth.W)))

  @public
  val askMask: MaskRequest = IO(Output(new MaskRequest(parameter.maskGroupSizeBits)))

  /** from [[T1.lsu]] to [[Lane.vrf]], indicate it's the load store is finished, used for chaining. because of load
    * store index EEW, is complicated for lane to calculate whether LSU is finished. let LSU directly tell each lane it
    * is finished.
    */
  @public
  val lsuLastReport: UInt = IO(Input(UInt(parameter.chaining1HBits.W)))

  /** for RaW, VRF should wait for buffer to be empty. */
  @public
  val loadDataInLSUWriteQueue: UInt = IO(Input(UInt(parameter.chaining1HBits.W)))

  @public
  val writeReadyForLsu: Bool = IO(Output(Bool()))
  @public
  val vrfReadyToStore:  Bool = IO(Output(Bool()))

  @public
  val freeCrossDataDeq: DecoupledIO[FreeWriteBusData] =
    IO(
      Decoupled(
        new FreeWriteBusData(
          parameter.datapathWidth,
          parameter.groupNumberBits,
          parameter.laneNumberBits,
          parameter.instructionIndexBits
        )
      )
    )

  @public
  val freeCrossDataEnq: DecoupledIO[FreeWriteBusData] =
    IO(
      Flipped(
        Decoupled(
          new FreeWriteBusData(
            parameter.datapathWidth,
            parameter.groupNumberBits,
            parameter.laneNumberBits,
            parameter.instructionIndexBits
          )
        )
      )
    )

  @public
  val freeCrossReqDeq: DecoupledIO[FreeWriteBusRequest] =
    IO(
      Decoupled(
        new FreeWriteBusRequest(
          parameter.datapathWidth,
          parameter.groupNumberBits,
          parameter.laneNumberBits
        )
      )
    )

  @public
  val freeCrossReqEnq: DecoupledIO[FreeWriteBusRequest] =
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

  @public
  val laneProbe = IO(Output(Probe(new LaneProbe(parameter), layers.Verification)))

  @public
  val reduceMaskRequest: DecoupledIO[reduceMaskRequest] = IO(Decoupled(new reduceMaskRequest(parameter.datapathWidth)))

  @public
  val reduceMaskResponse: DecoupledIO[reduceMaskRequest] = IO(
    Flipped(Decoupled(new reduceMaskRequest(parameter.datapathWidth)))
  )

  @public
  val writeCountForToken: DecoupledIO[WriteCountReport] = IO(
    Flipped(Decoupled(new WriteCountReport(parameter.vLen, parameter.laneNumber, parameter.instructionIndexBits)))
  )

  // TODO: remove
  dontTouch(writeBusPort2)
  val csrInterface: CSRInterface = laneRequest.bits.csrInterface

  /** VRF instantces. */
  val vrf: Instance[VRF] = Instantiate(new VRF(parameter.vrfParam))
  omInstance.vrfIn := Property(vrf.om.asAnyClassType)

  val fullMask: UInt = (-1.S(parameter.datapathWidth.W)).asUInt

  /** the slot is occupied by instruction */
  val slotOccupied: Vec[Bool] = RegInit(VecInit(Seq.fill(parameter.chainingSize)(false.B)))

  /** mask group count for slot */
  val maskGroupCountVec: Vec[UInt] =
    RegInit(VecInit(Seq.fill(parameter.chainingSize)(0.U(parameter.maskGroupSizeBits.W))))

  /** mask index for slot */
  val maskIndexVec: Vec[UInt] =
    RegInit(VecInit(Seq.fill(parameter.chainingSize)(0.U(log2Ceil(parameter.maskGroupWidth).W))))

  /** result of reduce instruction. */
  val reduceResult: UInt = RegInit(0.U(parameter.datapathWidth.W))

  /** arbiter for VRF write 1 for [[vrfWriteChannel]]
    */
  val vrfWriteArbiter: Vec[DecoupledIO[VRFWriteRequest]] = Wire(
    Vec(
      parameter.chainingSize + 1,
      Decoupled(
        new VRFWriteRequest(
          parameter.vrfParam.regNumBits,
          parameter.vrfOffsetBits,
          parameter.instructionIndexBits,
          parameter.datapathWidth
        )
      )
    )
  )

  vrfWriteArbiter(parameter.chainingSize).valid := vrfWriteChannel.valid
  vrfWriteArbiter(parameter.chainingSize).bits  := vrfWriteChannel.bits
  vrfWriteChannel.ready                         := vrfWriteArbiter(parameter.chainingSize).ready

  val allVrfWriteAfterCheck:  Seq[VRFWriteRequest] = Seq.tabulate(parameter.chainingSize + 1) { i =>
    RegInit(0.U.asTypeOf(vrfWriteArbiter.head.bits))
  }
  val afterCheckValid:        Seq[Bool]            = Seq.tabulate(parameter.chainingSize + 1) { _ => RegInit(false.B) }
  val afterCheckDequeueReady: Vec[Bool]            = Wire(Vec(parameter.chainingSize + 1, Bool()))
  val afterCheckDequeueFire:  Seq[Bool]            = afterCheckValid.zip(afterCheckDequeueReady).map { case (v, r) => v && r }

  // todo: mv to bundle.scala
  class MaskControl(parameter: LaneParameter) extends Bundle {
    val index:         UInt = UInt(parameter.instructionIndexBits.W)
    val sew:           UInt = UInt(2.W)
    val maskData:      UInt = UInt(parameter.datapathWidth.W)
    val group:         UInt = UInt(parameter.maskGroupSizeBits.W)
    val dataValid:     Bool = Bool()
    val waiteResponse: Bool = Bool()
    val controlValid:  Bool = Bool()

    // for slide mask
    val slide: Bool = Bool()
  }

  val maskControlRelease: Vec[ValidIO[UInt]] =
    Wire(Vec(parameter.chainingSize, Valid(UInt(parameter.instructionIndexBits.W))))

  val maskControlEnq:       UInt             = Wire(UInt(parameter.chainingSize.W))
  val maskControlDataDeq:   UInt             = Wire(UInt(parameter.chainingSize.W))
  val maskControlReq:       Vec[Bool]        = Wire(Vec(parameter.chainingSize, Bool()))
  val maskControlReqSelect: UInt             = ffo(maskControlReq.asUInt)
  // mask request & response handle
  val maskControlVec:       Seq[MaskControl] = Seq.tabulate(parameter.chainingSize) { index =>
    val state = RegInit(0.U.asTypeOf(new MaskControl(parameter)))
    val releaseHit: Bool = maskControlRelease.map(r => r.valid && (r.bits === state.index)).reduce(_ || _)
    val responseFire =
      Pipe(maskControlReqSelect(index), 0.U.asTypeOf(new EmptyBundle), parameter.maskRequestLatency).valid

    when(maskControlEnq(index)) {
      state              := 0.U.asTypeOf(state)
      state.index        := laneRequest.bits.instructionIndex
      state.sew          := laneRequest.bits.csrInterface.vSew
      state.controlValid := true.B
      state.slide        := laneRequest.bits.decodeResult(Decoder.maskPipeUop) === BitPat("b001??")
    }

    when(state.controlValid) {
      when(releaseHit) {
        state.controlValid := false.B
      }
    }

    maskControlReq(index) := state.controlValid && !state.dataValid && !state.waiteResponse
    when(maskControlReqSelect(index)) {
      state.waiteResponse := true.B
      state.group         := state.group + 1.U
    }

    when(responseFire) {
      state.dataValid     := true.B
      state.waiteResponse := false.B
      state.maskData      := maskInput
    }

    when(maskControlDataDeq(index)) {
      state.dataValid := false.B
    }

    state
  }
  val maskControlFree:      Seq[Bool]        = maskControlVec.map(s => !s.controlValid && !s.waiteResponse)
  val freeSelect:           UInt             = ffo(VecInit(maskControlFree).asUInt)
  maskControlEnq := maskAnd(laneRequest.fire && laneRequest.bits.mask, freeSelect)

  /** for each slot, assert when it is asking [[T1]] to change mask */
  val slotMaskRequestVec: Vec[ValidIO[UInt]] = Wire(
    Vec(
      parameter.chainingSize,
      Valid(UInt(parameter.maskGroupSizeBits.W))
    )
  )

  /** which slot wins the arbitration for requesting mask. */
  val maskRequestFireOH: Vec[Bool] = Wire(Vec(parameter.chainingSize, Bool()))
  val maskDataVec:       Vec[UInt] = Wire(Vec(parameter.chainingSize, UInt(parameter.maskGroupWidth.W)))

  /** FSM control for each slot. if index == 0,
    *   - slot can support write v0 in mask type, see [[Decoder.maskDestination]] [[Decoder.maskSource]]
    *     [[Decoder.maskLogic]]
    *   - cross lane read/write.
    *   - and all other instructions. TODO: clear [[Decoder.maskDestination]] [[Decoder.maskSource]]
    *     [[Decoder.maskLogic]] out from `index == 0`? we may only need cross lane read/write in `index == 0` the index
    *     != 0 slot is used for all other instructions.
    */
  val slotControl: Vec[InstructionControlRecord] =
    RegInit(
      VecInit(
        Seq.fill(parameter.chainingSize)(0.U.asTypeOf(new InstructionControlRecord(parameter)))
      )
    )

  /** VRF read request for each slot, 3 is for [[source1]] [[source2]] [[source3]]
    */
  val vrfReadRequest: Vec[Vec[DecoupledIO[VRFReadRequest]]] = Wire(
    Vec(
      parameter.chainingSize,
      Vec(
        3,
        Decoupled(
          new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
        )
      )
    )
  )

  /** VRF read result for each slot, 3 is for [[source1]] [[source2]] [[source3]]
    */
  val vrfReadResult: Vec[Vec[UInt]] = Wire(
    Vec(
      parameter.chainingSize,
      Vec(3, UInt(parameter.datapathWidth.W))
    )
  )

  // 3 * slot + 2 cross read
  val readCheckRequestVec: Vec[VRFReadRequest] = Wire(
    Vec(
      parameter.chainingSize * 3 + 2,
      new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
    )
  )

  val readCheckResult: Vec[Bool] = Wire(Vec(parameter.chainingSize * 3 + 2, Bool()))

  /** signal used for prohibiting slots to access VRF. a slot will become inactive when:
    *   1. cross lane read/write is not finished 2. lanes doesn't win mask request
    */
  val slotActive: Vec[Bool] = Wire(Vec(parameter.chainingSize, Bool()))

  /** When the slot wants to move, you need to stall the pipeline first and wait for the pipeline to be cleared.
    */
  val slotShiftValid: Vec[Bool] = Wire(Vec(parameter.chainingSize, Bool()))

  /** The slots start to shift in these rules:
    *   - instruction can only enqueue to the last slot.
    *   - all slots can only shift at the same time which means: if one slot is finished earlier -> 1101, it will wait
    *     for the first slot to finish -> 1100, and then take two cycles to move to xx11.
    */
  val slotCanShift: Vec[Bool] = Wire(Vec(parameter.chainingSize, Bool()))

  /** Which data group is waiting for the result of the cross-lane read */
  val readBusDequeueGroup: UInt = Wire(UInt(parameter.groupNumberBits.W))

  /** enqueue valid for execution unit */
  val executeEnqueueValid: Vec[Bool] = Wire(Vec(parameter.chainingSize + 1, Bool()))

  /** request from slot to vfu. */
  val requestVec: Vec[SlotRequestToVFU] = Wire(Vec(parameter.chainingSize + 1, new SlotRequestToVFU(parameter)))

  /** decode message for [[requestVec]]. */
  val executeDecodeVec: Vec[DecodeBundle] = Wire(
    Vec(parameter.chainingSize + 1, Decoder.bundle(parameter.decoderParam))
  )

  /** decode message for [[responseVec]]. */
  val responseDecodeVec: Vec[DecodeBundle] = Wire(
    Vec(parameter.chainingSize + 1, Decoder.bundle(parameter.decoderParam))
  )

  /** response from vfu to slot. */
  val responseVec: Vec[ValidIO[VFUResponseToSlot]] = Wire(
    Vec(parameter.chainingSize + 1, Valid(new VFUResponseToSlot(parameter)))
  )

  /** enqueue fire signal for execution unit */
  val executeEnqueueFire: Vec[Bool] = Wire(Vec(parameter.chainingSize + 1, Bool()))

  val executeOccupied: Vec[Bool] = Wire(Vec(parameter.vfuInstantiateParameter.genVec.size, Bool()))
  dontTouch(executeOccupied)

  val VFUNotClear: Bool = Wire(Bool())

  val slot0EnqueueFire: Bool = Wire(Bool())

  /** assert when a instruction is valid in the slot. */
  val instructionValid:     UInt = Wire(UInt(parameter.chaining1HBits.W))
  val instructionValidNext: UInt = RegNext(instructionValid, 0.U)

  val vxsatResult: UInt = RegInit(0.U(parameter.chaining1HBits.W))
  vxsatReport := vxsatResult

  // Overflow occurs
  val vxsatEnq: Vec[UInt] = Wire(Vec(parameter.chainingSize, UInt(parameter.chaining1HBits.W)))

  val instructionFinishInSlot: UInt = Wire(UInt(parameter.chaining1HBits.W))
  // vxsatEnq and instructionFinished cannot happen at the same time
  vxsatResult := (vxsatEnq.reduce(_ | _) | vxsatResult) & (~instructionFinishInSlot).asUInt

  /** assert when a instruction will not use mask unit */
  val instructionUnrelatedMaskUnitVec: Vec[UInt] = Wire(Vec(parameter.chainingSize, UInt(parameter.chainingSize.W)))

  val maskedWriteUnit: Instance[MaskedWrite]      = Instantiate(new MaskedWrite(parameter))
  val tokenManager:    Instance[SlotTokenManager] = Instantiate(new SlotTokenManager(parameter))
  // TODO: do we need to expose the slot to a module?
  class Slot(val record: InstructionControlRecord, val index: Int) {
    val decodeResult: DecodeBundle = record.laneRequest.decodeResult
    val isLastSlot:   Boolean      = index == 0

    /** We will ignore the effect of mask since: [[Decoder.crossRead]]: We need to read data to another lane
      * [[Decoder.crossWrite]]: We need to send cross write report to another lane [[Decoder.scheduler]]: We need to
      * synchronize with [[T1]] every group [[record.laneRequest.loadStore]]: We need to read data to lsu every group
      */
    val alwaysNextGroup: Bool = decodeResult(Decoder.crossRead) || decodeResult(Decoder.crossWrite) ||
      decodeResult(Decoder.nr) || !decodeResult(Decoder.scheduler) || record.laneRequest.loadStore

    // mask not use for mask element
    val maskNotMaskedElement = !record.laneRequest.mask ||
      record.laneRequest.decodeResult(Decoder.maskSource) ||
      record.laneRequest.decodeResult(Decoder.maskLogic)

    /** onehot value of SEW. */
    val vSew1H: UInt = UIntToOH(record.laneRequest.csrInterface.vSew)(2, 0)

    /** if asserted, the element won't be executed. adc: vm = 0; madc: vm = 0 -> s0 + s1 + c, vm = 1 -> s0 + s1
      */
    val skipEnable: Bool = record.laneRequest.mask &&
      !record.laneRequest.decodeResult(Decoder.maskSource) &&
      !record.laneRequest.decodeResult(Decoder.maskLogic) &&
      !alwaysNextGroup

    // register for s0 enqueue, it will move with the slot
    // 'maskGroupCountVec' 'maskIndexVec' 'pipeFinishVec'

    if (isLastSlot) {
      // todo: Reach vfu
      slotActive(index) := slotOccupied(index)
    } else {
      slotActive(index) := slotOccupied(index) && !slotShiftValid(index) &&
        !(decodeResult(Decoder.crossRead) || decodeResult(Decoder.crossWrite) || decodeResult(Decoder.widenReduce)) &&
        decodeResult(Decoder.scheduler)
    }

    if (isLastSlot) {
      slotCanShift(index) := !slotOccupied(index)
    } else {
      slotCanShift(index) := true.B
    }

    val laneState:       LaneState                          = Wire(new LaneState(parameter))
    val stage0:          Instance[LaneStage0]               = Instantiate(new LaneStage0(parameter, isLastSlot))
    val stage1:          Instance[LaneStage1]               = Instantiate(new LaneStage1(parameter, isLastSlot))
    val stage2:          Instance[LaneStage2]               = Instantiate(new LaneStage2(parameter, isLastSlot))
    val executionUnit:   Instance[LaneExecutionBridge]      = Instantiate(
      new LaneExecutionBridge(parameter, isLastSlot, index)
    )
    val maskStage:       Option[Instance[MaskExchangeUnit]] =
      Option.when(isLastSlot)(Instantiate(new MaskExchangeUnit(parameter)))
    val stage3:          Instance[LaneStage3]               = Instantiate(new LaneStage3(parameter, isLastSlot))
    val stage3EnqWire:   DecoupledIO[LaneStage3Enqueue]     = Wire(Decoupled(new LaneStage3Enqueue(parameter, isLastSlot)))
    val stage3EnqSelect: DecoupledIO[LaneStage3Enqueue]     = maskStage.map { mask =>
      mask.enqueue <> stage3EnqWire
      mask.pipeForMask.sew1H         := stage2.dequeue.bits.vSew1H
      mask.pipeForMask.readFromScala := stage2.dequeue.bits.readFromScalar.get
      mask.pipeForMask.csr           := stage2.dequeue.bits.csr.get
      mask.pipeForMask.source1       := executionUnit.dequeue.bits.source1.get
      mask.pipeForMask.source2       := executionUnit.dequeue.bits.source2.get
      mask.pipeForMask.vl            := executionUnit.dequeue.bits.vl.get
      mask.pipeForMask.vlmul         := executionUnit.dequeue.bits.vlmul.get
      mask.laneIndex                 := laneIndex
      maskUnitRequest <> mask.maskReq
      maskUnitRequest.bits.maskRequestToLSU <> mask.maskRequestToLSU

      requestVec.last             := mask.reduceVRFRequest.bits
      executeDecodeVec.last       := mask.reduceRequestDecode
      responseDecodeVec.last      := mask.reduceRequestDecode
      executeEnqueueValid.last    := mask.reduceVRFRequest.valid
      mask.reduceVRFRequest.ready := executeEnqueueFire.last
      mask.reduceResponse         := responseVec.last

      mask.dequeue
    }.getOrElse(stage3EnqWire)
    stage3.enqueue <> stage3EnqSelect

    // slot state
    laneState.vSew1H                   := vSew1H
    laneState.vSew                     := record.laneRequest.csrInterface.vSew
    laneState.loadStore                := record.laneRequest.loadStore
    laneState.laneIndex                := laneIndex
    laneState.decodeResult             := record.laneRequest.decodeResult
    laneState.lastGroupForInstruction  := record.lastGroupForInstruction
    laneState.isLastLaneForInstruction := record.isLastLaneForInstruction
    laneState.instructionFinished      := record.instructionFinished
    laneState.csr                      := record.laneRequest.csrInterface
    laneState.maskType                 := record.laneRequest.mask
    laneState.maskNotMaskedElement     := !record.laneRequest.mask ||
      record.laneRequest.decodeResult(Decoder.maskSource) ||
      record.laneRequest.decodeResult(Decoder.maskLogic)
    laneState.vs1                      := record.laneRequest.vs1
    laneState.vs2                      := record.laneRequest.vs2
    laneState.vd                       := record.laneRequest.vd
    laneState.instructionIndex         := record.laneRequest.instructionIndex
    laneState.skipEnable               := skipEnable
    laneState.additionalRW             := record.additionalRW
    laneState.skipRead                 := record.laneRequest.decodeResult(Decoder.other) &&
      (record.laneRequest.decodeResult(Decoder.uop) === 9.U)

    stage0.enqueue.valid                 := slotActive(index) && (record.mask.valid || !record.laneRequest.mask)
    stage0.enqueue.bits.maskIndex        := maskIndexVec(index)
    stage0.enqueue.bits.maskForMaskGroup := record.mask.bits
    stage0.enqueue.bits.maskGroupCount   := maskGroupCountVec(index)
    stage0.enqueue.bits.maskE0           := record.laneRequest.maskE0 || !record.laneRequest.mask
    // todo: confirm
    stage0.enqueue.bits.elements.foreach { case (k, d) =>
      laneState.elements.get(k).foreach(stateData => d := stateData)
    }

    maskControlRelease(index).valid := false.B
    maskControlRelease(index).bits  := record.laneRequest.instructionIndex
    // update lane state
    when(stage0.enqueue.fire) {
      maskGroupCountVec(index) := stage0.updateLaneState.maskGroupCount
      // todo: handle all elements in first group are masked
      maskIndexVec(index)      := stage0.updateLaneState.maskIndex
      when(stage0.updateLaneState.outOfExecutionRange) {
        slotOccupied(index)             := false.B
        maskControlRelease(index).valid := true.B
      }
    }

    // update mask todo: handle maskRequestFireOH
    slotMaskRequestVec(index).valid :=
      record.laneRequest.mask && slotOccupied(index) &&
        ((stage0.enqueue.fire && stage0.updateLaneState.maskExhausted) || !record.mask.valid)
    slotMaskRequestVec(index).bits  := stage0.updateLaneState.maskGroupCount
    // There are new masks
    val maskUpdateFire: Bool = slotMaskRequestVec(index).valid && maskRequestFireOH(index)
    // The old mask is used up
    val maskFailure:    Bool = stage0.updateLaneState.maskExhausted && stage0.enqueue.fire
    // update mask register
    when(maskUpdateFire) {
      record.mask.bits := maskDataVec(index)
    }
    when(maskUpdateFire ^ maskFailure) {
      record.mask.valid := maskUpdateFire
    }

    val instructionIndex1H: UInt = UIntToOH(
      record.laneRequest.instructionIndex(parameter.instructionIndexBits - 2, 0)
    )
    instructionUnrelatedMaskUnitVec(index) :=
      Mux(decodeResult(Decoder.maskUnit) && decodeResult(Decoder.readOnly), 0.U, instructionIndex1H)

    // stage 1: read stage
    stage1.enqueue.valid                       := stage0.dequeue.valid
    stage0.dequeue.ready                       := stage1.enqueue.ready
    stage1.enqueue.bits.groupCounter           := stage0.dequeue.bits.groupCounter
    stage1.enqueue.bits.maskForMaskInput       := stage0.dequeue.bits.maskForMaskInput
    stage1.enqueue.bits.boundaryMaskCorrection := stage0.dequeue.bits.boundaryMaskCorrection
    stage1.enqueue.bits.sSendResponse.zip(stage0.dequeue.bits.sSendResponse).foreach { case (sink, source) =>
      sink := source
    }
    stage1.dequeue.bits.readBusDequeueGroup.foreach(data => readBusDequeueGroup := data)

    stage1.enqueue.bits.elements.foreach { case (k, d) =>
      stage0.dequeue.bits.elements.get(k).foreach(stateData => d := stateData)
    }
    stage0.enqueue.bits.readFromScalar := record.laneRequest.readFromScalar
    vrfReadRequest(index).zip(stage1.vrfReadRequest).foreach { case (sink, source) => sink <> source }
    vrfReadResult(index).zip(stage1.vrfReadResult).foreach { case (source, sink) => sink := source }
    // 3: read vs1 vs2 vd
    // 2: cross read lsb & msb
    val checkSize = if (isLastSlot) 5 else 3
    Seq.tabulate(checkSize) { portIndex =>
      // parameter.chainingSize - index: slot 0 need 5 port, so reverse connection
      readCheckRequestVec((parameter.chainingSize - index - 1) * 3 + portIndex) := stage1.vrfCheckRequest(portIndex)
      stage1.checkResult(portIndex)                                             := readCheckResult((parameter.chainingSize - index - 1) * 3 + portIndex)
    }
    // connect cross read bus
    if (isLastSlot) {
      val tokenSize = parameter.crossLaneVRFWriteEscapeQueueSize
      readBusPort.zipWithIndex.foreach { case (readPort, portIndex) =>
        // tx
        readPort.deq <> stage1.readBusRequest.get(portIndex)
        // rx
        // rx queue
        val queue = Queue.io(chiselTypeOf(readPort.deq.bits), tokenSize, pipe = true)
        queue.enq <> readPort.enq
        // dequeue to cross read unit
        stage1.readBusDequeue.get(portIndex) <> queue.deq
      }

      // cross write
      writeBusPort2.zipWithIndex.foreach { case (writePort, portIndex) =>
        writePort.deq <> maskStage.get.crossWritePort2Deq(portIndex)
        maskStage.get.crossWritePort2Enq(portIndex) <> writePort.enq
      }
      writeBusPort4.zipWithIndex.foreach { case (writePort, portIndex) =>
        writePort.deq <> maskStage.get.crossWritePort4Deq(portIndex)
        maskStage.get.crossWritePort4Enq(portIndex) <> writePort.enq
      }
      freeCrossDataDeq <> maskStage.get.freeCrossDataDeq
      maskStage.get.freeCrossDataEnq <> freeCrossDataEnq
      freeCrossReqDeq <> maskStage.get.freeCrossReqDeq
      stage0.freeCrossReqEnq.get <> freeCrossReqEnq
      stage0.maskPipeRelease.get <> maskStage.get.maskPipeRelease

      reduceMaskRequest <> maskStage.get.reduceMaskRequest
      maskStage.get.reduceMaskResponse <> reduceMaskResponse

      stage1.enqueue.bits.secondPipe.get        := stage0.dequeue.bits.secondPipe.get
      stage1.enqueue.bits.emptyPipe.get         := stage0.dequeue.bits.emptyPipe.get
      stage1.enqueue.bits.pipeForSecondPipe.get := stage0.dequeue.bits.pipeForSecondPipe.get

      stage2.enqueue.bits.secondPipe.get        := stage1.dequeue.bits.secondPipe.get
      stage2.enqueue.bits.emptyPipe.get         := stage1.dequeue.bits.emptyPipe.get
      stage2.enqueue.bits.pipeForSecondPipe.get := stage1.dequeue.bits.pipeForSecondPipe.get
    }

    stage2.enqueue.valid        := stage1.dequeue.valid && executionUnit.enqueue.ready
    stage1.dequeue.ready        := stage2.enqueue.ready && executionUnit.enqueue.ready
    executionUnit.enqueue.valid := stage1.dequeue.valid && stage2.enqueue.ready

    stage2.enqueue.bits.elements.foreach { case (k, d) =>
      stage1.dequeue.bits.elements.get(k).foreach(pipeData => d := pipeData)
    }
    stage2.enqueue.bits.groupCounter        := stage1.dequeue.bits.groupCounter
    stage2.enqueue.bits.mask                := stage1.dequeue.bits.mask
    stage2.enqueue.bits.maskE0              := stage1.dequeue.bits.maskE0
    stage2.enqueue.bits.maskForFilter       := stage1.dequeue.bits.maskForFilter
    stage2.enqueue.bits.src                 := stage1.dequeue.bits.src
    stage2.enqueue.bits.sSendResponse.zip(stage1.dequeue.bits.sSendResponse).foreach { case (sink, source) =>
      sink := source
    }
    stage2.enqueue.bits.bordersForMaskLogic := executionUnit.enqueue.bits.bordersForMaskLogic

    executionUnit.enqueue.bits.elements.foreach { case (k, d) =>
      stage1.dequeue.bits.elements.get(k).foreach(pipeData => d := pipeData)
    }
    executionUnit.enqueue.bits.src                 := stage1.dequeue.bits.src
    executionUnit.enqueue.bits.bordersForMaskLogic := stage1.dequeue.bits.bordersForMaskLogic
    executionUnit.enqueue.bits.mask                := stage1.dequeue.bits.mask
    executionUnit.enqueue.bits.maskForFilter       := stage1.dequeue.bits.maskForFilter
    executionUnit.enqueue.bits.groupCounter        := stage1.dequeue.bits.groupCounter
    executionUnit.enqueue.bits.sSendResponse.zip(stage1.dequeue.bits.sSendResponse).foreach { case (sink, source) =>
      sink := source
    }
    executionUnit.enqueue.bits.crossReadSource.zip(stage1.dequeue.bits.crossReadSource).foreach { case (sink, source) =>
      sink := source
    }

    // executionUnit <> vfu
    requestVec(index)              := executionUnit.vfuRequest.bits
    executeDecodeVec(index)        := executionUnit.executeDecode
    responseDecodeVec(index)       := executionUnit.responseDecode
    executeEnqueueValid(index)     := executionUnit.vfuRequest.valid
    executionUnit.vfuRequest.ready := executeEnqueueFire(index)
    executionUnit.dataResponse     := responseVec(index)

    vxsatEnq(index)             := Mux(
      executionUnit.dataResponse.valid &&
        (executionUnit.dataResponse.bits.clipFail ## executionUnit.dataResponse.bits.vxsat).orR,
      indexToOH(executionUnit.responseIndex, parameter.chainingSize),
      0.U(parameter.chainingSize.W)
    )
    AssertProperty(BoolSequence(!executionUnit.dequeue.valid || stage2.dequeue.valid))
    stage3EnqWire.valid         := executionUnit.dequeue.valid
    executionUnit.dequeue.ready := stage3EnqWire.ready
    stage2.dequeue.ready        := executionUnit.dequeue.fire

    if (!isLastSlot) {
      stage3EnqWire.bits := DontCare
    }

    // pipe state from stage0
    stage3EnqWire.bits.decodeResult     := stage2.dequeue.bits.decodeResult
    stage3EnqWire.bits.instructionIndex := stage2.dequeue.bits.instructionIndex
    stage3EnqWire.bits.loadStore        := stage2.dequeue.bits.loadStore
    stage3EnqWire.bits.vd               := stage2.dequeue.bits.vd
    stage3EnqWire.bits.maskE0           := stage2.dequeue.bits.maskE0
    stage3EnqWire.bits.ffoByOtherLanes  := false.B
    stage3EnqWire.bits.groupCounter     := stage2.dequeue.bits.groupCounter
    stage3EnqWire.bits.mask             := stage2.dequeue.bits.mask
    if (isLastSlot) {
      stage3EnqWire.bits.sSendResponse         := stage2.dequeue.bits.sSendResponse.get
      stage3EnqWire.bits.ffoSuccess            := executionUnit.dequeue.bits.ffoSuccess.get
      stage3EnqWire.bits.fpReduceValid.zip(executionUnit.dequeue.bits.fpReduceValid).foreach { case (sink, source) =>
        sink := source
      }
      // for mask pipe
      stage3EnqWire.bits.secondPipe.get        := stage2.dequeue.bits.secondPipe.get
      stage3EnqWire.bits.emptyPipe.get         := stage2.dequeue.bits.emptyPipe.get
      stage3EnqWire.bits.pipeForSecondPipe.get := stage2.dequeue.bits.pipeForSecondPipe.get
    }
    stage3EnqWire.bits.data             := executionUnit.dequeue.bits.data
    stage3EnqWire.bits.pipeData         := stage2.dequeue.bits.pipeData.getOrElse(DontCare)
    stage3EnqWire.bits.ffoIndex         := executionUnit.dequeue.bits.ffoIndex
    executionUnit.dequeue.bits.crossWriteData.foreach(data => stage3EnqWire.bits.crossWriteData := data)
    stage2.dequeue.bits.sSendResponse.foreach(_ => stage3EnqWire.bits.sSendResponse := _)
    executionUnit.dequeue.bits.ffoSuccess.foreach(_ => stage3EnqWire.bits.ffoSuccess := _)

    // --- stage 3 end & stage 4 start ---
    // vrfWriteQueue try to write vrf
    vrfWriteArbiter(index).valid := stage3.vrfWriteRequest.valid
    vrfWriteArbiter(index).bits  := stage3.vrfWriteRequest.bits
    stage3.vrfWriteRequest.ready := vrfWriteArbiter(index).ready

    tokenManager.enqReports(index) := stage0.tokenReport
  }
  val slots = slotControl.zipWithIndex.map { case (record: InstructionControlRecord, index: Int) =>
    new Slot(record, index)
  }

  val vfus: Seq[Instance[VFUModule]] = instantiateVFU(parameter.vfuInstantiateParameter)(
    requestVec,
    executeEnqueueValid,
    executeDecodeVec,
    responseDecodeVec,
    executeEnqueueFire,
    responseVec,
    executeOccupied,
    VFUNotClear
  )
  omInstance.vfusIn := Property(vfus.map(_.om.asAnyClassType))

  // It’s been a long time since I selected it. Need pipe
  val queueBeforeMaskWrite: QueueIO[VRFWriteRequest] =
    Queue.io(chiselTypeOf(maskedWriteUnit.enqueue.bits), entries = 1, pipe = true)
  val writeSelect:          UInt                     = Wire(UInt((parameter.chainingSize + 1).W))
  val writeCavitation:      UInt                     = VecInit(allVrfWriteAfterCheck.map(_.mask === 0.U)).asUInt
  val slotEnqueueFire:      Seq[Bool]                = Seq.tabulate(parameter.chainingSize)(_ => Wire(Bool()))

  // 处理 rf
  {
    val readBeforeMaskedWrite: DecoupledIO[VRFReadRequest]      = Wire(
      Decoupled(
        new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
      )
    )
    val readPortVec:           Seq[DecoupledIO[VRFReadRequest]] =
      readBeforeMaskedWrite +: vrfReadRequest.flatten :+ vrfReadAddressChannel
    val readResultVec:         Seq[UInt]                        = maskedWriteUnit.vrfReadResult +: vrfReadResult.flatten :+ vrfReadDataChannel
    parameter.vrfParam.connectTree.zipWithIndex.foreach { case (connectSource, vrfPortIndex) =>
      val readArbiter = Module(
        new Arbiter(
          new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits),
          connectSource.size
        )
      ).suggestName(s"vrfReadArbiter_${vrfPortIndex}")

      connectSource.zipWithIndex.foreach { case (sourceIndex, i) =>
        // connect arbiter input
        readArbiter.io.in(i) <> readPortVec(sourceIndex)
        // connect arbiter output
        vrf.readRequests(vrfPortIndex) <> readArbiter.io.out
        // connect read result
        readResultVec(sourceIndex) := vrf.readResults(vrfPortIndex)
      }
    }

    // all vrf write
    val allVrfWrite: Seq[DecoupledIO[VRFWriteRequest]] = vrfWriteArbiter
    // check all write
    vrf.writeCheck.zip(allVrfWrite).foreach { case (check, write) =>
      check.vd               := write.bits.vd
      check.offset           := write.bits.offset
      check.instructionIndex := write.bits.instructionIndex
    }

    vrf.readCheck.zip(readCheckRequestVec).foreach { case (sink, source) => sink := source }
    readCheckResult.zip(vrf.readCheckResult).foreach { case (sink, source) => sink := source }

    allVrfWriteAfterCheck.zipWithIndex.foreach { case (req, i) =>
      val check    = vrf.writeAllow(i)
      val enqReady = check && (!afterCheckValid(i) || afterCheckDequeueReady(i))
      val enqFire  = enqReady && allVrfWrite(i).valid
      allVrfWrite(i).ready := enqReady
      when(enqFire) {
        req := allVrfWrite(i).bits
      }
      val deqFire = afterCheckDequeueFire(i)
      when(deqFire ^ enqFire) {
        afterCheckValid(i) := enqFire
      }
    }

    // Arbiter
    writeSelect := ffo(VecInit(afterCheckValid).asUInt & (~writeCavitation).asUInt)
    afterCheckDequeueReady.zipWithIndex.foreach { case (p, i) =>
      p := (writeSelect(i) && queueBeforeMaskWrite.enq.ready) || writeCavitation(i)
    }

    maskedWriteUnit.enqueue <> queueBeforeMaskWrite.deq
    queueBeforeMaskWrite.enq.valid := writeSelect.orR
    queueBeforeMaskWrite.enq.bits  := Mux1H(writeSelect, allVrfWriteAfterCheck)

    vrf.write <> maskedWriteUnit.dequeue
    readBeforeMaskedWrite <> maskedWriteUnit.vrfReadRequest

    // 更新v0
    v0Update.valid       := vrf.write.valid && vrf.write.bits.vd === 0.U
    v0Update.bits.data   := vrf.write.bits.data
    v0Update.bits.offset := vrf.write.bits.offset
    v0Update.bits.mask   := vrf.write.bits.mask
  }

  {
    askMask.maskSelect    := Mux1H(maskControlReqSelect, maskControlVec.map(_.group))
    askMask.maskSelectSew := Mux1H(maskControlReqSelect, maskControlVec.map(_.sew))
    askMask.slide         := Mux1H(maskControlReqSelect, maskControlVec.map(_.slide))
    maskControlDataDeq    := slotMaskRequestVec.zipWithIndex.map { case (req, index) =>
      val slotIndex      = slotControl(index).laneRequest.instructionIndex
      val hitMaskControl = VecInit(maskControlVec.map(c => c.index === slotIndex && c.controlValid)).asUInt
      val dataValid      = Mux1H(hitMaskControl, maskControlVec.map(_.dataValid))
      val data           = Mux1H(hitMaskControl, maskControlVec.map(_.maskData))
      val group          = Mux1H(hitMaskControl, maskControlVec.map(_.group))
      val sameGroup      = group === req.bits
      dontTouch(sameGroup)
      val hitShifter: Bool = if (index == 0) false.B else slotEnqueueFire(index - 1)
      val maskRequestFire = req.valid && dataValid && !hitShifter
      maskRequestFireOH(index) := maskRequestFire
      maskDataVec(index)       := data
      maskAnd(maskRequestFire, hitMaskControl).asUInt
    }.reduce(_ | _)
  }

  // package a control logic for incoming instruction.
  val entranceControl: InstructionControlRecord = Wire(new InstructionControlRecord(parameter))

  /** for mask logic, the granularity is bitwise. */
  val maskLogicCompleted: Bool =
    laneRequest.bits.decodeResult(Decoder.maskLogic) &&
      (laneIndex ## 0.U(parameter.datapathWidthBits.W) >= csrInterface.vl)

  entranceControl.laneRequest         := laneRequest.bits
  // TODO: in scalar core, raise illegal instruction exception when vstart is nonzero.
  //   see [[https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#37-vector-start-index-csr-vstart]]
  //   "Such implementations are permitted to raise an illegal instruction exception
  //   when attempting to execute a vector arithmetic instruction when vstart is nonzero."
  entranceControl.executeIndex        := 0.U
  entranceControl.instructionFinished :=
    // vl is too small, don't need to use this lane.
    (((laneIndex ## 0.U(
      parameter.dataPathByteBits.W
    )) >> csrInterface.vSew).asUInt >= csrInterface.vl || maskLogicCompleted) &&
      // for 'nr' type instructions, they will need another complete signal.
      !(laneRequest.bits.decodeResult(Decoder.nr) || laneRequest.bits.lsWholeReg)
  // indicate if this is the mask type.
  entranceControl.mask.valid          := false.B
  // assign mask from [[V]]
  entranceControl.mask.bits           := DontCare
  // mask used for VRF write in this group.
  entranceControl.vrfWriteMask        := 0.U

  // calculate last group
  val lastElementIndex: UInt = (csrInterface.vl - csrInterface.vl.orR)(parameter.vlMaxBits - 2, 0)
  val requestVSew1H:    UInt = UIntToOH(csrInterface.vSew)

  val dataPathScaleBit: Int = log2Ceil(parameter.datapathWidth / parameter.eLen)

  /** For an instruction, the last group is not executed by all lanes, here is the last group of the instruction xxxxx
    * xxx xx -> vsew = 0 xxxxxx xxx x -> vsew = 1 xxxxxxx xxx -> vsew = 2
    */
  val lastGroupForInstruction: UInt = Mux1H(
    requestVSew1H(2, 0),
    Seq(
      lastElementIndex(parameter.vlMaxBits - 2, parameter.laneNumberBits + 2 + dataPathScaleBit),
      lastElementIndex(parameter.vlMaxBits - 2, parameter.laneNumberBits + 1 + dataPathScaleBit),
      lastElementIndex(parameter.vlMaxBits - 2, parameter.laneNumberBits + dataPathScaleBit)
    )
  )

  /** Which lane the last element is in. */
  val lastLaneIndex: UInt = Mux1H(
    requestVSew1H(2, 0),
    Seq(
      lastElementIndex(parameter.laneNumberBits + 2 - 1 + dataPathScaleBit, 2 + dataPathScaleBit),
      lastElementIndex(parameter.laneNumberBits + 1 - 1 + dataPathScaleBit, 1 + dataPathScaleBit),
      lastElementIndex(parameter.laneNumberBits - 1 + dataPathScaleBit, dataPathScaleBit)
    )
  )

  /** The relative position of the last lane determines the processing of the last group. */
  val lanePositionLargerThanEndLane: Bool = laneIndex > lastLaneIndex
  val isEndLane:                     Bool = laneIndex === lastLaneIndex
  val lastGroupForLane:              UInt = lastGroupForInstruction - lanePositionLargerThanEndLane

  val requestIsSlideDown: Bool = laneRequest.bits.decodeResult(Decoder.maskPipeUop) === BitPat("b0010?")
  val requestIsSlide1:    Bool = laneRequest.bits.decodeResult(Decoder.maskPipeUop) === BitPat("b001?0")
  val slideSize:          UInt = Mux(requestIsSlide1, 1.U, laneRequest.bits.readFromScalar)
  // for slide down
  // a: slide sown size
  // b: dLen element size
  // a % b + vl
  val slideExecuteSizeVec = Seq(1, 2, 4).map { eByte =>
    val dSize         = parameter.dByte / eByte
    val dSizeLg       = log2Ceil(dSize)
    val slideSizeTail = slideSize & Fill(dSizeLg, true.B)
    val slideElement  = slideSizeTail + laneRequest.bits.csrInterface.vl
    (slideElement >> dSizeLg).asUInt - !changeUIntSize(slideElement, dSizeLg).orR
  }
  val slideExecuteSize    = Mux1H(
    requestVSew1H(2, 0),
    slideExecuteSizeVec
  )

  // last group for mask logic type
  /** xxx xxx xxxxx head body tail
    */
  val vlTail: UInt = csrInterface.vl(parameter.datapathWidthBits - 1, 0)
  val vlBody: UInt =
    csrInterface.vl(parameter.datapathWidthBits + parameter.laneNumberBits - 1, parameter.datapathWidthBits)
  val vlHead: UInt = csrInterface.vl(parameter.vlMaxBits - 1, parameter.datapathWidthBits + parameter.laneNumberBits)
  val lastGroupMask = scanRightOr(UIntToOH(vlTail)) >> 1
  val dataPathMisaligned: Bool = vlTail.orR
  val maskeDataGroup = (vlHead ## vlBody) - !dataPathMisaligned
  val lastLaneIndexForMaskLogic:  UInt = maskeDataGroup(parameter.laneNumberBits - 1, 0)
  val isLastLaneForMaskLogic:     Bool = lastLaneIndexForMaskLogic === laneIndex
  val lastGroupCountForMaskLogic: UInt = (maskeDataGroup >> parameter.laneNumberBits).asUInt -
    ((vlBody.orR || dataPathMisaligned) && (laneIndex > lastLaneIndexForMaskLogic))
  val vlTailWidth:                Int  = log2Ceil(parameter.datapathWidth / 8)
  val misalignedForOther:         Bool = Mux1H(
    requestVSew1H(2, 0),
    Seq(
      csrInterface.vl(vlTailWidth - 1, 0).orR,
      csrInterface.vl(vlTailWidth - 2, 0).orR,
      if (vlTailWidth - 3 >= 0) csrInterface.vl(vlTailWidth - 3, 0).orR else false.B
    )
  )

  entranceControl.lastGroupForInstruction := Mux(
    laneRequest.bits.decodeResult(Decoder.maskLogic),
    lastGroupCountForMaskLogic,
    Mux(requestIsSlideDown, slideExecuteSize, lastGroupForLane)
  )

  entranceControl.isLastLaneForInstruction := Mux(
    laneRequest.bits.decodeResult(Decoder.maskLogic),
    isLastLaneForMaskLogic && dataPathMisaligned,
    isEndLane && misalignedForOther
  )

  entranceControl.additionalRW :=
    (laneRequest.bits.decodeResult(Decoder.crossRead) || laneRequest.bits.decodeResult(Decoder.crossWrite)) &&
      lanePositionLargerThanEndLane && !lastLaneIndex.andR && csrInterface.vl.orR

  // slot needs to be moved, try to shifter and stall pipe
  val slotFree = slotOccupied.zipWithIndex.foldLeft(false.B) { case (pre, (current, index)) =>
    slotShiftValid(index) := pre || !current
    pre || !current
  }

  Seq.tabulate(parameter.chainingSize) { slotIndex =>
    val enqueueReady: Bool = Wire(Bool())
    val enqueueValid: Bool = Wire(Bool())
    val enqueueFire:  Bool = enqueueReady && enqueueValid
    // enqueue from lane request
    if (slotIndex == parameter.chainingSize - 1) {
      enqueueValid := laneRequest.valid
      enqueueReady := slotShiftValid(slotIndex)
      when(enqueueFire) {
        slotControl(slotIndex)       := entranceControl
        maskGroupCountVec(slotIndex) := 0.U(parameter.maskGroupSizeBits.W)
        maskIndexVec(slotIndex)      := 0.U(log2Ceil(parameter.maskGroupWidth).W)
      }
    } else {
      // shifter for slot
      enqueueValid := slotCanShift(slotIndex + 1) && slotOccupied(slotIndex + 1)
      enqueueReady := slotShiftValid(slotIndex)
      when(enqueueFire) {
        slotControl(slotIndex)       := slotControl(slotIndex + 1)
        maskGroupCountVec(slotIndex) := maskGroupCountVec(slotIndex + 1)
        maskIndexVec(slotIndex)      := maskIndexVec(slotIndex + 1)
      }
    }
    slotEnqueueFire(slotIndex) := enqueueFire
  }

  val slotDequeueFire: Seq[Bool] = (slotCanShift.head && slotOccupied.head) +: slotEnqueueFire
  Seq.tabulate(parameter.chainingSize) { slotIndex =>
    when(slotEnqueueFire(slotIndex) ^ slotDequeueFire(slotIndex)) {
      slotOccupied(slotIndex) := slotEnqueueFire(slotIndex)
    }
  }
  slot0EnqueueFire := slotEnqueueFire.head

  // handshake
  // @todo @Clo91eaf lane can take request from Sequencer
  laneRequest.ready := slotFree

  val writeCount = laneRequest.bits.writeCount
  val instructionFinishAndNotReportByTop: Bool =
    entranceControl.instructionFinished && !laneRequest.bits.decodeResult(Decoder.readOnly) && (writeCount === 0.U)
  val needWaitCrossWrite:                 Bool = laneRequest.bits.decodeResult(Decoder.crossWrite) && csrInterface.vl.orR
  // normal instruction, LSU instruction will be report to VRF.
  vrf.instructionWriteReport.valid                       := laneRequest.bits.issueInst && (!instructionFinishAndNotReportByTop || needWaitCrossWrite)
  vrf.instructionWriteReport.bits.instIndex              := laneRequest.bits.instructionIndex
  vrf.instructionWriteReport.bits.vd.bits                := laneRequest.bits.vd
  vrf.instructionWriteReport.bits.vd.valid               := !laneRequest.bits.decodeResult(
    Decoder.targetRd
  ) || (laneRequest.bits.loadStore && !laneRequest.bits.store)
  vrf.instructionWriteReport.bits.vs2                    := laneRequest.bits.vs2
  vrf.instructionWriteReport.bits.vs1.bits               := laneRequest.bits.vs1
  vrf.instructionWriteReport.bits.vs1.valid              := laneRequest.bits.decodeResult(Decoder.vtype)
  vrf.instructionWriteReport.bits.indexType              := laneRequest.valid && laneRequest.bits.loadStore
  // TODO: move ma to [[V]]
  vrf.instructionWriteReport.bits.ma                     := laneRequest.bits.ma
  vrf.instructionWriteReport.bits.onlyRead               := laneRequest.bits.decodeResult(Decoder.popCount)
  // for mask unit
  vrf.instructionWriteReport.bits.slow                   := laneRequest.bits.decodeResult(Decoder.special)
  vrf.instructionWriteReport.bits.oooWrite               := laneRequest.bits.decodeResult(Decoder.maskPipeUop) === BitPat("b001??")
  vrf.instructionWriteReport.bits.ls                     := laneRequest.bits.loadStore
  vrf.instructionWriteReport.bits.st                     := laneRequest.bits.store
  vrf.instructionWriteReport.bits.crossWrite             := laneRequest.bits.decodeResult(Decoder.crossWrite)
  vrf.instructionWriteReport.bits.crossRead              := laneRequest.bits.decodeResult(Decoder.crossRead)
  vrf.instructionWriteReport.bits.gather16               := laneRequest.bits.decodeResult(Decoder.gather16)
  vrf.instructionWriteReport.bits.gather                 := laneRequest.bits.decodeResult(Decoder.gather) &&
    laneRequest.bits.decodeResult(Decoder.vtype)
  // init state
  vrf.instructionWriteReport.bits.state.stFinish         := !laneRequest.bits.loadStore
  // load need wait for write queue clear in lsu write queue
  vrf.instructionWriteReport.bits.state.wWriteQueueClear := !(laneRequest.bits.loadStore && !laneRequest.bits.store)
  vrf.instructionWriteReport.bits.state.wLaneLastReport  := !laneRequest.valid
  vrf.instructionWriteReport.bits.state.wTopLastReport   := !laneRequest.bits.decodeResult(Decoder.maskUnit)
  vrf.instructionWriteReport.bits.state.wLaneClear       := false.B

  val elementSizeForOneRegister: Int  = parameter.vLen / parameter.datapathWidth / parameter.laneNumber
  val nrMask:                    UInt = VecInit(Seq.tabulate(8) { i =>
    Fill(elementSizeForOneRegister, laneRequest.bits.segment < i.U)
  }).asUInt
  // writeCount
  val lastWriteOH:               UInt = scanLeftOr(UIntToOH(writeCount)(parameter.vrfParam.elementSize - 1, 0))

  // segment ls type
  val segmentLS:   Bool = laneRequest.bits.loadStore && laneRequest.bits.segment.orR && !laneRequest.bits.lsWholeReg
  // 0 -> 1, 1 -> 2, 2 -> 4, 4 -> 8
  val mul:         UInt = Mux(csrInterface.vlmul(2), 0.U, csrInterface.vlmul(1, 0))
  val mul1H:       UInt = UIntToOH(mul)
  val seg1H:       UInt = UIntToOH(laneRequest.bits.segment)
  val segmentMask: UInt =
    calculateSegmentWriteMask(parameter.datapathWidth, parameter.laneNumber, elementSizeForOneRegister)(
      seg1H,
      mul1H,
      lastWriteOH
    )

  val selectMask: UInt = Mux(
    segmentLS,
    segmentMask,
    Mux(
      laneRequest.bits.decodeResult(Decoder.nr) || laneRequest.bits.lsWholeReg,
      nrMask,
      lastWriteOH
    )
  )

  vrf.instructionWriteReport.bits.elementMask := selectMask

  instructionFinishInSlot := (~instructionValid).asUInt & instructionValidNext

  val emptyInstValid: Bool = RegNext(laneRequest.bits.issueInst && !vrf.instructionWriteReport.valid, false.B)
  val emptyInstCount: UInt = RegNext(indexToOH(laneRequest.bits.instructionIndex, parameter.chainingSize))
  val emptyReport:    UInt = maskAnd(emptyInstValid, emptyInstCount).asUInt

  // clear record by instructionFinished
  vrf.instructionLastReport                 := instructionFinishInSlot
  vrf.lsuLastReport                         := lsuLastReport
  vrf.loadDataInLSUWriteQueue               := loadDataInLSUWriteQueue
  vrf.dataInLane                            := instructionValid
  instructionFinished                       := vrf.vrfSlotRelease | emptyReport
  writeReadyForLsu                          := vrf.writeReadyForLsu
  vrfReadyToStore                           := vrf.vrfReadyToStore
  tokenManager.crossWrite2Reports.zipWithIndex.foreach { case (rpt, rptIndex) =>
    rpt.valid := slots.head.maskStage.get.crossWritePort2Enq(rptIndex).fire
    rpt.bits  := slots.head.maskStage.get.crossWritePort2Enq(rptIndex).bits.instructionIndex
  }
  tokenManager.crossWrite4Reports.zipWithIndex.foreach { case (rpt, rptIndex) =>
    rpt.valid := slots.head.maskStage.get.crossWritePort4Enq(rptIndex).fire
    rpt.bits  := slots.head.maskStage.get.crossWritePort4Enq(rptIndex).bits.instructionIndex
  }
  tokenManager.maskStageToken               := slots.head.maskStage.get.token
  slots.head.maskStage.get.instructionValid := tokenManager.instructionValid
  // todo: add mask unit write token
  tokenManager.responseReport.valid         := maskUnitRequest.fire
  tokenManager.responseReport.bits          := maskUnitRequest.bits.index
  // todo: delete feedback token
  tokenManager.responseFeedbackReport.valid := vrfWriteChannel.fire && writeFromMask
  tokenManager.responseFeedbackReport.bits  := vrfWriteChannel.bits.instructionIndex
  val instInSlot: UInt = slotControl
    .zip(slotOccupied)
    .map { case (slotState, occupied) =>
      Mux(
        occupied,
        indexToOH(slotState.laneRequest.instructionIndex, parameter.chainingSize),
        0.U
      )
    }
    .reduce(_ | _)
  instructionValid := tokenManager.instructionValid | instInSlot

  // slot write
  tokenManager.slotWriteReport.zipWithIndex.foreach { case (rpt, rptIndex) =>
    // All masks are also removed here
    rpt.valid := afterCheckDequeueFire(rptIndex)
    rpt.bits  := allVrfWriteAfterCheck(rptIndex).instructionIndex
  }

  tokenManager.writePipeEnqReport.valid := queueBeforeMaskWrite.enq.fire
  tokenManager.writePipeEnqReport.bits  := queueBeforeMaskWrite.enq.bits.instructionIndex

  tokenManager.writePipeDeqReport.valid := vrf.write.fire
  tokenManager.writePipeDeqReport.bits  := vrf.write.bits.instructionIndex

  tokenManager.topWriteEnq.valid := vrfWriteChannel.fire
  tokenManager.topWriteEnq.bits  := vrfWriteChannel.bits.instructionIndex

  tokenManager.topWriteDeq.valid := afterCheckDequeueFire(parameter.chainingSize)
  tokenManager.topWriteDeq.bits  := allVrfWriteAfterCheck(parameter.chainingSize).instructionIndex

  tokenManager.maskUnitLastReport := lsuLastReport
  tokenManager.laneIndex          := laneIndex

  tokenManager.instNeedWaitWrite.valid := laneRequest.fire && laneRequest.bits.decodeResult(Decoder.writeCount)
  tokenManager.instNeedWaitWrite.bits  := laneRequest.bits.instructionIndex
  tokenManager.writeCountForToken <> writeCountForToken

  layer.block(layers.Verification) {
    val probeWire = Wire(new LaneProbe(parameter))
    define(laneProbe, ProbeValue(probeWire))
    slots.foreach { slot =>
      slots.map { slot: Slot =>
        probeWire.slots(slot.index).stage0EnqueueReady             := slot.stage0.enqueue.ready
        probeWire.slots(slot.index).stage0EnqueueValid             := slot.stage0.enqueue.valid
        probeWire.slots(slot.index).changingMaskSet                := slot.record.mask.valid || !slot.record.laneRequest.mask
        probeWire.slots(slot.index).slotActive                     := slotActive(slot.index)
        probeWire.slots(slot.index).slotOccupied                   := slotOccupied(slot.index)
        probeWire.slots(slot.index).pipeFinish                     := !slotOccupied(slot.index)
        probeWire.slots(slot.index).slotShiftValid                 := slotShiftValid(slot.index)
        probeWire.slots(slot.index).decodeResultIsCrossReadOrWrite := slot.decodeResult(Decoder.crossRead) || slot
          .decodeResult(Decoder.crossWrite)
        probeWire.slots(slot.index).decodeResultIsScheduler        := slot.decodeResult(Decoder.scheduler)
        probeWire.slots(slot.index).executionUnitVfuRequestReady   := slot.executionUnit.vfuRequest.ready
        probeWire.slots(slot.index).executionUnitVfuRequestValid   := slot.executionUnit.vfuRequest.valid
        probeWire.slots(slot.index).stage3VrfWriteReady            := slot.stage3.vrfWriteRequest.ready
        probeWire.slots(slot.index).stage3VrfWriteValid            := slot.stage3.vrfWriteRequest.valid
        probeWire.slots(slot.index).writeQueueEnq                  := slot.stage3.vrfWriteRequest.fire
        probeWire.slots(slot.index).writeTag                       := slot.stage3.vrfWriteRequest.bits.instructionIndex
        probeWire.slots(slot.index).writeMask                      := slot.stage3.vrfWriteRequest.bits.mask

      }
      // probes

    }
    // probe wire
    probeWire.laneRequestStall    := laneRequest.valid && !laneRequest.ready
    probeWire.lastSlotOccupied    := slotOccupied.last
    probeWire.instructionFinished := instructionFinished
    probeWire.instructionValid    := vrf.instructionValid
    probeWire.crossWriteProbe.zip(writeBusPort2).foreach { case (pb, port) =>
      pb.valid          := port.deq.fire
      pb.bits.writeTag  := port.deq.bits.instructionIndex
      pb.bits.writeMask := port.deq.bits.mask
    }
    probeWire.vrfProbe            := probe.read(vrf.vrfProbe)
  }

}
