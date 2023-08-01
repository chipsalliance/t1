package v

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleGenerator, SerializableModuleParameter}
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle
import v.TableGenerator.LaneDecodeTable.div

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
                          vfuInstantiateParameter: VFUInstantiateParameter)
    extends SerializableModuleParameter {

  /** 1 in MSB for instruction order. */
  val instructionIndexBits: Int = log2Ceil(chainingSize) + 1

  /** maximum of lmul, defined in spec. */
  val lmulMax: Int = 8

  /** see [[VParameter.sewMin]] */
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
  val laneNumberBits: Int = log2Ceil(laneNumber)

  /** hardware width of [[datapathWidth]]. */
  val datapathWidthBits: Int = log2Ceil(datapathWidth)

  /** see [[VParameter.maskGroupWidth]] */
  val maskGroupWidth: Int = datapathWidth

  /** see [[VParameter.maskGroupSize]] */
  val maskGroupSize: Int = vLen / maskGroupWidth

  /** hardware width of [[maskGroupSize]]. */
  val maskGroupSizeBits: Int = log2Ceil(maskGroupSize)

  /** Size of the queue for storing execution information
    * todo: Determined by the longest execution unit
    * */
  val executionQueueSize: Int = 2

  /** Parameter for [[VRF]] */
  def vrfParam: VRFParam = VRFParam(vLen, laneNumber, datapathWidth, chainingSize)

  /** Parameter for [[OtherUnit]]. */
  def otherUnitParam: OtherUnitParam = OtherUnitParam(datapathWidth, vlMaxBits, groupNumberBits, laneNumberBits, dataPathByteWidth)

}

/** Instantiate [[Lane]] from [[V]],
  * - [[VRF]] is designed for store the vector register of the processor.
  * - datapath units: [[MaskedLogic]], [[LaneAdder]], [[LaneShifter]], [[LaneMul]], [[LaneDiv]], [[OtherUnit]]
  */
class Lane(val parameter: LaneParameter) extends Module with SerializableModule[LaneParameter] {

  /** laneIndex is a IO constant for D/I and physical implementations. */
  val laneIndex: UInt = IO(Input(UInt(parameter.laneNumberBits.W)))
  // constant parameter for physical implementations.
  dontTouch(laneIndex)

  /** Cross lane VRF Read Interface.
    * only used for `narrow` an `widen`
    * TODO: benchmark the usecase for tuning the Ring Bus width.
    *       find a real world case for using `narrow` and `widen` aggressively.
    */
  val readBusPort: RingPort[ReadBusData] = IO(new RingPort(new ReadBusData(parameter)))

  /** VRF Write Interface.
    * only used for `narrow` an `widen`
    * TODO: benchmark the usecase for tuning the Ring Bus width.
    *       find a real world case for using `narrow` and `widen` aggressively.
    */
  val writeBusPort: RingPort[WriteBusData] = IO(new RingPort(new WriteBusData(parameter)))

  /** request from [[V.decode]] to [[Lane]]. */
  val laneRequest: DecoupledIO[LaneRequest] = IO(Flipped(Decoupled(new LaneRequest(parameter))))

  /** CSR Interface.
    * TODO: merge to [[laneRequest]]
    */
  val csrInterface: CSRInterface = IO(Input(new CSRInterface(parameter.vlMaxBits)))

  /** response to [[V.lsu]] or mask unit in [[V]] */
  val laneResponse: ValidIO[LaneResponse] = IO(Valid(new LaneResponse(parameter)))

  /** feedback from [[V]] to [[Lane]] for [[laneResponse]] */
  val laneResponseFeedback: ValidIO[LaneResponseFeedback] = IO(Flipped(Valid(new LaneResponseFeedback(parameter))))

  /** for LSU and V accessing lane, this is not a part of ring, but a direct connection.
    * TODO: learn AXI channel, reuse [[vrfReadAddressChannel]] and [[vrfWriteChannel]].
    */
  val vrfReadAddressChannel: DecoupledIO[VRFReadRequest] = IO(
    Flipped(
      Decoupled(
        new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
      )
    )
  )
  val vrfReadDataChannel: UInt = IO(Output(UInt(parameter.datapathWidth.W)))
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
  val instructionFinished: UInt = IO(Output(UInt(parameter.chainingSize.W)))

  /** V0 update in the lane should also update [[V.v0]] */
  val v0Update: ValidIO[V0Update] = IO(Valid(new V0Update(parameter)))

