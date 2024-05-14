// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{Definition, Instance, Instantiate, instantiable, public}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.probe.{Probe, ProbeValue, define}
import chisel3.properties.{AnyClassType, Class, ClassType, Path, Property}
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle
import org.chipsalliance.t1.rtl.decoder.Decoder
import org.chipsalliance.t1.rtl.lane._
import org.chipsalliance.t1.rtl.vrf.{RamType, VRF, VRFParam, VRFProbe}

@instantiable
class LaneOM extends Class {
  @public
  val vfus = IO(Output(Property[Seq[AnyClassType]]()))
  @public
  val vfusIn = IO(Input(Property[Seq[AnyClassType]]()))
  vfus := vfusIn
}

class LaneSlotProbe extends Bundle {
  val stage0EnqueueReady: Bool = Bool()
  val stage0EnqueueValid: Bool = Bool()
  val changingMaskSet: Bool = Bool()
  val slotActive: Bool = Bool()
  val slotOccupied: Bool = Bool()
  val pipeFinish: Bool = Bool()
  val slotShiftValid: Bool = Bool()
  val decodeResultIsCrossReadOrWrite: Bool = Bool()
  val decodeResultIsScheduler: Bool = Bool()
  val executionUnitVfuRequestReady: Bool = Bool()
  val executionUnitVfuRequestValid: Bool = Bool()
  val stage3VrfWriteReady: Bool = Bool()
  val stage3VrfWriteValid: Bool = Bool()
  // val probeStage1: Bool = Bool()
}

class LaneProbe(slotsSize: Int) extends Bundle {
  val slots = Vec(slotsSize, new LaneSlotProbe)
  val laneRequestValid: Bool = Bool()
  val laneRequestReady: Bool = Bool()
  val lastSlotOccupied: Bool = Bool()
  val vrfInstructionWriteReportReady: Bool = Bool()
  val instructionFinished: UInt = UInt(slotsSize.W)
}

object LaneParameter {
  implicit def rwP: upickle.default.ReadWriter[LaneParameter] = upickle.default.macroRW
}

/** Parameter for [[Lane]].
  * @param vLen VLEN
  * @param dataPathWidth width of data path, can be 32 or 64, decides the memory bandwidth.
  * @param laneNumber how many lanes in the vector processor
  * @param chainingSize how many instructions can be chained
  * @param crossLaneVRFWriteEscapeQueueSize The cross lane write queue designed for latch data from cross lane interconnection,
  *                                         if VRF backpress on ring, this queue will be used for latch data from ring,
  *                                         in case of additional latency from ring.
  *                                         TODO: cover the queue full.
  */
case class LaneParameter(
                          vLen:                             Int,
                          datapathWidth:                    Int,
                          laneNumber:                       Int,
                          chainingSize:                     Int,
                          crossLaneVRFWriteEscapeQueueSize: Int,
                          fpuEnable:                        Boolean,
                          portFactor:                       Int,
                          vrfRamType:                       RamType,
                          vfuInstantiateParameter: VFUInstantiateParameter)
    extends SerializableModuleParameter {

  /** 1 in MSB for instruction order. */
  val instructionIndexBits: Int = log2Ceil(chainingSize) + 1

  /** maximum of lmul, defined in spec. */
  val lmulMax: Int = 8

  /** see [[T1Parameter.sewMin]] */
  val sewMin: Int = 8

  /** The datapath is divided into groups based on SEW.
    * The current implement is calculating them in multiple cycles.
    * TODO: we must parallelize it as much as possible since it highly affects the computation bandwidth.
    */
  val dataPathByteWidth: Int = datapathWidth / sewMin

  /** maximum of vl. */
  val vlMax: Int = vLen * lmulMax / sewMin

  /** width of [[vlMax]]
    * `+1` is for vl being 0 to vlMax(not vlMax - 1).
    * we use less than for comparing, rather than less equal.
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

  /** +1 in MSB for comparing to next group number.
    * we don't use `vl-1` is because if the mask of last group is all 0, it should be jumped through.
    * so we directly compare the next group number with the MSB of `vl`.
    */
  val groupNumberBits: Int = log2Ceil(groupNumberMax + 1)

  /** Half of [[datapathWidth]], this is used in the cross-lane accessing logic. */
  val halfDatapathWidth: Int = datapathWidth / 2

  /** uarch TODO: instantiate logic, add to each slot
    * logic, add, shift, multiple, divide, other
    *
    * TODO: use Seq().size to calculate
    */
  val executeUnitNum: Int = 6

  /** hardware width of [[laneNumber]]. */
  val laneNumberBits: Int = 1 max log2Ceil(laneNumber)

  /** hardware width of [[datapathWidth]]. */
  val datapathWidthBits: Int = log2Ceil(datapathWidth)

  /** see [[T1Parameter.maskGroupWidth]] */
  val maskGroupWidth: Int = datapathWidth

  /** see [[T1Parameter.maskGroupSize]] */
  val maskGroupSize: Int = vLen / maskGroupWidth

  /** hardware width of [[maskGroupSize]]. */
  val maskGroupSizeBits: Int = log2Ceil(maskGroupSize)

  /** Size of the queue for storing execution information
    * todo: Determined by the longest execution unit
    * */
  val executionQueueSize: Int = 4

  /** Parameter for [[VRF]] */
  def vrfParam: VRFParam = VRFParam(vLen, laneNumber, datapathWidth, chainingSize, portFactor, vrfRamType)
}

/** Instantiate [[Lane]] from [[T1]],
  * - [[VRF]] is designed for store the vector register of the processor.
  * - datapath units: [[MaskedLogic]], [[LaneAdder]], [[LaneShifter]], [[LaneMul]], [[LaneDiv]], [[OtherUnit]]
  */
@instantiable
class Lane(val parameter: LaneParameter) extends Module with SerializableModule[LaneParameter] {
  val omInstance: Instance[LaneOM] = Instantiate(new LaneOM)
  val omType: ClassType = omInstance.toDefinition.getClassType
  @public
  val om: Property[ClassType] = IO(Output(Property[omType.Type]()))
  om := omInstance.getPropertyReference

  /** laneIndex is a IO constant for D/I and physical implementations. */
  @public
  val laneIndex: UInt = IO(Input(UInt(parameter.laneNumberBits.W)))
  // constant parameter for physical implementations.
  dontTouch(laneIndex)

  /** Cross lane VRF Read Interface.
    * only used for `narrow` an `widen`
    * TODO: benchmark the usecase for tuning the Ring Bus width.
    *       find a real world case for using `narrow` and `widen` aggressively.
    */
  @public
  val readBusPort: Vec[RingPort[ReadBusData]] = IO(Vec(2, new RingPort(new ReadBusData(parameter))))