  /** input of mask data */
  val maskInput: UInt = IO(Input(UInt(parameter.maskGroupWidth.W)))

  /** select which mask group. */
  val maskSelect: UInt = IO(Output(UInt(parameter.maskGroupSizeBits.W)))

  /** The sew of instruction which is requesting for mask. */
  val maskSelectSew: UInt = IO(Output(UInt(2.W)))

  /** from [[V.lsu]] to [[Lane.vrf]], indicate it's the load store is finished, used for chaining.
    * because of load store index EEW, is complicated for lane to calculate whether LSU is finished.
    * let LSU directly tell each lane it is finished.
    */
  val lsuLastReport: UInt = IO(Input(UInt(parameter.chainingSize.W)))

  /** If lsu changes the mask group, you need to tell vrf */
  val lsuMaskGroupChange: UInt = IO(Input(UInt(parameter.chainingSize.W)))

  /** for RaW, VRF should wait for buffer to be empty. */
  val lsuVRFWriteBufferClear: Bool = IO(Input(Bool()))

  /** VRF will record information for each instructions,
    * we use `last` to indicate if the write is finished.
    * for some mask instructions, e.g. `reduce`, it will only write single lane,
    * thus we need to use this signal to release the record from [[VRF]]
    */
  val maskUnitFlushVrf: Bool = IO(Input(Bool()))

  // TODO: remove
  dontTouch(writeBusPort)