  /** VRF Write Interface.
    * only used for `narrow` an `widen`
    * TODO: benchmark the usecase for tuning the Ring Bus width.
    *       find a real world case for using `narrow` and `widen` aggressively.
    */
  @public
  val writeBusPort: Vec[RingPort[WriteBusData]] = IO(Vec(2, new RingPort(new WriteBusData(parameter))))

  /** request from [[T1.decode]] to [[Lane]]. */
  @public
  val laneRequest: DecoupledIO[LaneRequest] = IO(Flipped(Decoupled(new LaneRequest(parameter))))

  /** CSR Interface.
    * TODO: merge to [[laneRequest]]
    */
  @public
  val csrInterface: CSRInterface = IO(Input(new CSRInterface(parameter.vlMaxBits)))

  /** response to [[T1.lsu]] or mask unit in [[T1]] */
  @public
  val laneResponse: ValidIO[LaneResponse] = IO(Valid(new LaneResponse(parameter)))

  /** feedback from [[T1]] to [[Lane]] for [[laneResponse]] */
  @public
  val laneResponseFeedback: ValidIO[LaneResponseFeedback] = IO(Flipped(Valid(new LaneResponseFeedback(parameter))))

  /** for LSU and V accessing lane, this is not a part of ring, but a direct connection.
    * TODO: learn AXI channel, reuse [[vrfReadAddressChannel]] and [[vrfWriteChannel]].
    */
  @public
  val vrfReadAddressChannel: DecoupledIO[VRFReadRequest] = IO(
    Flipped(
      Decoupled(
        new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
      )
    )
  )
  @public
  val vrfReadDataChannel: UInt = IO(Output(UInt(parameter.datapathWidth.W)))
  @public
  val vrfWriteChannel: DecoupledIO[VRFWriteRequest] = IO(
    Flipped(
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

  /** for each instruction in the slot, response to top when instruction is finished in this lane. */
  @public
  val instructionFinished: UInt = IO(Output(UInt(parameter.chainingSize.W)))

  /** V0 update in the lane should also update [[T1.v0]] */
  @public
  val v0Update: ValidIO[V0Update] = IO(Valid(new V0Update(parameter)))

  /** input of mask data */
  @public
  val maskInput: UInt = IO(Input(UInt(parameter.maskGroupWidth.W)))

  /** select which mask group. */
  @public
  val maskSelect: UInt = IO(Output(UInt(parameter.maskGroupSizeBits.W)))

  /** The sew of instruction which is requesting for mask. */
  @public
  val maskSelectSew: UInt = IO(Output(UInt(2.W)))

  /** from [[T1.lsu]] to [[Lane.vrf]], indicate it's the load store is finished, used for chaining.
    * because of load store index EEW, is complicated for lane to calculate whether LSU is finished.
    * let LSU directly tell each lane it is finished.
    */
  @public
  val lsuLastReport: UInt = IO(Input(UInt(parameter.chainingSize.W)))

  /** If lsu changes the mask group, you need to tell vrf */
  @public
  val lsuMaskGroupChange: UInt = IO(Input(UInt(parameter.chainingSize.W)))

  /** for RaW, VRF should wait for buffer to be empty. */
  @public
  val loadDataInLSUWriteQueue: UInt = IO(Input(UInt(parameter.chainingSize.W)))

  /** for RaW, VRF should wait for cross write bus to be empty. */
  @public
  val dataInCrossBus: UInt = IO(Input(UInt(parameter.chainingSize.W)))

  /** How many dataPath will writ by instruction in this lane */
  @public
  val writeCount: UInt =
    IO(Input(UInt((parameter.vlMaxBits - log2Ceil(parameter.laneNumber) - log2Ceil(parameter.dataPathByteWidth)).W)))
  @public
  val writeQueueValid: Bool = IO(Output(Bool()))
  @public
  val writeReadyForLsu: Bool = IO(Output(Bool()))
  @public
  val vrfReadyToStore: Bool = IO(Output(Bool()))
  @public
  val crossWriteDataInSlot: UInt = IO(Output(UInt(parameter.chainingSize.W)))

  @public
  val probe: LaneProbe = IO(Output(Probe(new LaneProbe(parameter.chainingSize))))
  val probeWire: LaneProbe = Wire(new LaneProbe(parameter.chainingSize))
  define(probe, ProbeValue(probeWire))
  @public
  val vrfProbe = IO(Output(Probe(new VRFProbe(
    parameter.vrfParam.regNumBits,
    parameter.vrfOffsetBits,
    parameter.instructionIndexBits,
    parameter.datapathWidth
  ))))

  // TODO: remove
  dontTouch(writeBusPort)

  /** VRF instantces. */
  val vrf: Instance[VRF] = Instantiate(new VRF(parameter.vrfParam))
  define(vrfProbe, vrf.probe)

  /** TODO: review later
    */
  val maskGroupedOrR: UInt = VecInit(
    maskInput.asBools
      .grouped(parameter.dataPathByteWidth)
      .toSeq
      .map(
        VecInit(_).asUInt.orR
      )
  ).asUInt

  val fullMask: UInt = (-1.S(parameter.datapathWidth.W)).asUInt

  /** the slot is occupied by instruction */
  val slotOccupied: Vec[Bool] = RegInit(VecInit(Seq.fill(parameter.chainingSize)(false.B)))

  /** mask group count for slot */
  val maskGroupCountVec: Vec[UInt] =
    RegInit(VecInit(Seq.fill(parameter.chainingSize)(0.U(parameter.maskGroupSizeBits.W))))

  /** mask index for slot */
  val maskIndexVec: Vec[UInt] =
    RegInit(VecInit(Seq.fill(parameter.chainingSize)(0.U(log2Ceil(parameter.maskGroupWidth).W))))

  /** pipe state for slot */
  val pipeFinishVec: Vec[Bool] = RegInit(VecInit(Seq.fill(parameter.chainingSize)(false.B)))

  /** the find first one index register in this lane. */
  val ffoIndexReg: UInt = RegInit(0.U(log2Ceil(parameter.vLen / 8).W))

  /** result of reduce instruction. */
  val reduceResult: UInt = RegInit(0.U(parameter.datapathWidth.W))

  /** arbiter for VRF write
    * 1 for [[vrfWriteChannel]]
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
  val topWriteQueue: DecoupledIO[VRFWriteRequest] = Queue(vrfWriteChannel, 1, flow = true)
  vrfWriteArbiter(parameter.chainingSize).valid := topWriteQueue.valid
  vrfWriteArbiter(parameter.chainingSize).bits := topWriteQueue.bits
  topWriteQueue.ready := vrfWriteArbiter(parameter.chainingSize).ready

  /** for each slot, assert when it is asking [[T1]] to change mask */
  val slotMaskRequestVec: Vec[ValidIO[UInt]] = Wire(
    Vec(
      parameter.chainingSize,
      Valid(UInt(parameter.maskGroupSizeBits.W))
    )
  )

  /** which slot wins the arbitration for requesting mask. */
  val maskRequestFireOH: UInt = Wire(UInt(parameter.chainingSize.W))

  /** FSM control for each slot.
    * if index == 0,
    * - slot can support write v0 in mask type, see [[Decoder.maskDestination]] [[Decoder.maskSource]] [[Decoder.maskLogic]]
    * - cross lane read/write.
    * - and all other instructions.
    * TODO: clear [[Decoder.maskDestination]] [[Decoder.maskSource]] [[Decoder.maskLogic]] out from `index == 0`?
    *       we may only need cross lane read/write in `index == 0`
    * the index != 0 slot is used for all other instructions.
    */
  val slotControl: Vec[InstructionControlRecord] =
    RegInit(
      VecInit(
        Seq.fill(parameter.chainingSize)(0.U.asTypeOf(new InstructionControlRecord(parameter)))
      )
    )

  /** VRF read request for each slot,
    * 3 is for [[source1]] [[source2]] [[source3]]
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

  /** VRF read result for each slot,
    * 3 is for [[source1]] [[source2]] [[source3]]
    */
  val vrfReadResult: Vec[Vec[UInt]] = Wire(
    Vec(
      parameter.chainingSize,
      Vec(3, UInt(parameter.datapathWidth.W))
    )
  )

  // 3 * slot + 2 cross read
  val readCheckRequestVec: Vec[VRFReadRequest] = Wire(Vec(parameter.chainingSize * 3 + 2,
    new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
  ))

  val readCheckResult: Vec[Bool] = Wire(Vec(parameter.chainingSize * 3 + 2, Bool()))

  /** signal used for prohibiting slots to access VRF.
    * a slot will become inactive when:
    * 1. cross lane read/write is not finished
    * 2. lanes doesn't win mask request
    */
  val slotActive: Vec[Bool] = Wire(Vec(parameter.chainingSize, Bool()))

  /** When the slot wants to move,
    * you need to stall the pipeline first and wait for the pipeline to be cleared.
    */
  val slotShiftValid: Vec[Bool] = Wire(Vec(parameter.chainingSize, Bool()))

  /** The slots start to shift in these rules:
    * - instruction can only enqueue to the last slot.
    * - all slots can only shift at the same time which means:
    *   if one slot is finished earlier -> 1101,
    *   it will wait for the first slot to finish -> 1100,
    *   and then take two cycles to move to xx11.
    */
  val slotCanShift: Vec[Bool] = Wire(Vec(parameter.chainingSize, Bool()))

  /** Which data group is waiting for the result of the cross-lane read */
  val readBusDequeueGroup: UInt = Wire(UInt(parameter.groupNumberBits.W))

  /** enqueue valid for execution unit */
  val executeEnqueueValid: Vec[Bool] = Wire(Vec(parameter.chainingSize, Bool()))

  /** request from slot to vfu. */
  val requestVec: Vec[SlotRequestToVFU] = Wire(Vec(parameter.chainingSize, new SlotRequestToVFU(parameter)))

  /** response from vfu to slot. */
  val responseVec: Vec[ValidIO[VFUResponseToSlot]] = Wire(Vec(parameter.chainingSize, Valid(new VFUResponseToSlot(parameter))))

  /** enqueue fire signal for execution unit */
  val executeEnqueueFire: Vec[Bool] = Wire(Vec(parameter.chainingSize, Bool()))

  val executeOccupied: Vec[Bool] = Wire(Vec(parameter.vfuInstantiateParameter.genVec.size, Bool()))
  dontTouch(executeOccupied)

  val VFUNotClear:           Bool = Wire(Bool())

  val slot0EnqueueFire: Bool = Wire(Bool())

  /** assert when a instruction is finished in the slot. */
  val instructionFinishedVec: Vec[UInt] = Wire(Vec(parameter.chainingSize, UInt(parameter.chainingSize.W)))

  /** any cross lane write data in slot */
  val crossWriteDataInSlotVec: Vec[UInt] = Wire(Vec(parameter.chainingSize, UInt(parameter.chainingSize.W)))

  /** assert when a instruction will not use mask unit */
  val instructionUnrelatedMaskUnitVec: Vec[UInt] = Wire(Vec(parameter.chainingSize, UInt(parameter.chainingSize.W)))

  /** queue for cross lane writing.
    * TODO: benchmark the size of the queue
    */
  val crossLaneWriteQueue: Seq[Queue[VRFWriteRequest]] = Seq.tabulate(2)(i => Module(
    new Queue(
      new VRFWriteRequest(
        parameter.vrfParam.regNumBits,
        parameter.vrfOffsetBits,
        parameter.instructionIndexBits,
        parameter.datapathWidth
      ),
      parameter.crossLaneVRFWriteEscapeQueueSize,
      pipe = true
    )
  ))
  val maskedWriteUnit: Instance[MaskedWrite] = Instantiate(new MaskedWrite(parameter))
  val dataInPipeQueue: UInt = Wire(UInt(parameter.chainingSize.W))
  slotControl.zipWithIndex.foreach {
    case (record, index) =>
      val decodeResult: DecodeBundle = record.laneRequest.decodeResult
      val isLastSlot: Boolean = index == 0

      /** We will ignore the effect of mask since:
        * [[Decoder.crossRead]]: We need to read data to another lane
        * [[Decoder.scheduler]]: We need to synchronize with [[T1]] every group
        * [[record.laneRequest.loadStore]]: We need to read data to lsu every group
        */
      val alwaysNextGroup: Bool = decodeResult(Decoder.crossRead) || decodeResult(Decoder.nr) ||
        !decodeResult(Decoder.scheduler) || record.laneRequest.loadStore

      // mask not use for mask element
      val maskNotMaskedElement = !record.laneRequest.mask ||
        record.laneRequest.decodeResult(Decoder.maskSource) ||
        record.laneRequest.decodeResult(Decoder.maskLogic)

      /** onehot value of SEW. */
      val vSew1H: UInt = UIntToOH(record.csr.vSew)(2, 0)

      /** if asserted, the element won't be executed.
        * adc: vm = 0; madc: vm = 0 -> s0 + s1 + c, vm = 1 -> s0 + s1
        */
      val skipEnable: Bool = record.laneRequest.mask &&
        !record.laneRequest.decodeResult(Decoder.maskSource) &&
        !record.laneRequest.decodeResult(Decoder.maskLogic) &&
        !alwaysNextGroup

      // mask for current mask group
      val maskForMaskGroup: UInt = Mux(
        skipEnable,
        record.mask.bits,
        (-1.S(parameter.datapathWidth.W)).asUInt
      )

      // register for s0 enqueue, it will move with the slot
      // 'maskGroupCountVec' 'maskIndexVec' 'pipeFinishVec'

      // pipe clear
      val pipeClear: Bool = Wire(Bool())

      if (isLastSlot) {
        slotActive(index) := slotOccupied(index) && !pipeFinishVec(index)
      } else {
        slotActive(index) := slotOccupied(index) && !pipeFinishVec(index) && !slotShiftValid(index) &&
          !(decodeResult(Decoder.crossRead) || decodeResult(Decoder.crossWrite) || decodeResult(Decoder.widenReduce)) &&
          decodeResult(Decoder.scheduler)
      }

      if(isLastSlot) {
        slotCanShift(index) := pipeClear && pipeFinishVec(index)
      } else {
        slotCanShift(index) := pipeClear
      }

      val laneState: LaneState = Wire(new LaneState(parameter))
      val stage0: Instance[LaneStage0] = Instantiate(new LaneStage0(parameter, isLastSlot))
      val stage1: Instance[LaneStage1] = Instantiate(new LaneStage1(parameter, isLastSlot))
      val stage2: Instance[LaneStage2] = Instantiate(new LaneStage2(parameter, isLastSlot))
      val executionUnit: Instance[LaneExecutionBridge] = Instantiate(new LaneExecutionBridge(parameter, isLastSlot, index))
      val stage3: Instance[LaneStage3] = Instantiate(new LaneStage3(parameter, isLastSlot))

      // slot state
      laneState.vSew1H := vSew1H
      laneState.loadStore := record.laneRequest.loadStore
      laneState.laneIndex := laneIndex
      laneState.decodeResult := record.laneRequest.decodeResult
      laneState.lastGroupForInstruction := record.lastGroupForInstruction
      laneState.isLastLaneForInstruction := record.isLastLaneForInstruction
      laneState.instructionFinished := record.instructionFinished
      laneState.csr := record.csr
      laneState.maskType := record.laneRequest.mask
      laneState.maskNotMaskedElement := !record.laneRequest.mask ||
        record.laneRequest.decodeResult(Decoder.maskSource) ||
        record.laneRequest.decodeResult(Decoder.maskLogic)
      laneState.mask := record.mask
      laneState.vs1 := record.laneRequest.vs1
      laneState.vs2 := record.laneRequest.vs2
      laneState.vd := record.laneRequest.vd
      laneState.instructionIndex := record.laneRequest.instructionIndex
      laneState.maskForMaskGroup := maskForMaskGroup
      laneState.ffoByOtherLanes := record.ffoByOtherLanes
      laneState.additionalRead := record.additionalRead
      laneState.skipRead := record.laneRequest.decodeResult(Decoder.other) &&
        (record.laneRequest.decodeResult(Decoder.uop) === 9.U)
      laneState.newInstruction.foreach(_ := slot0EnqueueFire)

      stage0.enqueue.valid := slotActive(index) && (record.mask.valid || !record.laneRequest.mask)
      stage0.enqueue.bits.maskIndex := maskIndexVec(index)
      stage0.enqueue.bits.maskForMaskGroup := record.mask.bits
      stage0.enqueue.bits.maskGroupCount := maskGroupCountVec(index)
      stage0.state := laneState

      // update lane state
      when(stage0.enqueue.fire) {
        maskGroupCountVec(index) := stage0.updateLaneState.maskGroupCount
        // todo: handle all elements in first group are masked
        maskIndexVec(index) := stage0.updateLaneState.maskIndex
        when(stage0.updateLaneState.outOfExecutionRange) {
          pipeFinishVec(index) := true.B
        }
      }

      // update mask todo: handle maskRequestFireOH
      slotMaskRequestVec(index).valid :=
        record.laneRequest.mask &&
          ((stage0.enqueue.fire && stage0.updateLaneState.maskExhausted) || !record.mask.valid)
      slotMaskRequestVec(index).bits := stage0.updateLaneState.maskGroupCount
      // There are new masks
      val maskUpdateFire: Bool = slotMaskRequestVec(index).valid && maskRequestFireOH(index)
      // The old mask is used up
      val maskFailure: Bool = stage0.updateLaneState.maskExhausted && stage0.enqueue.fire
      // update mask register
      when(maskUpdateFire) {
        record.mask.bits := maskInput
      }
      when(maskUpdateFire ^ maskFailure) {
        record.mask.valid := maskUpdateFire
      }

      val instructionIndex1H: UInt = UIntToOH(
        record.laneRequest.instructionIndex(parameter.instructionIndexBits - 2, 0)
      )
      instructionFinishedVec(index) := 0.U
      instructionUnrelatedMaskUnitVec(index) :=
        Mux(decodeResult(Decoder.maskUnit) && decodeResult(Decoder.readOnly), 0.U, instructionIndex1H)
      val dataInWritePipe: Bool =
        ohCheck(maskedWriteUnit.maskedWrite1H, record.laneRequest.instructionIndex, parameter.chainingSize) |
          ohCheck(dataInPipeQueue, record.laneRequest.instructionIndex, parameter.chainingSize)
      when(slotOccupied(index) && pipeClear && pipeFinishVec(index) && !dataInWritePipe) {
        slotOccupied(index) := false.B
        instructionFinishedVec(index) := instructionIndex1H
      }

      // stage 1: read stage
      stage1.enqueue.valid := stage0.dequeue.valid
      stage0.dequeue.ready := stage1.enqueue.ready
      stage1.enqueue.bits.groupCounter := stage0.dequeue.bits.groupCounter
      stage1.enqueue.bits.maskForMaskInput := stage0.dequeue.bits.maskForMaskInput
      stage1.enqueue.bits.boundaryMaskCorrection := stage0.dequeue.bits.boundaryMaskCorrection
      stage1.enqueue.bits.sSendResponse.zip(stage0.dequeue.bits.sSendResponse).foreach { case (sink, source) =>
        sink := source
      }
      stage1.dequeue.bits.readBusDequeueGroup.foreach(data => readBusDequeueGroup := data)

      // todo: connect state from stage0
      stage1.enqueue.bits.elements.foreach { case (k ,d) =>
        laneState.elements.get(k).foreach(stateData => d := stateData)
      }
      stage1.readFromScalar := record.laneRequest.readFromScalar
      vrfReadRequest(index).zip(stage1.vrfReadRequest).foreach{ case (sink, source) => sink <> source }
      vrfReadResult(index).zip(stage1.vrfReadResult).foreach{ case (source, sink) => sink := source }
      // 3: read vs1 vs2 vd
      // 2: cross read lsb & msb
      val checkSize = if (isLastSlot) 5 else 3
      Seq.tabulate(checkSize){ portIndex =>
        // parameter.chainingSize - index: slot 0 need 5 port, so reverse connection
        readCheckRequestVec((parameter.chainingSize - index - 1) * 3 + portIndex) := stage1.vrfCheckRequest(portIndex)
        stage1.checkResult(portIndex) := readCheckResult((parameter.chainingSize - index - 1) * 3 + portIndex)
      }
      // connect cross read bus
      if(isLastSlot) {
        val tokenSize = parameter.crossLaneVRFWriteEscapeQueueSize
        readBusPort.zipWithIndex.foreach {case (readPort, portIndex) =>
          // tx
          val tokenReg = RegInit(0.U(log2Ceil(tokenSize + 1).W))
          val tokenReady: Bool = tokenReg =/= tokenSize.U
          stage1.readBusRequest.get(portIndex).ready := tokenReady
          readPort.deq.valid := stage1.readBusRequest.get(portIndex).valid && tokenReady
          readPort.deq.bits := stage1.readBusRequest.get(portIndex).bits
          val tokenUpdate = Mux(readPort.deq.valid, 1.U, -1.S(tokenReg.getWidth.W).asUInt)
          when(readPort.deq.valid ^ readPort.deqRelease) {
            tokenReg := tokenReg + tokenUpdate
          }
          // rx
          // rx queue
          val queue = Module(new Queue(chiselTypeOf(readPort.deq.bits), tokenSize, pipe=true))
          queue.io.enq.valid := readPort.enq.valid
          queue.io.enq.bits := readPort.enq.bits
          readPort.enqRelease := queue.io.deq.fire
          assert(queue.io.enq.ready || !readPort.enq.valid)
          // dequeue to cross read unit
          stage1.readBusDequeue.get(portIndex) <> queue.io.deq
        }

        // cross write
        writeBusPort.zipWithIndex.foreach {case (writePort, portIndex) =>
          val tokenReg = RegInit(0.U(log2Ceil(tokenSize + 1).W))
          val tokenReady: Bool = tokenReg =/= tokenSize.U
          writePort.deq.valid := stage3.crossWritePort.get(portIndex).valid && tokenReady
          writePort.deq.bits := stage3.crossWritePort.get(portIndex).bits
          stage3.crossWritePort.get(portIndex).ready := tokenReady

          // update token
          val tokenUpdate = Mux(writePort.deq.valid, 1.U, -1.S(tokenReg.getWidth.W).asUInt)
          when(writePort.deq.valid ^ writePort.deqRelease) {
            tokenReg := tokenReg + tokenUpdate
          }
        }
      }

      crossWriteDataInSlotVec(index) := Mux(
        (pipeClear & !slotOccupied(index)) || !decodeResult(Decoder.crossWrite),
        0.U,
        indexToOH(record.laneRequest.instructionIndex, parameter.chainingSize)
      )

      stage2.enqueue.valid := stage1.dequeue.valid && executionUnit.enqueue.ready
      stage1.dequeue.ready := stage2.enqueue.ready && executionUnit.enqueue.ready
      executionUnit.enqueue.valid := stage1.dequeue.valid && stage2.enqueue.ready

      // todo: connect state from stage1
      stage2.enqueue.bits.elements.foreach { case (k ,d) =>
        laneState.elements.get(k).foreach(stateData => d := stateData)
      }
      stage2.enqueue.bits.groupCounter := stage1.dequeue.bits.groupCounter
      stage2.enqueue.bits.mask := stage1.dequeue.bits.mask
      stage2.enqueue.bits.maskForFilter := stage1.dequeue.bits.maskForFilter
      stage2.enqueue.bits.src := stage1.dequeue.bits.src
      stage2.enqueue.bits.sSendResponse.zip(stage1.dequeue.bits.sSendResponse).foreach { case (sink, source) =>
        sink := source
      }
      stage2.enqueue.bits.bordersForMaskLogic := executionUnit.enqueue.bits.bordersForMaskLogic

      // todo: connect state from stage1
      executionUnit.enqueue.bits.elements.foreach { case (k ,d) =>
        laneState.elements.get(k).foreach(stateData => d := stateData)
      }
      executionUnit.enqueue.bits.src := stage1.dequeue.bits.src
      executionUnit.enqueue.bits.bordersForMaskLogic :=
        (stage1.dequeue.bits.groupCounter === record.lastGroupForInstruction && record.isLastLaneForInstruction)
      executionUnit.enqueue.bits.mask := stage1.dequeue.bits.mask
      executionUnit.enqueue.bits.maskForFilter := stage1.dequeue.bits.maskForFilter
      executionUnit.enqueue.bits.groupCounter := stage1.dequeue.bits.groupCounter
      executionUnit.enqueue.bits.sSendResponse.zip(stage1.dequeue.bits.sSendResponse).foreach { case (sink, source) =>
        sink := source
      }
      executionUnit.enqueue.bits.crossReadSource.zip(stage1.dequeue.bits.crossReadSource).foreach { case (sink, source) =>
        sink := source
      }

      executionUnit.ffoByOtherLanes := record.ffoByOtherLanes
      executionUnit.selfCompleted := record.selfCompleted

      // executionUnit <> vfu
      requestVec(index) := executionUnit.vfuRequest.bits
      executeEnqueueValid(index) := executionUnit.vfuRequest.valid
      executionUnit.vfuRequest.ready := executeEnqueueFire(index)
      executionUnit.dataResponse := responseVec(index)

      when(executionUnit.dequeue.valid)(assert(stage2.dequeue.valid))
      stage3.enqueue.valid := executionUnit.dequeue.valid
      executionUnit.dequeue.ready := stage3.enqueue.ready
      stage2.dequeue.ready := executionUnit.dequeue.fire

      if (!isLastSlot) {
        stage3.enqueue.bits := DontCare
      }
      // todo: connect state from stage2
      stage3.enqueue.bits.elements.foreach { case (k ,d) =>
        laneState.elements.get(k).foreach(stateData => d := stateData)
      }
      stage3.enqueue.bits.groupCounter := stage2.dequeue.bits.groupCounter
      stage3.enqueue.bits.mask := stage2.dequeue.bits.mask
      if (isLastSlot) {
        stage3.enqueue.bits.sSendResponse := stage2.dequeue.bits.sSendResponse.get
        stage3.enqueue.bits.ffoSuccess := executionUnit.dequeue.bits.ffoSuccess.get
        stage3.enqueue.bits.fpReduceValid.zip(executionUnit.dequeue.bits.fpReduceValid).foreach {
          case (sink, source) => sink := source
        }
      }
      stage3.enqueue.bits.data := executionUnit.dequeue.bits.data
      stage3.enqueue.bits.pipeData := stage2.dequeue.bits.pipeData.getOrElse(DontCare)
      stage3.enqueue.bits.ffoIndex := executionUnit.dequeue.bits.ffoIndex
      executionUnit.dequeue.bits.crossWriteData.foreach(data => stage3.enqueue.bits.crossWriteData := data)
      stage2.dequeue.bits.sSendResponse.foreach(_ => stage3.enqueue.bits.sSendResponse := _)
      executionUnit.dequeue.bits.ffoSuccess.foreach(_ => stage3.enqueue.bits.ffoSuccess := _)

      if (isLastSlot){
        when(laneResponseFeedback.valid && slotOccupied(index)) {
          when(laneResponseFeedback.bits.complete) {
            record.ffoByOtherLanes := true.B
          }
        }
        when(stage3.enqueue.fire) {
          executionUnit.dequeue.bits.ffoSuccess.foreach(record.selfCompleted := _)
          // This group found means the next group ended early
          record.ffoByOtherLanes := record.ffoByOtherLanes || record.selfCompleted
        }

        laneResponse <> stage3.laneResponse.get
        stage3.laneResponseFeedback.get <> laneResponseFeedback
      }

      // --- stage 3 end & stage 4 start ---
      // vrfWriteQueue try to write vrf
      vrfWriteArbiter(index).valid := stage3.vrfWriteRequest.valid
      vrfWriteArbiter(index).bits := stage3.vrfWriteRequest.bits
      stage3.vrfWriteRequest.ready := vrfWriteArbiter(index).ready

      pipeClear := !Seq(stage0.stageValid, stage1.stageValid, stage2.stageValid, stage3.stageValid, dataInWritePipe).reduce(_ || _)

      // probes
      probeWire.slots(index).stage0EnqueueReady := stage0.enqueue.ready
      probeWire.slots(index).stage0EnqueueValid := stage0.enqueue.valid
      probeWire.slots(index).changingMaskSet := record.mask.valid || !record.laneRequest.mask
      probeWire.slots(index).slotActive := slotActive(index)
      probeWire.slots(index).slotOccupied := slotOccupied(index)
      probeWire.slots(index).pipeFinish := pipeFinishVec(index)
      probeWire.slots(index).slotShiftValid := slotShiftValid(index)
      probeWire.slots(index).decodeResultIsCrossReadOrWrite := decodeResult(Decoder.crossRead) || decodeResult(Decoder.crossWrite)
      probeWire.slots(index).decodeResultIsScheduler := decodeResult(Decoder.scheduler)
      probeWire.slots(index).executionUnitVfuRequestReady := executionUnit.vfuRequest.ready
      probeWire.slots(index).executionUnitVfuRequestValid := executionUnit.vfuRequest.valid
      probeWire.slots(index).stage3VrfWriteReady := stage3.vrfWriteRequest.ready
      probeWire.slots(index).stage3VrfWriteValid := stage3.vrfWriteRequest.valid
      // probeWire.slots(index).probeStage1 := ???
  }


  // cross write bus <> write queue
  crossLaneWriteQueue.zipWithIndex.foreach {case (queue, index) =>
    val port = writeBusPort(index)
    // ((counter << 1) >> parameter.vrfParam.vrfOffsetBits).low(3)
    val registerIncreaseBase = parameter.vrfParam.vrfOffsetBits - 1
    queue.io.enq.valid := port.enq.valid
    queue.io.enq.bits.vd :=
      // 3: 8 reg => log(2, 8)
      slotControl.head.laneRequest.vd + port.enq.bits.counter(registerIncreaseBase + 3 - 1, registerIncreaseBase)
    queue.io.enq.bits.offset := port.enq.bits.counter ## index.U(1.W)
    queue.io.enq.bits.data := port.enq.bits.data
    queue.io.enq.bits.last := DontCare
    queue.io.enq.bits.instructionIndex := port.enq.bits.instructionIndex
    queue.io.enq.bits.mask := FillInterleaved(2, port.enq.bits.mask)
    assert(queue.io.enq.ready || !port.enq.valid)
    port.enqRelease := queue.io.deq.fire
  }
  // convert data types


  // VFU
  // TODO: reuse logic, adder, multiplier datapath
  val decodeResultVec: Seq[DecodeBundle] = slotControl.map(_.laneRequest.decodeResult)

  val vfus: Seq[Instance[VFUModule]] = instantiateVFU(parameter.vfuInstantiateParameter)(
    requestVec,
    executeEnqueueValid,
    decodeResultVec,
    executeEnqueueFire,
    responseVec,
    executeOccupied,
    VFUNotClear
  )
  omInstance.vfusIn := Property(vfus.map(_.om.asAnyClassType))

  // It’s been a long time since I selected it. Need pipe
  val queueBeforeMaskWrite: Queue[VRFWriteRequest] =
    Module(new Queue(chiselTypeOf(maskedWriteUnit.enqueue.bits), entries = 1, pipe = true))
  dataInPipeQueue := Mux(
    queueBeforeMaskWrite.io.deq.valid,
    indexToOH(queueBeforeMaskWrite.io.deq.bits.instructionIndex, parameter.chainingSize),
    0.U
  )

  // 处理 rf
  {
    val readBeforeMaskedWrite: DecoupledIO[VRFReadRequest] = Wire(Decoupled(
      new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
    ))
    val readPortVec: Seq[DecoupledIO[VRFReadRequest]] = readBeforeMaskedWrite +: vrfReadRequest.flatten :+ vrfReadAddressChannel
    val readResultVec: Seq[UInt] = maskedWriteUnit.vrfReadResult +: vrfReadResult.flatten :+ vrfReadDataChannel
    parameter.vrfParam.connectTree.zipWithIndex.foreach {
      case (connectSource, vrfPortIndex) =>
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
    val allVrfWrite: Seq[DecoupledIO[VRFWriteRequest]] = vrfWriteArbiter ++ crossLaneWriteQueue.map(_.io.deq)
    // check all write
    vrf.writeCheck.zip(allVrfWrite).foreach {case (check, write) =>
      check.vd := write.bits.vd
      check.offset := write.bits.offset
      check.instructionIndex := write.bits.instructionIndex
    }
    val checkResult = vrf.writeAllow.asUInt

    vrf.readCheck.zip(readCheckRequestVec).foreach{case (sink, source) => sink := source}
    readCheckResult.zip(vrf.readCheckResult).foreach{case (sink, source) => sink := source}

    // Arbiter
    val writeSelect: UInt = ffo(checkResult & VecInit(allVrfWrite.map(_.valid)).asUInt)
    allVrfWrite.zipWithIndex.foreach{ case (p, i) => p.ready := writeSelect(i) && queueBeforeMaskWrite.io.enq.ready }

    maskedWriteUnit.enqueue <> queueBeforeMaskWrite.io.deq
    queueBeforeMaskWrite.io.enq.valid := writeSelect.orR
    queueBeforeMaskWrite.io.enq.bits := Mux1H(writeSelect, allVrfWrite.map(_.bits))

    vrf.write <> maskedWriteUnit.dequeue
    readBeforeMaskedWrite <> maskedWriteUnit.vrfReadRequest
    writeQueueValid := maskedWriteUnit.enqueue.valid || maskedWriteUnit.dequeue.valid ||
      topWriteQueue.valid || vrfWriteChannel.valid || queueBeforeMaskWrite.io.enq.valid ||
      crossLaneWriteQueue.map(q => q.io.deq.valid || q.io.enq.valid).reduce(_ || _)

    //更新v0
    v0Update.valid := vrf.write.valid && vrf.write.bits.vd === 0.U
    v0Update.bits.data := vrf.write.bits.data
    v0Update.bits.offset := vrf.write.bits.offset
    v0Update.bits.mask := vrf.write.bits.mask
  }

  {
    // 处理mask的请求
    val maskSelectArbitrator = ffo(
      VecInit(slotMaskRequestVec.map(_.valid)).asUInt ##
        (laneRequest.valid && (laneRequest.bits.mask || laneRequest.bits.decodeResult(Decoder.maskSource)))
    )
    maskRequestFireOH := maskSelectArbitrator(parameter.chainingSize, 1)
    maskSelect := Mux1H(
      maskSelectArbitrator,
      0.U.asTypeOf(slotMaskRequestVec.head.bits) +: slotMaskRequestVec.map(_.bits)
    )
    maskSelectSew := Mux1H(
      maskSelectArbitrator,
      csrInterface.vSew +: slotControl.map(_.csr.vSew)
    )
  }

  // package a control logic for incoming instruction.
  val entranceControl: InstructionControlRecord = Wire(new InstructionControlRecord(parameter))

  /** for mask logic, the granularity is bitwise. */
  val maskLogicCompleted: Bool =
    laneRequest.bits.decodeResult(Decoder.maskLogic) &&
      (laneIndex ## 0.U(parameter.datapathWidthBits.W) >= csrInterface.vl)
  // latch CSR from V
  entranceControl.csr := csrInterface

  entranceControl.laneRequest := laneRequest.bits
  // TODO: in scalar core, raise illegal instruction exception when vstart is nonzero.
  //   see [[https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#37-vector-start-index-csr-vstart]]
  //   "Such implementations are permitted to raise an illegal instruction exception
  //   when attempting to execute a vector arithmetic instruction when vstart is nonzero."
  entranceControl.executeIndex := 0.U
  entranceControl.ffoByOtherLanes := false.B
  entranceControl.selfCompleted := false.B
  entranceControl.instructionFinished :=
    // vl is too small, don't need to use this lane.
    (((laneIndex ## 0.U(2.W)) >> csrInterface.vSew).asUInt >= csrInterface.vl || maskLogicCompleted) &&
      // for 'nr' type instructions, they will need another complete signal.
      !(laneRequest.bits.decodeResult(Decoder.nr) || laneRequest.bits.lsWholeReg)
  // indicate if this is the mask type.
  entranceControl.mask.valid := laneRequest.bits.mask
  // assign mask from [[V]]
  entranceControl.mask.bits := maskInput
  // TODO: remove it.
  entranceControl.maskGroupedOrR := maskGroupedOrR
  // mask used for VRF write in this group.
  entranceControl.vrfWriteMask := 0.U

  // calculate last group
  val lastElementIndex: UInt = (csrInterface.vl - csrInterface.vl.orR)(parameter.vlMaxBits - 2, 0)
  val requestVSew1H:    UInt = UIntToOH(csrInterface.vSew)

  /** For an instruction, the last group is not executed by all lanes,
    * here is the last group of the instruction
    * xxxxx xxx xx -> vsew = 0
    * xxxxxx xxx x -> vsew = 1
    * xxxxxxx xxx  -> vsew = 2
    */
  val lastGroupForInstruction: UInt = Mux1H(
    requestVSew1H(2, 0),
    Seq(
      lastElementIndex(parameter.vlMaxBits - 2, parameter.laneNumberBits + 2),
      lastElementIndex(parameter.vlMaxBits - 2, parameter.laneNumberBits + 1),
      lastElementIndex(parameter.vlMaxBits - 2, parameter.laneNumberBits)
    )
  )

  /** Which lane the last element is in. */
  val lastLaneIndex: UInt = Mux1H(
    requestVSew1H(2, 0),
    Seq(
      lastElementIndex(parameter.laneNumberBits + 2 - 1, 2),
      lastElementIndex(parameter.laneNumberBits + 1 - 1, 1),
      lastElementIndex(parameter.laneNumberBits - 1, 0)
    )
  )

  /** The relative position of the last lane determines the processing of the last group. */
  val lanePositionLargerThanEndLane: Bool = laneIndex > lastLaneIndex
  val isEndLane: Bool = laneIndex === lastLaneIndex
  val lastGroupForLane: UInt = lastGroupForInstruction - lanePositionLargerThanEndLane

  // last group for mask logic type
  /** xxx   xxx     xxxxx
    * head  body    tail
    */
  val vlTail: UInt = csrInterface.vl(parameter.datapathWidthBits - 1, 0)
  val vlBody: UInt =
    csrInterface.vl(parameter.datapathWidthBits + parameter.laneNumberBits - 1, parameter.datapathWidthBits)
  val vlHead: UInt = csrInterface.vl(parameter.vlMaxBits - 1, parameter.datapathWidthBits + parameter.laneNumberBits)
  val lastGroupMask = scanRightOr(UIntToOH(vlTail)) >> 1
  val dataPathMisaligned: Bool = vlTail.orR
  val maskeDataGroup = (vlHead ## vlBody) - !dataPathMisaligned
  val lastLaneIndexForMaskLogic: UInt = maskeDataGroup(parameter.laneNumberBits - 1, 0)
  val isLastLaneForMaskLogic: Bool = lastLaneIndexForMaskLogic === laneIndex
  val lastGroupCountForMaskLogic: UInt = (maskeDataGroup >> parameter.laneNumberBits).asUInt -
    ((vlBody.orR || dataPathMisaligned) && (laneIndex > lastLaneIndexForMaskLogic))
  val misalignedForOther: Bool = Mux1H(
    requestVSew1H(1, 0),
    Seq(
      csrInterface.vl(1, 0).orR,
      csrInterface.vl(0),
    )
  )

  entranceControl.lastGroupForInstruction := Mux(
    laneRequest.bits.decodeResult(Decoder.maskLogic),
    lastGroupCountForMaskLogic,
    lastGroupForLane
  )

  entranceControl.isLastLaneForInstruction := Mux(
    laneRequest.bits.decodeResult(Decoder.maskLogic),
    isLastLaneForMaskLogic && dataPathMisaligned,
    isEndLane && misalignedForOther
  )

  entranceControl.additionalRead :=
    laneRequest.bits.decodeResult(Decoder.crossRead) &&
      lanePositionLargerThanEndLane && !lastLaneIndex.andR && csrInterface.vl.orR

  // slot needs to be moved, try to shifter and stall pipe
  slotShiftValid := VecInit(Seq.range(0, parameter.chainingSize).map { slotIndex =>
    if (slotIndex == 0) false.B else !slotOccupied(slotIndex - 1)
  })


  val slotEnqueueFire: Seq[Bool] = Seq.tabulate(parameter.chainingSize) { slotIndex =>
    val enqueueReady: Bool = Wire(Bool())
    val enqueueValid: Bool = Wire(Bool())
    val enqueueFire: Bool = enqueueReady && enqueueValid
    // enqueue from lane request
    if (slotIndex == parameter.chainingSize - 1) {
      enqueueValid := laneRequest.valid
      enqueueReady := !slotOccupied(slotIndex) && vrf.instructionWriteReport.ready
      when(enqueueFire) {
        slotControl(slotIndex) := entranceControl
        maskGroupCountVec(slotIndex) := 0.U(parameter.maskGroupSizeBits.W)
        maskIndexVec(slotIndex) := 0.U(log2Ceil(parameter.maskGroupWidth).W)
        pipeFinishVec(slotIndex) := false.B
      }
      enqueueFire
    } else {
      // shifter for slot
      enqueueValid := slotCanShift(slotIndex + 1) && slotOccupied(slotIndex + 1)
      enqueueReady := !slotOccupied(slotIndex)
      when(enqueueFire) {
        slotControl(slotIndex) := slotControl(slotIndex + 1)
        maskGroupCountVec(slotIndex) := maskGroupCountVec(slotIndex + 1)
        maskIndexVec(slotIndex) := maskIndexVec(slotIndex + 1)
        pipeFinishVec(slotIndex) := pipeFinishVec(slotIndex + 1)
      }
      enqueueFire
    }
  }

  val slotDequeueFire: Seq[Bool] = (slotCanShift.head && slotOccupied.head) +: slotEnqueueFire
  Seq.tabulate(parameter.chainingSize) { slotIndex =>
    when(slotEnqueueFire(slotIndex) ^ slotDequeueFire(slotIndex)) {
      slotOccupied(slotIndex) := slotEnqueueFire(slotIndex)
    }
  }
  slot0EnqueueFire := slotEnqueueFire.head

  // handshake
  laneRequest.ready := !slotOccupied.last && vrf.instructionWriteReport.ready

  val instructionFinishAndNotReportByTop: Bool =
    entranceControl.instructionFinished && !laneRequest.bits.decodeResult(Decoder.readOnly) && (writeCount === 0.U)
  val needWaitCrossWrite: Bool = laneRequest.bits.decodeResult(Decoder.crossWrite) && csrInterface.vl.orR
  // normal instruction, LSU instruction will be report to VRF.
  vrf.instructionWriteReport.valid := laneRequest.bits.issueInst && (!instructionFinishAndNotReportByTop || needWaitCrossWrite)
  vrf.instructionWriteReport.bits.instIndex := laneRequest.bits.instructionIndex
  vrf.instructionWriteReport.bits.vd.bits := laneRequest.bits.vd
  vrf.instructionWriteReport.bits.vd.valid := !laneRequest.bits.decodeResult(Decoder.targetRd) || (laneRequest.bits.loadStore && !laneRequest.bits.store)
  vrf.instructionWriteReport.bits.vs2 := laneRequest.bits.vs2
  vrf.instructionWriteReport.bits.vs1.bits := laneRequest.bits.vs1
  vrf.instructionWriteReport.bits.vs1.valid := laneRequest.bits.decodeResult(Decoder.vtype)
  vrf.instructionWriteReport.bits.indexType := laneRequest.valid && laneRequest.bits.loadStore
  // TODO: move ma to [[V]]
  vrf.instructionWriteReport.bits.ma := laneRequest.bits.ma
  // for mask unit
  vrf.instructionWriteReport.bits.slow := laneRequest.bits.decodeResult(Decoder.special)
  vrf.instructionWriteReport.bits.ls := laneRequest.bits.loadStore
  vrf.instructionWriteReport.bits.st := laneRequest.bits.store
  vrf.instructionWriteReport.bits.crossWrite := laneRequest.bits.decodeResult(Decoder.crossWrite)
  vrf.instructionWriteReport.bits.crossRead := laneRequest.bits.decodeResult(Decoder.crossRead)
  vrf.instructionWriteReport.bits.stFinish := false.B
  vrf.instructionWriteReport.bits.wWriteQueueClear := false.B
  vrf.instructionWriteReport.bits.wBusClear := false.B
  vrf.instructionWriteReport.bits.wQueueClear := false.B

  val elementSizeForOneRegister: Int = parameter.vLen / parameter.datapathWidth / parameter.laneNumber
  val nrMask: UInt = VecInit(Seq.tabulate(8){ i =>
    Fill(elementSizeForOneRegister, laneRequest.bits.segment < i.U)
  }).asUInt
  // writeCount
  val lastWriteOH: UInt = scanLeftOr(UIntToOH(writeCount)(parameter.vrfParam.elementSize - 1, 0))

  // segment ls type
  val segmentLS: Bool = laneRequest.bits.loadStore && laneRequest.bits.segment.orR && !laneRequest.bits.lsWholeReg
  // 0 -> 1, 1 -> 2, 2 -> 4, 4 -> 8
  val mul: UInt = Mux(csrInterface.vlmul(2), 0.U, csrInterface.vlmul(1, 0))
  val mul1H: UInt = UIntToOH(mul)
  val seg1H: UInt = UIntToOH(laneRequest.bits.segment)
  val segmentMask: UInt =
    calculateSegmentWriteMask(parameter.datapathWidth, parameter.laneNumber, elementSizeForOneRegister)(
      seg1H, mul1H, lastWriteOH
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
  val shifterMask: UInt = (
    ((selectMask ## Fill(32, true.B))
      << laneRequest.bits.vd(2, 0) ## 0.U(log2Ceil(elementSizeForOneRegister).W))
      >> 32).asUInt

  vrf.instructionWriteReport.bits.elementMask := shifterMask

  // clear record by instructionFinished
  vrf.instructionLastReport := lsuLastReport | (instructionFinished & instructionUnrelatedMaskUnitVec.reduce(_ | _))
  vrf.lsuMaskGroupChange := lsuMaskGroupChange
  vrf.loadDataInLSUWriteQueue := loadDataInLSUWriteQueue
  vrf.dataInCrossBus := dataInCrossBus
  vrf.dataInWriteQueue :=
    crossLaneWriteQueue.map(q => Mux(q.io.deq.valid, indexToOH(q.io.deq.bits.instructionIndex, parameter.chainingSize), 0.U)).reduce(_ | _)|
      Mux(topWriteQueue.valid, indexToOH(topWriteQueue.bits.instructionIndex, parameter.chainingSize), 0.U) |
      maskedWriteUnit.maskedWrite1H | dataInPipeQueue
  instructionFinished := instructionFinishedVec.reduce(_ | _)
  crossWriteDataInSlot := crossWriteDataInSlotVec.reduce(_ | _) | dataInPipeQueue | maskedWriteUnit.maskedWrite1H
  writeReadyForLsu := vrf.writeReadyForLsu
  vrfReadyToStore := vrf.vrfReadyToStore

  // probe wire
  probeWire.laneRequestValid := laneRequest.valid
  probeWire.laneRequestReady := laneRequest.ready
  probeWire.lastSlotOccupied := slotOccupied.last
  probeWire.vrfInstructionWriteReportReady := vrf.instructionWriteReport.ready
  probeWire.instructionFinished := instructionFinished
}