  /** VRF instantces. */
  val vrf: VRF = Module(new VRF(parameter.vrfParam))

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
  val vrfWriteArbiter: Vec[ValidIO[VRFWriteRequest]] = Wire(
    Vec(
      parameter.chainingSize + 1,
      Valid(
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
  vrfWriteArbiter(parameter.chainingSize).bits := vrfWriteChannel.bits

  /** writing to VRF
    * 1 for [[vrfWriteChannel]]
    * 1 for [[crossLaneWriteQueue]]
    */
  val vrfWriteFire: UInt = Wire(UInt((parameter.chainingSize + 2).W))
  vrfWriteChannel.ready := vrfWriteFire(parameter.chainingSize)

  /** for each slot, assert when it is asking [[V]] to change mask */
  val slotMaskRequestVec: Vec[ValidIO[UInt]] = Wire(
    Vec(
      parameter.chainingSize,
      Valid(UInt(parameter.maskGroupSizeBits.W))
    )
  )

  /** which slot wins the arbitration for requesting mask. */
  val maskRequestFireOH: UInt = Wire(UInt(parameter.chainingSize.W))

  /** read from VRF, it will go to ring in the next cycle.
    * from [[vrfReadRequest]](1)
    */
  val crossReadLSBOut: UInt = RegInit(0.U(parameter.datapathWidth.W))

  /** read from VRF, it will go to ring in the next cycle.
    * from [[vrfReadRequest]](2)
    */
  val crossReadMSBOut: UInt = RegInit(0.U(parameter.datapathWidth.W))

  /** read from Bus, it will try to write to VRF in the next cycle. */
  val crossReadLSBIn: UInt = RegInit(0.U(parameter.datapathWidth.W))

  /** read from Bus, it will try to write to VRF in the next cycle. */
  val crossReadMSBIn: UInt = RegInit(0.U(parameter.datapathWidth.W))

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

  /** cross lane reading port from [[readBusPort]]
    * if [[ReadBusData.sinkIndex]] matches the index of this lane, dequeue from ring
    */
  val readBusDequeue: ValidIO[ReadBusData] = Wire(Valid(new ReadBusData(parameter: LaneParameter)))

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

  /** assert when a instruction is finished in the slot. */
  val instructionFinishedVec: Vec[UInt] = Wire(Vec(parameter.chainingSize, UInt(parameter.chainingSize.W)))

  /** ready signal for enqueuing [[readBusPort]] */
  val crossLaneReadReady: Bool = Wire(Bool())

  /** ready signal for enqueuing [[writeBusPort]] */
  val crossLaneWriteReady: Bool = Wire(Bool())

  /** data for enqueuing [[readBusPort]]
    * [[crossLaneRead.valid]] indicate there is a slot try to enqueue [[readBusPort]]
    */
  val crossLaneRead: ValidIO[ReadBusData] = Wire(Valid(new ReadBusData(parameter)))

  /** data for enqueuing [[writeBusPort]]
    * [[crossLaneWrite.valid]] indicate there is a slot try to enqueue [[writeBusPort]]
    */
  val crossLaneWrite: ValidIO[WriteBusData] = Wire(Valid(new WriteBusData(parameter)))

  /** queue for cross lane writing.
    * TODO: benchmark the size of the queue
    */
  val crossLaneWriteQueue: Queue[VRFWriteRequest] = Module(
    new Queue(
      new VRFWriteRequest(
        parameter.vrfParam.regNumBits,
        parameter.vrfOffsetBits,
        parameter.instructionIndexBits,
        parameter.datapathWidth
      ),
      parameter.crossLaneVRFWriteEscapeQueueSize
    )
  )

  slotControl.zipWithIndex.foreach {
    case (record, index) =>
      val decodeResult: DecodeBundle = record.laneRequest.decodeResult
      val isLastSlot: Boolean = index == 0

      /** We will ignore the effect of mask since:
        * [[Decoder.crossRead]]: We need to read data to another lane
        * [[Decoder.scheduler]]: We need to synchronize with [[V]] every group
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
          !(decodeResult(Decoder.crossRead) || decodeResult(Decoder.crossWrite)) &&
          decodeResult(Decoder.scheduler)
      }

      if(isLastSlot) {
        slotCanShift(index) := pipeClear && pipeFinishVec(index)
      } else {
        slotCanShift(index) := pipeClear
      }

      val laneState: LaneState = Wire(new LaneState(parameter))
      val stage0: LaneStage0 = Module(new LaneStage0(parameter, isLastSlot))
      val stage1 = Module(new LaneStage1(parameter, isLastSlot))
      val stage2 = Module(new LaneStage2(parameter, isLastSlot))
      val executionUnit: LaneExecutionBridge = Module(new LaneExecutionBridge(parameter, isLastSlot))
      val stage3 = Module(new LaneStage3(parameter, isLastSlot))

      // slot state
      laneState.vSew1H := vSew1H
      laneState.loadStore := record.laneRequest.loadStore
      laneState.laneIndex := laneIndex
      laneState.decodeResult := record.laneRequest.decodeResult
      laneState.lastGroupForInstruction := record.lastGroupForInstruction
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
        stage0.updateLaneState.maskExhausted && record.laneRequest.mask && (stage0.enqueue.fire || !record.mask.valid)
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

      instructionFinishedVec(index) := 0.U
      when(slotOccupied(index) && pipeClear && pipeFinishVec(index)) {
        slotOccupied(index) := false.B
        instructionFinishedVec(index) := UIntToOH(
          record.laneRequest.instructionIndex(parameter.instructionIndexBits - 2, 0)
        )
      }

      // stage 1: read stage
      stage1.enqueue.valid := stage0.dequeue.valid
      stage0.dequeue.ready := stage1.enqueue.ready
      stage1.enqueue.bits.groupCounter := stage0.dequeue.bits.groupCounter
      stage1.enqueue.bits.mask := stage0.dequeue.bits.mask
      stage1.enqueue.bits.sSendResponse.zip(stage0.dequeue.bits.sSendResponse).foreach { case (sink, source) =>
        sink := source
      }
      stage1.dequeue.bits.readBusDequeueGroup.foreach(data => readBusDequeueGroup := data)

      stage1.state := laneState
      stage1.readFromScalar := record.laneRequest.readFromScalar
      vrfReadRequest(index).zip(stage1.vrfReadRequest).foreach{ case (sink, source) => sink <> source }
      vrfReadResult(index).zip(stage1.vrfReadResult).foreach{ case (source, sink) => sink := source }
      // connect cross read bus
      if(isLastSlot) {
        crossLaneRead.valid := stage1.readBusRequest.get.valid
        crossLaneRead.bits := stage1.readBusRequest.get.bits
        stage1.readBusRequest.get.ready := crossLaneReadReady
        stage1.readBusDequeue.get <> readBusDequeue
      }

      stage2.enqueue.valid := stage1.dequeue.valid && executionUnit.enqueue.ready
      stage1.dequeue.ready := stage2.enqueue.ready && executionUnit.enqueue.ready
      executionUnit.enqueue.valid := stage1.dequeue.valid && stage2.enqueue.ready

      stage2.state := laneState
      stage2.enqueue.bits.groupCounter := stage1.dequeue.bits.groupCounter
      stage2.enqueue.bits.mask := stage1.dequeue.bits.mask
      stage2.enqueue.bits.maskForFilter := stage1.dequeue.bits.maskForFilter
      stage2.enqueue.bits.src := stage1.dequeue.bits.src
      stage2.enqueue.bits.sSendResponse.zip(stage1.dequeue.bits.sSendResponse).foreach { case (sink, source) =>
        sink := source
      }

      executionUnit.state := laneState
      executionUnit.enqueue.bits.src := stage1.dequeue.bits.src
      executionUnit.enqueue.bits.bordersForMaskLogic :=
        (stage1.dequeue.bits.groupCounter === record.lastGroupForInstruction && record.isLastLaneForMaskLogic)
      executionUnit.enqueue.bits.mask := stage1.dequeue.bits.mask
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
      stage3.state := laneState
      stage3.enqueue.bits.groupCounter := stage2.dequeue.bits.groupCounter
      stage3.enqueue.bits.mask := stage2.dequeue.bits.mask
      if (isLastSlot) {
        stage3.enqueue.bits.sSendResponse := stage2.dequeue.bits.sSendResponse.get
        stage3.enqueue.bits.ffoSuccess := executionUnit.dequeue.bits.ffoSuccess.get
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
        crossLaneWrite.valid := stage3.crossWritePort.get.valid
        crossLaneWrite.bits := stage3.crossWritePort.get.bits
        stage3.crossWritePort.get.ready := crossLaneWriteReady

        laneResponse <> stage3.laneResponse.get
        stage3.laneResponseFeedback.get <> laneResponseFeedback
      }

      // --- stage 3 end & stage 4 start ---
      // vrfWriteQueue try to write vrf
      vrfWriteArbiter(index).valid := stage3.vrfWriteRequest.valid
      vrfWriteArbiter(index).bits := stage3.vrfWriteRequest.bits
      stage3.vrfWriteRequest.ready := vrfWriteFire(index)

      pipeClear := !Seq(stage0.stageValid, stage1.stageValid, stage2.stageValid, stage3.stageValid).reduce(_ || _)
  }

  // Read Ring
  /** latch from [[readBusPort]]. */
  val readBusDataReg: ValidIO[ReadBusData] = RegInit(0.U.asTypeOf(Valid(new ReadBusData(parameter))))

  /** peek bits on the bus, checking if it is matched, if match dequeue it. */
  val readBusDequeueMatch = {
    // check the request is to this lane.
    readBusPort.enq.bits.sinkIndex === laneIndex &&
    // because the ring may send unordered transactions, we need the check the counter on the ring.
    // TODO: add one depth escape queue to latch the case that transaction on the ring is not the current groupCounter.
    readBusPort.enq.bits.counter === readBusDequeueGroup
  }
  // when `readBusDequeueMatch`, local lane must be ready.
  readBusDequeue.valid := readBusDequeueMatch && readBusPort.enq.valid
  readBusDequeue.bits := readBusPort.enq.bits
  // ring has higher priority than local data enqueue.
  readBusPort.enq.ready := true.B
  // last connect: by default false
  readBusDataReg.valid := false.B

  // data is latched to [[readBusDataReg]]
  // TODO: merge two when.
  when(readBusPort.enq.valid) {
    when(!readBusDequeueMatch) {
      readBusDataReg.valid := true.B
      readBusDataReg.bits := readBusPort.enq.bits
    }
  }

  // enqueue data to read ring.
  readBusPort.deq.valid := readBusDataReg.valid || crossLaneRead.valid
  // arbitrate between ring data and local data.
  readBusPort.deq.bits := Mux(readBusDataReg.valid, readBusDataReg.bits, crossLaneRead.bits)
  // no forward will enqueue successfully.
  crossLaneReadReady := !readBusDataReg.valid

  // Write Ring
  /** latch from [[writeBusPort]] */
  val writeBusDataReg: ValidIO[WriteBusData] = RegInit(0.U.asTypeOf(Valid(new WriteBusData(parameter))))

  /** peek bits on the bus, checking if it is matched, if match dequeue it.
    * don't need compare counter since unorder write to VRF is allowed.
    * need to block the case if [[crossLaneWriteQueue]] is full.
    */
  val writeBusIndexMatch = writeBusPort.enq.bits.sinkIndex === laneIndex && crossLaneWriteQueue.io.enq.ready
  // ring has higher priority than local data enqueue.
  writeBusPort.enq.ready := true.B
  // last connect: by default false
  writeBusDataReg.valid := false.B
  crossLaneWriteQueue.io.enq.valid := false.B

  // convert data types
  crossLaneWriteQueue.io.enq.bits.vd := slotControl.head.laneRequest.vd + writeBusPort.enq.bits.counter(3, 1)
  crossLaneWriteQueue.io.enq.bits.offset := writeBusPort.enq.bits.counter ## writeBusPort.enq.bits.isTail
  crossLaneWriteQueue.io.enq.bits.data := writeBusPort.enq.bits.data
  crossLaneWriteQueue.io.enq.bits.last := DontCare
  crossLaneWriteQueue.io.enq.bits.instructionIndex := slotControl.head.laneRequest.instructionIndex
  crossLaneWriteQueue.io.enq.bits.mask := FillInterleaved(2, writeBusPort.enq.bits.mask)

  // arbitrate ring data to ring or local.
  when(writeBusPort.enq.valid) {
    when(writeBusIndexMatch) {
      crossLaneWriteQueue.io.enq.valid := true.B
    }.otherwise {
      writeBusDataReg.valid := true.B
      // gate ring when there is no data.
      writeBusDataReg.bits := writeBusPort.enq.bits
    }
  }

  // enqueue data to write ring.
  writeBusPort.deq.valid := writeBusDataReg.valid || crossLaneWrite.valid
  // arbitrate between ring data and local data.
  writeBusPort.deq.bits := Mux(writeBusDataReg.valid, writeBusDataReg.bits, crossLaneWrite.bits)
  // no forward will enqueue successfully.
  crossLaneWriteReady := !writeBusDataReg.valid

  // VFU
  // TODO: reuse logic, adder, multiplier datapath
  {
    val decodeResultVec: Seq[DecodeBundle] = slotControl.map(_.laneRequest.decodeResult)

    vfu.vfuConnect(parameter.vfuInstantiateParameter)(
      requestVec, executeEnqueueValid, decodeResultVec, executeEnqueueFire, responseVec, executeOccupied, VFUNotClear
    )
  }

  // 处理 rf
  {
    // 连接读口
    val readArbiter = Module(
      new Arbiter(
        new VRFReadRequest(parameter.vrfParam.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits),
        8
      )
    )
    // 暂时把lsu的读放在了最低优先级,有问题再改
    (vrfReadRequest(1).last +: (vrfReadRequest(2) ++ vrfReadRequest(3)) :+ vrfReadAddressChannel)
      .zip(readArbiter.io.in)
      .foreach {
        case (source, sink) =>
          sink <> source
      }
    (vrfReadRequest.head ++ vrfReadRequest(1).init :+ readArbiter.io.out).zip(vrf.readRequests).foreach {
      case (source, sink) =>
        sink <> source
    }

    // 读的结果
    vrfReadResult.foreach(a => a.foreach(_ := vrf.readResults.last))
    (vrfReadResult.head ++ vrfReadResult(1).init).zip(vrf.readResults.init).foreach {
      case (sink, source) =>
        sink := source
    }
    vrfReadDataChannel := vrf.readResults.last

    // 写 rf
    val normalWrite = VecInit(vrfWriteArbiter.map(_.valid)).asUInt.orR
    val writeSelect = !normalWrite ## ffo(VecInit(vrfWriteArbiter.map(_.valid)).asUInt)
    val writeEnqBits = Mux1H(writeSelect, vrfWriteArbiter.map(_.bits) :+ crossLaneWriteQueue.io.deq.bits)
    vrf.write.valid := normalWrite || crossLaneWriteQueue.io.deq.valid
    vrf.write.bits := writeEnqBits
    crossLaneWriteQueue.io.deq.ready := !normalWrite && vrf.write.ready
    vrfWriteFire := Mux(vrf.write.ready, writeSelect, 0.U)

    //更新v0
    v0Update.valid := vrf.write.valid && writeEnqBits.vd === 0.U
    v0Update.bits.data := writeEnqBits.data
    v0Update.bits.offset := writeEnqBits.offset
    v0Update.bits.mask := writeEnqBits.mask
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
      !laneRequest.bits.decodeResult(Decoder.nr) &&
      // complete is notified by LSU.
      !laneRequest.bits.loadStore
  // indicate if this is the mask type.
  entranceControl.mask.valid := laneRequest.bits.mask
  // assign mask from [[V]]
  entranceControl.mask.bits := maskInput
  // TODO: remove it.
  entranceControl.maskGroupedOrR := maskGroupedOrR
  // mask used for VRF write in this group.
  entranceControl.vrfWriteMask := 0.U

  // calculate last group
  val lastElementIndex: UInt = (csrInterface.vl - 1.U)(parameter.vlMaxBits - 2, 0)
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
  val dataPathMisaligned = vlTail.orR
  val maskeDataGroup = (vlHead ## vlBody) - !dataPathMisaligned
  val lastLaneIndexForMaskLogic: UInt = maskeDataGroup(parameter.laneNumberBits - 1, 0)
  val isLastLaneForMaskLogic: Bool = lastLaneIndexForMaskLogic === laneIndex
  val lastGroupCountForMaskLogic: UInt = (maskeDataGroup >> parameter.laneNumberBits).asUInt

  entranceControl.lastGroupForInstruction := Mux(
    laneRequest.bits.decodeResult(Decoder.maskLogic),
    lastGroupCountForMaskLogic,
    lastGroupForLane
  )

  entranceControl.isLastLaneForMaskLogic :=
    isLastLaneForMaskLogic && dataPathMisaligned && laneRequest.bits.decodeResult(Decoder.maskLogic)

  // slot needs to be moved, try to shifter and stall pipe
  slotShiftValid := VecInit(Seq.range(0, parameter.chainingSize).map { slotIndex =>
    if (slotIndex == 0) false.B else !slotOccupied(slotIndex - 1)
  })


  val slotEnqueueFire: Seq[Bool] = Seq.tabulate(parameter.chainingSize) { slotIndex =>
    val enqueueReady: Bool = !slotOccupied(slotIndex)
    val enqueueValid: Bool = Wire(Bool())
    val enqueueFire: Bool = enqueueReady && enqueueValid
    // enqueue from lane request
    if (slotIndex == parameter.chainingSize - 1) {
      enqueueValid := laneRequest.valid
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

  // handshake
  laneRequest.ready := !slotOccupied.last && vrf.instructionWriteReport.ready

  vrf.flush := maskUnitFlushVrf
  // normal instruction, LSU instruction will be report to VRF.
  vrf.lsuInstructionFire := laneRequest.bits.LSUFire
  vrf.instructionWriteReport.valid := (laneRequest.fire || laneRequest.bits.LSUFire) && !entranceControl.instructionFinished
  vrf.instructionWriteReport.bits.instIndex := laneRequest.bits.instructionIndex
  vrf.instructionWriteReport.bits.offset := 0.U //todo
  vrf.instructionWriteReport.bits.vdOffset := 0.U
  vrf.instructionWriteReport.bits.vd.bits := laneRequest.bits.vd
  vrf.instructionWriteReport.bits.vd.valid := !laneRequest.bits.decodeResult(Decoder.targetRd) || (laneRequest.bits.loadStore && !laneRequest.bits.store)
  vrf.instructionWriteReport.bits.vs2 := laneRequest.bits.vs2
  vrf.instructionWriteReport.bits.vs1.bits := laneRequest.bits.vs1
  vrf.instructionWriteReport.bits.vs1.valid := laneRequest.bits.decodeResult(Decoder.vtype)
  // TODO: move ma to [[V]]
  vrf.instructionWriteReport.bits.ma := laneRequest.bits.ma
  // lsu访问vrf都不是无序的
  vrf.instructionWriteReport.bits.unOrderWrite := laneRequest.bits.decodeResult(Decoder.other)
  vrf.instructionWriteReport.bits.seg.valid := laneRequest.bits.loadStore && laneRequest.bits.segment.orR
  vrf.instructionWriteReport.bits.seg.bits := laneRequest.bits.segment
  vrf.instructionWriteReport.bits.eew := laneRequest.bits.loadStoreEEW
  vrf.instructionWriteReport.bits.ls := laneRequest.bits.loadStore
  vrf.instructionWriteReport.bits.st := laneRequest.bits.store
  vrf.instructionWriteReport.bits.widen := laneRequest.bits.decodeResult(Decoder.crossWrite)
  vrf.instructionWriteReport.bits.stFinish := false.B
  vrf.instructionWriteReport.bits.mul := Mux(csrInterface.vlmul(2), 0.U, csrInterface.vlmul(1, 0))
  vrf.instructionWriteReport.bits.maskGroupCounter := 0.U
  // clear record by instructionFinished
  vrf.lsuLastReport := lsuLastReport | instructionFinished
  vrf.lsuMaskGroupChange := lsuMaskGroupChange
  vrf.lsuWriteBufferClear := lsuVRFWriteBufferClear && !crossLaneWriteQueue.io.deq.valid
  instructionFinished := instructionFinishedVec.reduce(_ | _)
}
