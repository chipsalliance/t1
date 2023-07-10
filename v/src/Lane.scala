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

      // --- stage 0 start ---
      // todo: parameter register width for all stage

      // register for stage0
      val valid0: Bool = RegInit(false.B)
      val groupCounterInStage0: UInt = RegInit(0.U(parameter.groupNumberBits.W))
      val maskInStage0: UInt = RegInit(0.U(4.W))
      val sSendResponseInStage0: Option[Bool] = Option.when(isLastSlot) {RegInit(true.B)}

      val s0Valid: Bool = Wire(Bool())
      val s0Ready: Bool = Wire(Bool())
      val s0Fire: Bool = s0Valid && s0Ready

      /** Filter by different sew */
      val filterVec: Seq[(Bool, UInt)] = Seq(0, 1, 2).map { filterSew =>
        // The lower 'dataGroupIndexSize' bits represent the offsets in the data group
        val dataGroupIndexSize: Int = 2 - filterSew
        // each group has '2 ** dataGroupIndexSize' elements
        val dataGroupSize = 1 << dataGroupIndexSize
        // The data group index of last data group
        val groupIndex = (maskIndexVec(index) >> dataGroupIndexSize).asUInt
        // Filtering data groups
        val groupFilter: UInt = scanLeftOr(UIntToOH(groupIndex)) ## false.B
        // Whether there are element in the data group that have not been masked
        // TODO: use 'record.maskGroupedOrR' & update it
        val maskForDataGroup: UInt =
          VecInit(maskForMaskGroup.asBools.grouped(dataGroupSize).map(_.reduce(_ || _)).toSeq).asUInt
        val groupFilterByMask = maskForDataGroup & groupFilter
        // ffo next group
        val nextDataGroupOH: UInt = ffo(groupFilterByMask)
        // This mask group has the next data group to execute
        val hasNextDataGroup = nextDataGroupOH.orR
        val nextElementBaseIndex: UInt = (OHToUInt(nextDataGroupOH) << dataGroupIndexSize).asUInt
        (hasNextDataGroup, nextElementBaseIndex)
      }

      /** is there any data left in this group? */
      val nextOrR: Bool = Mux1H(vSew1H, filterVec.map(_._1))

      // mask is exhausted
      val maskExhausted: Bool = !nextOrR

      /** The index of next element in this mask group.(0-31) */
      val nextIndex: UInt = Mux(decodeResult(Decoder.maskLogic), 0.U, Mux1H(vSew1H, filterVec.map(_._2)))

      /** The mask group will be updated */
      val maskGroupWillUpdate: Bool = decodeResult(Decoder.maskLogic) || maskExhausted

      /** next mask group */
      val nextMaskGroupCount: UInt = maskGroupCountVec(index) + maskGroupWillUpdate

      /** The index of next execute element in whole instruction */
      val elementIndexForInstruction = maskGroupCountVec(index) ## Mux1H(
        vSew1H,
        Seq(
          maskIndexVec(index)(parameter.datapathWidthBits - 1, 2) ## laneIndex ## maskIndexVec(index)(1, 0),
          maskIndexVec(index)(parameter.datapathWidthBits - 1, 1) ## laneIndex ## maskIndexVec(index)(0),
          maskIndexVec(index) ## laneIndex
        )
      )


      /** The next element is out of execution range */
      val outOfExecutionRange = Mux(
        decodeResult(Decoder.maskLogic),
        (maskGroupCountVec(index) > record.lastGroupForInstruction),
        elementIndexForInstruction >= record.csr.vl
      ) || record.instructionFinished

      // todo: 如果这一部分时序不够,可以放到下一级去, 然后在下一级 kill nr类型的
      /** Encoding of different element lengths: 1, 8, 16, 32 */
      val elementLengthOH = Mux(decodeResult(Decoder.maskLogic), 1.U, vSew1H(2, 0) ## false.B)

      /** Which group of data will be accessed */
      val dataGroupIndex: UInt = Mux1H(
        elementLengthOH,
        Seq(
          maskGroupCountVec(index),
          maskGroupCountVec(index) ## maskIndexVec(index)(4, 2),
          maskGroupCountVec(index) ## maskIndexVec(index)(4, 1),
          maskGroupCountVec(index) ## maskIndexVec(index)
        )
      )

      /** Calculate the mask of the request that is in s0 */
      val maskEnqueueWireInStage0: UInt = (record.mask.bits >> maskIndexVec(index)).asUInt(3, 0)

      val isTheLastGroup = dataGroupIndex === record.lastGroupForInstruction
      // update register in s0
      when(s0Fire) {
        maskGroupCountVec(index) := nextMaskGroupCount
        // todo: handle all elements in first group are masked
        maskIndexVec(index) := nextIndex
        groupCounterInStage0 := dataGroupIndex
        maskInStage0 := maskEnqueueWireInStage0
        sSendResponseInStage0.foreach(state =>
          state :=
            !(record.laneRequest.loadStore ||
              decodeResult(Decoder.readOnly) ||
              (decodeResult(Decoder.red) && isTheLastGroup) ||
              (decodeResult(Decoder.maskDestination) && (maskGroupWillUpdate || isTheLastGroup)) ||
              decodeResult(Decoder.ffo))
        )
      }

      // Handshake for s0
      s0Valid := slotActive(index) && !outOfExecutionRange && (record.mask.valid || !record.laneRequest.mask)

      when(!pipeFinishVec(index) && outOfExecutionRange) {
        pipeFinishVec(index) := true.B
      }

      instructionFinishedVec(index) := 0.U
      when(slotOccupied(index) && pipeClear && pipeFinishVec(index)) {
        slotOccupied(index) := false.B
        instructionFinishedVec(index) := UIntToOH(
          record.laneRequest.instructionIndex(parameter.instructionIndexBits - 2, 0)
        )
      }

      // update mask todo: handle maskRequestFireOH
      slotMaskRequestVec(index).valid := maskExhausted && record.laneRequest.mask && (s0Fire || !record.mask.valid)
      slotMaskRequestVec(index).bits := nextMaskGroupCount
      // There are new masks
      val maskUpdateFire: Bool = slotMaskRequestVec(index).valid && maskRequestFireOH(index)
      // The old mask is used up
      val maskFailure: Bool = maskExhausted && s0Fire
      // update mask register
      when(maskUpdateFire) {
        record.mask.bits := maskInput
      }
      when(maskUpdateFire ^ maskFailure) {
        record.mask.valid := maskUpdateFire
      }

      // --- stage 0 end & stage 1_0 start ---

      // stage 1_0 reg
      val valid1: Bool = RegInit(false.B)

      /** schedule read src1 */
      val sRead0 = RegInit(true.B)

      /** schedule read src2 */
      val sRead1 = RegInit(true.B)

      /** schedule read vd */
      val sRead2 = RegInit(true.B)

      // pipe from stage0
      val groupCounterInStage1: UInt = RegInit(0.U(parameter.groupNumberBits.W))

      // mask for group pipe from stage0
      val maskInStage1: UInt = RegInit(0.U(4.W))
      val maskForFilterInStage1: UInt = FillInterleaved(4, maskNotMaskedElement) | maskInStage1

      // read result register
      val readResult0: UInt = RegInit(0.U(parameter.datapathWidth.W))
      val readResult1: UInt = RegInit(0.U(parameter.datapathWidth.W))
      val readResult2: UInt = RegInit(0.U(parameter.datapathWidth.W))

      /** schedule cross lane read LSB.(access VRF for cross read) */
      val sCrossReadLSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

      /** schedule cross lane read MSB.(access VRF for cross read) */
      val sCrossReadMSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

      /** schedule send cross lane read LSB result. */
      val sSendCrossReadResultLSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

      /** schedule send cross lane read MSB result. */
      val sSendCrossReadResultMSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

      /** wait for cross lane read LSB result. */
      val wCrossReadLSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

      /** wait for cross lane read MSB result. */
      val wCrossReadMSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

      // next for update cross read register
      val sReadNext0: Bool = RegNext(sRead0, false.B)
      val sReadNext1: Bool = RegNext(sRead1, false.B)
      val sReadNext2: Bool = RegNext(sRead2, false.B)
      val sCrossReadLSBNext: Option[Bool] = sCrossReadLSB.map(RegNext(_, false.B))
      val sCrossReadMSBNext: Option[Bool] = sCrossReadMSB.map(RegNext(_, false.B))
      // All read requests sent
      val sReadFinish: Bool = sRead0 && sRead1 && sRead2
      // Waiting to read the response
      val sReadFinishNext: Bool = sReadNext0 && sReadNext1 && sReadNext2
      // 'sReadFinishNext' may assert at the next cycle of 's1Fire', need sReadFinish
      val readFinish: Bool = sReadFinish && sReadFinishNext
      val stage1Finish: Bool = (Seq(readFinish) ++ sSendCrossReadResultLSB ++
        sSendCrossReadResultMSB ++ wCrossReadLSB ++ wCrossReadMSB).reduce(_ && _)

      // control wire
      val s1Valid = valid0
      val s1Ready = Wire(Bool())
      val s1Fire = s1Valid && s1Ready
      val sSendResponseInStage1 = Option.when(isLastSlot)(RegEnable(sSendResponseInStage0.get, true.B, s1Fire))

      when(s1Fire ^ s0Fire) { valid0 := s0Fire }
      s0Ready := s1Ready || !valid0

      /** mask offset for this group, needs to be aligned with data group */
      val maskOffsetForNextGroup: UInt = maskIndexVec(index)(4, 2) ## Mux1H(
        vSew1H(2, 0),
        Seq(
          0.U(2.W),
          maskIndexVec(index)(1) ## false.B,
          maskIndexVec(index)(1, 0)
        )
      )

      /** mask for this group */
      val nextMaskForGroup: UInt = (record.mask.bits >> maskOffsetForNextGroup)(3, 0)

      // --- stage 1_0 end & stage 1_1 start ---

      // read port 0
      vrfReadRequest(index)(0).valid := !sRead0 && valid1
      vrfReadRequest(index)(0).bits.offset := groupCounterInStage1(parameter.vrfOffsetBits - 1, 0)
      vrfReadRequest(index)(0).bits.vs := Mux(
        // encodings with vm=0 are reserved for mask type logic
        record.laneRequest.decodeResult(Decoder.maskLogic) && !record.laneRequest.decodeResult(Decoder.logic),
        // read v0 for (15. Vector Mask Instructions)
        0.U,
        record.laneRequest.vs1 + groupCounterInStage1(
          parameter.groupNumberBits - 1,
          parameter.vrfOffsetBits
        )
      )
      // used for hazard detection
      vrfReadRequest(index)(0).bits.instructionIndex := record.laneRequest.instructionIndex

      // read port 1
      if (isLastSlot) {
        vrfReadRequest(index)(1).valid := !(sRead1 && sCrossReadLSB.get) && valid1
        vrfReadRequest(index)(1).bits.offset := Mux(
          sRead1,
          // cross lane LSB
          groupCounterInStage1(parameter.vrfOffsetBits - 2, 0) ## false.B,
          // normal read
          groupCounterInStage1(parameter.vrfOffsetBits - 1, 0)
        )
        vrfReadRequest(index)(1).bits.vs := Mux(
          decodeResult(Decoder.vwmacc) && sRead1,
          // cross read vd for vwmacc, since it need dual [[dataPathWidth]], use vs2 port to read LSB part of it.
          record.laneRequest.vd,
          // read vs2 for other instruction
          record.laneRequest.vs2
        ) + Mux(
          sRead1,
          // cross lane
          groupCounterInStage1(parameter.groupNumberBits - 2, parameter.vrfOffsetBits - 1),
          // no cross lane
          groupCounterInStage1(parameter.groupNumberBits - 1, parameter.vrfOffsetBits)
        )
      } else {
        vrfReadRequest(index)(1).valid := !sRead1 && valid1
        vrfReadRequest(index)(1).bits.offset := groupCounterInStage1(parameter.vrfOffsetBits - 1, 0)
        vrfReadRequest(index)(1).bits.vs := record.laneRequest.vs2 +
          groupCounterInStage1(parameter.groupNumberBits - 1, parameter.vrfOffsetBits)
      }
      vrfReadRequest(index)(1).bits.instructionIndex := record.laneRequest.instructionIndex

      // read port 2
      if (isLastSlot) {
        vrfReadRequest(index)(2).valid := !(sRead2 && sCrossReadMSB.get) && valid1
        vrfReadRequest(index)(2).bits.offset := Mux(
          sRead2,
          // cross lane MSB
          groupCounterInStage1(parameter.vrfOffsetBits - 2, 0) ## true.B,
          // normal read
          groupCounterInStage1(parameter.vrfOffsetBits - 1, 0)
        )
        vrfReadRequest(index)(2).bits.vs := Mux(
          sRead2 && !record.laneRequest.decodeResult(Decoder.vwmacc),
          // cross lane access use vs2
          record.laneRequest.vs2,
          // normal read vd or cross read vd for vwmacc
          record.laneRequest.vd
        ) +
          Mux(
            sRead2,
            groupCounterInStage1(parameter.groupNumberBits - 2, parameter.vrfOffsetBits - 1),
            groupCounterInStage1(parameter.groupNumberBits - 1, parameter.vrfOffsetBits)
          )
      } else {
        vrfReadRequest(index)(2).valid := !sRead2 && valid1
        vrfReadRequest(index)(2).bits.offset := groupCounterInStage1(parameter.vrfOffsetBits - 1, 0)
        vrfReadRequest(index)(2).bits.vs := record.laneRequest.vd +
          groupCounterInStage1(parameter.groupNumberBits - 1, parameter.vrfOffsetBits)
      }
      vrfReadRequest(index)(2).bits.instructionIndex := record.laneRequest.instructionIndex

      val readPortFire0: Bool = vrfReadRequest(index)(0).fire
      val readPortFire1: Bool = vrfReadRequest(index)(1).fire
      val readPortFire2: Bool = vrfReadRequest(index)(2).fire
      // reg next for update result
      val readPortFireNext0: Bool = RegNext(readPortFire0, false.B)
      val readPortFireNext1: Bool = RegNext(readPortFire1, false.B)
      val readPortFireNext2: Bool = RegNext(readPortFire2, false.B)

      // update read control register in stage 1
      when(s1Fire) {
        // init register by decode result
        sRead0 := !decodeResult(Decoder.vtype)
        // todo: gather only read vs1?
        sRead1 := false.B
        sRead2 := decodeResult(Decoder.sReadVD)
        val sCrossRead = !decodeResult(Decoder.crossRead)
        (
          sCrossReadLSB ++ sCrossReadMSB ++
            sSendCrossReadResultLSB ++ sSendCrossReadResultMSB ++
            wCrossReadLSB ++ wCrossReadMSB
          ).foreach(state => state := sCrossRead)

        // pipe reg from stage 0
        groupCounterInStage1 := groupCounterInStage0
        maskInStage1 := maskInStage0
      }.otherwise {
        // change state machine when read source1
        when(readPortFire0) {
          sRead0 := true.B
        }
        // the priority of `sRead1` is higher than `sCrossReadLSB`
        when(readPortFire1) {
          sRead1 := true.B
          sCrossReadLSB.foreach(d => d := sRead1)
        }
        // the priority of `sRead2` is higher than `sCrossReadMSB`
        when(readPortFire2) {
          sRead2 := true.B
          sCrossReadMSB.foreach(d => d := sRead2)
        }

        when(readBusDequeue.valid) {
          when(readBusDequeue.bits.isTail) {
            wCrossReadMSB.foreach(_ := true.B)
          }.otherwise {
            wCrossReadLSB.foreach(_ := true.B)
          }
        }
      }

      // update read result register
      when(readPortFireNext0) {
        readResult0 := vrfReadResult(index)(0)
      }

      when(readPortFireNext1) {
        if (isLastSlot) {
          when(sReadNext1) {
            crossReadLSBOut := vrfReadResult(index)(1)
          }.otherwise {
            readResult1 := vrfReadResult(index)(1)
          }
        } else {
          readResult1 := vrfReadResult(index)(1)
        }
      }

      when(readPortFireNext2) {
        if (isLastSlot) {
          when(sReadNext2) {
            crossReadMSBOut := vrfReadResult(index)(2)
          }.otherwise {
            readResult2 := vrfReadResult(index)(2)
          }
        } else {
          readResult2 := vrfReadResult(index)(2)
        }
      }

      if (isLastSlot) {
        // cross read
        /** for dequeue group counter match */
        readBusDequeueGroup := groupCounterInStage1
        /** The data to be sent is ready
          * need sCrossReadLSB since sCrossReadLSBNext may assert after s1fire.
          */
        val crossReadDataReadyLSB: Bool = (sCrossReadLSBNext ++ sCrossReadLSB).reduce(_ && _)
        val crossReadDataReadyMSB: Bool = (sCrossReadMSBNext ++ sCrossReadMSB).reduce(_ && _)

        /** read data from RF, try to send cross lane read LSB data to ring */
        val tryCrossReadSendLSB: Bool = crossReadDataReadyLSB && !sSendCrossReadResultLSB.get && valid1

        /** read data from RF, try to send cross lane read MSB data to ring */
        val tryCrossReadSendMSB: Bool = crossReadDataReadyMSB && !sSendCrossReadResultMSB.get && valid1
        // TODO: use [[record.state.sSendCrossReadResultLSB]]
        crossLaneRead.bits.sinkIndex := (!tryCrossReadSendLSB) ## laneIndex(parameter.laneNumberBits - 1, 1)
        crossLaneRead.bits.isTail := laneIndex(0)
        crossLaneRead.bits.sourceIndex := laneIndex
        crossLaneRead.bits.instructionIndex := record.laneRequest.instructionIndex
        crossLaneRead.bits.counter := groupCounterInStage1
        // TODO: use [[record.state.sSendCrossReadResultLSB]] -> MSB may be ready earlier
        crossLaneRead.bits.data := Mux(tryCrossReadSendLSB, crossReadLSBOut, crossReadMSBOut)
        crossLaneRead.valid := tryCrossReadSendLSB || tryCrossReadSendMSB

        when(crossLaneReadReady && crossLaneRead.valid) {
          when(tryCrossReadSendLSB) {
            sSendCrossReadResultLSB.foreach(_ := true.B)
          }.otherwise {
            sSendCrossReadResultMSB.foreach(_ := true.B)
          }
        }

        // cross read receive. todo: move out slot
        when(readBusDequeue.valid) {
          assert(readBusDequeue.bits.instructionIndex === record.laneRequest.instructionIndex)
          when(readBusDequeue.bits.isTail) {
            crossReadMSBIn := readBusDequeue.bits.data
          }.otherwise {
            crossReadLSBIn := readBusDequeue.bits.data
          }
        }
      }

      // --- stage 1_1 end & stage 2 start ---
      val executionQueue: Queue[LaneExecuteStage] =
        Module(new Queue(new LaneExecuteStage(parameter)(isLastSlot), parameter.executionQueueSize))

      val s2Ready = Wire(Bool())
      val s2Valid = valid1 && stage1Finish
      val s2Fire: Bool = s2Ready && s2Valid
      val valid2 = RegInit(false.B)
      // need clear mask format result when mask group change
      val updateMaskResult: Option[Bool] = Option.when(isLastSlot)(Wire(Bool()))
      // backpressure for stage 1
      s1Ready := !valid1 || (stage1Finish && s2Ready)
      // update 'valid1'
      when(s1Fire ^ s2Fire) {valid1 := s1Fire}
      val s2ExecuteOver = Wire(Bool())

      // execution result from execute unit
      val executionResult = RegInit(0.U(parameter.datapathWidth.W))

      /** mask format result for current `mask group` */
      val maskFormatResultForGroup: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(parameter.maskGroupWidth.W)))

      /** cross write LSB mask to send out to other lanes. */
      val Stage2crossWriteLSB = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))

      /** cross write MSB data to send out to other lanes. */
      val Stage2crossWriteMSB = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))
      // pipe from stage 0
      val sSendResponseInStage2 = Option.when(isLastSlot)(RegEnable(sSendResponseInStage1.get, true.B, s2Fire))
      // ffo success in current data group?
      val ffoSuccessImStage2: Option[Bool] = Option.when(isLastSlot)(RegInit(false.B))

      // executionQueue enqueue
      executionQueue.io.enq.bits.pipeData.foreach { data =>
        data := Mux(
          // pipe source1 for gather, pipe v0 for ffo
          decodeResult(Decoder.gather) || decodeResult(Decoder.ffo),
          readResult0,
          readResult1
        )
      }
      executionQueue.io.enq.bits.pipeVD.foreach(_ := readResult2)
      executionQueue.io.enq.bits.groupCounter := groupCounterInStage1
      executionQueue.io.enq.bits.mask := Mux1H(
        vSew1H,
        Seq(
          maskForFilterInStage1,
          FillInterleaved(2, maskForFilterInStage1(1, 0)),
          // todo: handle first masked
          FillInterleaved(4, maskForFilterInStage1(0))
        )
      )


      // 先用一个伪装的执行单元 todo: 等执行单元重构需要替换
      if (true) {
        val executionRecord: ExecutionUnitRecord = RegInit(0.U.asTypeOf(new ExecutionUnitRecord(parameter)(isLastSlot)))

        val executeIndex1H: UInt = UIntToOH(executionRecord.executeIndex)

        // state register
        val sSendExecuteRequest = RegInit(true.B)
        val wExecuteResult = RegInit(true.B)
        val executeRequestStateValid: Bool = !sSendExecuteRequest
        s2ExecuteOver := sSendExecuteRequest && wExecuteResult

        val source1Select: UInt = Mux(decodeResult(Decoder.vtype), readResult0, record.laneRequest.readFromScalar)
        // init register when s2Fire
        when(s2Fire) {
          executionRecord.crossReadVS2 := decodeResult(Decoder.crossRead) && !decodeResult(Decoder.vwmacc)
          executionRecord.bordersForMaskLogic :=
            (groupCounterInStage1 === record.lastGroupForInstruction && record.isLastLaneForMaskLogic)
          executionRecord.mask := maskInStage1
          executionRecord.source := VecInit(Seq(source1Select, readResult1, readResult2))
          executionRecord.crossReadSource.foreach(_ := crossReadMSBIn ## crossReadLSBIn)
          executionRecord.groupCounter := groupCounterInStage1
          sSendExecuteRequest := decodeResult(Decoder.dontNeedExecuteInLane)
          wExecuteResult := decodeResult(Decoder.dontNeedExecuteInLane)
          ffoSuccessImStage2.foreach(_ := false.B)
        }

        /** the byte-level mask of current execution.
          * sew match:
          *   0:
          *     executeIndex match:
          *       0: 0001
          *       1: 0010
          *       2: 0100
          *       3: 1000
          *   1:
          *     executeIndex(0) match:
          *       0: 0011
          *       1: 1100
          *   2:
          *     1111
          */
        val byteMaskForExecution = Mux1H(
          vSew1H(2, 0),
          Seq(
            executeIndex1H,
            executionRecord.executeIndex(1) ## executionRecord.executeIndex(1) ##
              !executionRecord.executeIndex(1) ## !executionRecord.executeIndex(1),
            15.U(4.W)
          )
        )

        /** the bit-level mask of current execution. */
        val bitMaskForExecution: UInt = FillInterleaved(8, byteMaskForExecution)

        def CollapseOperand(data: UInt, enable: Bool = true.B, sign: Bool = false.B): UInt = {
          val dataMasked: UInt = data & bitMaskForExecution
          val select: UInt = Mux(enable, vSew1H(2, 0), 4.U(3.W))
          // when sew = 0
          val collapse0 = Seq.tabulate(4)(i => dataMasked(8 * i + 7, 8 * i)).reduce(_ | _)
          // when sew = 1
          val collapse1 = Seq.tabulate(2)(i => dataMasked(16 * i + 15, 16 * i)).reduce(_ | _)
          Mux1H(
            select,
            Seq(
              Fill(25, sign && collapse0(7)) ## collapse0,
              Fill(17, sign && collapse1(15)) ## collapse1,
              (sign && data(31)) ## data
            )
          )
        }

        // 有2 * sew 的操作数需要折叠
        def CollapseDoubleOperand(sign: Bool = false.B): UInt = {
          val doubleBitEnable = FillInterleaved(16, byteMaskForExecution)
          val doubleDataMasked: UInt = executionRecord.crossReadSource.get & doubleBitEnable
          val select: UInt = vSew1H(1, 0)
          // when sew = 0
          val collapse0 = Seq.tabulate(4)(i => doubleDataMasked(16 * i + 15, 16 * i)).reduce(_ | _)
          // when sew = 1
          val collapse1 = Seq.tabulate(2)(i => doubleDataMasked(32 * i + 31, 32 * i)).reduce(_ | _)
          Mux1H(
            select,
            Seq(
              Fill(16, sign && collapse0(15)) ## collapse0,
              collapse1
            )
          )
        }

        /** collapse the dual SEW size operand for cross read.
          * it can be vd or src2.
          */
        val doubleCollapse = Option.when(isLastSlot)(CollapseDoubleOperand(!decodeResult(Decoder.unsigned1)))

        /** src1 for the execution
          * src1 has three types: V, I, X.
          * only V type need to use [[CollapseOperand]]
          */
        val finalSource1 = CollapseOperand(
          // A will be updated every time it is executed, so you can only choose here
          Mux(decodeResult(Decoder.red) && !decodeResult(Decoder.maskLogic), reduceResult, executionRecord.source.head),
          decodeResult(Decoder.vtype) && (!decodeResult(Decoder.red) || decodeResult(Decoder.maskLogic)),
          !decodeResult(Decoder.unsigned0)
        )

        /** src2 for the execution,
          * need to take care of cross read.
          */
        val finalSource2 = if (isLastSlot) {
          Mux(
            executionRecord.crossReadVS2,
            doubleCollapse.get,
            CollapseOperand(executionRecord.source(1), true.B, !decodeResult(Decoder.unsigned1))
          )
        } else {
          CollapseOperand(executionRecord.source(1), true.B, !decodeResult(Decoder.unsigned1))
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
            CollapseOperand(executionRecord.source(2))
          )
        }else {
          CollapseOperand(executionRecord.source(2))
        }

        val maskAsInput = Mux1H(
          vSew1H(2, 0),
          Seq(
            (UIntToOH(executionRecord.executeIndex) & executionRecord.mask).orR,
            Mux(executionRecord.executeIndex(1), executionRecord.mask(1), executionRecord.mask(0)),
            executionRecord.mask(0)
          )
        )

        /** use mask to fix the case that `vl` is not in the multiple of [[parameter.datapathWidth]].
          * it will fill the LSB of mask to `0`, mask it to not execute those elements.
          */
        val lastGroupMask = scanRightOr(UIntToOH(record.csr.vl(parameter.datapathWidthBits - 1, 0))) >> 1

        /** if [[executionRecord.bordersForMaskLogic]],
          * use [[lastGroupMask]] to mask the result otherwise use [[fullMask]]. */
        val maskCorrect = Mux(executionRecord.bordersForMaskLogic, lastGroupMask, fullMask)

        val requestToVFU: SlotRequestToVFU = Wire(new SlotRequestToVFU(parameter))
        requestToVFU.src := VecInit(Seq(finalSource1, finalSource2, finalSource3, maskCorrect))
        requestToVFU.opcode := decodeResult(Decoder.uop)
        requestToVFU.mask := Mux(
          decodeResult(Decoder.adder),
          maskAsInput && decodeResult(Decoder.maskSource),
          maskAsInput || !record.laneRequest.mask
        )
        requestToVFU.sign := !decodeResult(Decoder.unsigned1)
        requestToVFU.reverse := decodeResult(Decoder.reverse)
        requestToVFU.average := decodeResult(Decoder.average)
        requestToVFU.saturate := decodeResult(Decoder.saturate)
        requestToVFU.vxrm := record.csr.vxrm
        requestToVFU.vSew := record.csr.vSew
        requestToVFU.shifterSize := Mux1H(
          Mux(executionRecord.crossReadVS2, vSew1H(1, 0), vSew1H(2, 1)),
          Seq(false.B ## finalSource1(3), finalSource1(4, 3))
        ) ## finalSource1(2, 0)
        requestToVFU.rem := decodeResult(Decoder.uop)(0)
        requestToVFU.executeIndex := executionRecord.executeIndex
        requestToVFU.popInit := reduceResult
        requestToVFU.groupIndex := executionRecord.groupCounter
        requestToVFU.laneIndex := laneIndex
        requestToVFU.complete := record.ffoByOtherLanes || record.selfCompleted
        requestToVFU.maskType := record.laneRequest.mask
        requestToVFU.unitSelet := decodeResult(Decoder.fpExecutionType)
        requestToVFU.floatMul := decodeResult(Decoder.floatMul)
        // from float csr
        requestToVFU.roundingMode := record.csr.vxrm

        requestVec(index) := requestToVFU

        executeEnqueueValid(index) := executeRequestStateValid

        /** select from VFU, send to [[executionResult]], [[Stage2crossWriteLSB]], [[Stage2crossWriteMSB]]. */
        val dataDequeue: UInt = responseVec(index).bits.data

        val executeEnqueueFireForSlot: Bool = executeEnqueueFire(index)

        /** fire of [[dataDequeue]] */
        val executeDequeueFireForSlot: Bool =
          Mux(decodeResult(Decoder.multiCycle), responseVec(index).valid, executeEnqueueFireForSlot)

        // mask reg for filtering
        val maskForFilter = FillInterleaved(4, maskNotMaskedElement) | executionRecord.mask
        // current one hot depends on execute index
        val currentOHForExecuteGroup: UInt = UIntToOH(executionRecord.executeIndex)
        // Remaining to be requested
        val remainder: UInt = maskForFilter & (~scanRightOr(currentOHForExecuteGroup)).asUInt
        // Finds the first unfiltered execution.
        val nextIndex1H: UInt = ffo(remainder)

        // There are no more left.
        val isLastRequestForThisGroup: Bool =
          Mux1H(vSew1H, Seq(!remainder.orR, !remainder(1, 0).orR, true.B))

        /** the next index to execute.
          * @note Requests into this disguised execution unit are not executed on the spot
          * */
        val nextExecuteIndex: UInt = Mux1H(
          vSew1H(1, 0),
          Seq(
            OHToUInt(nextIndex1H),
            // Mux(remainder(0), 0.U, 2.U)
            !remainder(0) ## false.B
          )
        )

        // next execute index if data group change
        val nextExecuteIndexForNextGroup = Mux1H(
          vSew1H(1, 0),
          Seq(
            OHToUInt(ffo(maskForFilterInStage1)),
            !maskForFilterInStage1(0) ## false.B,
          )
        )

        // update execute index
        when(executeEnqueueFireForSlot || s2Fire) {
          executionRecord.executeIndex := Mux(s2Fire, nextExecuteIndexForNextGroup, nextExecuteIndex)
        }

        when(executeEnqueueFireForSlot && isLastRequestForThisGroup) {
          sSendExecuteRequest := true.B
        }

        // execute response finish
        val responseFinish: Bool = Mux(
          decodeResult(Decoder.multiCycle),
          executeDequeueFireForSlot && sSendExecuteRequest,
          executeEnqueueFireForSlot && isLastRequestForThisGroup
        )

        when(responseFinish) {
          wExecuteResult := true.B
        }

        val divWriteIndexLatch: UInt = RegEnable(responseVec(index).bits.executeIndex, 0.U(2.W), responseVec(index).valid)
        val divWriteIndex = Mux(responseVec(index).valid, responseVec(index).bits.executeIndex, divWriteIndexLatch)
        /** the index to write to VRF in [[parameter.dataPathByteWidth]].
          * for long latency pipe, the index will follow the pipeline.
          */
        val writeIndex = Mux(
          record.laneRequest.decodeResult(Decoder.multiCycle),
          divWriteIndex,
          executionRecord.executeIndex
        )

        val writeIndex1H = UIntToOH(writeIndex)

        /** VRF byte level mask */
        val writeMaskInByte = Mux1H(
          vSew1H(2, 0),
          Seq(
            writeIndex1H,
            writeIndex(1) ## writeIndex(1) ## !writeIndex(1) ## !writeIndex(1),
            "b1111".U(4.W)
          )
        )

        /** VRF bit level mask */
        val writeMaskInBit: UInt = FillInterleaved(8, writeMaskInByte)

        /** output of execution unit need to align to VRF in bit level(used in dynamic shift)
          * TODO: fix me
          */
        val dataOffset: UInt = writeIndex ## 0.U(3.W)

        // TODO: this is a dynamic shift logic, but if we switch to parallel execution unit, we don't need it anymore.
        val executeResult = (dataDequeue << dataOffset).asUInt(parameter.datapathWidth - 1, 0)

        // execute 1,2,4 times based on SEW, only write VRF when 32 bits is ready.
        val resultUpdate: UInt = (executeResult & writeMaskInBit) | (executionResult & (~writeMaskInBit).asUInt)

        // update execute result
        when(executeDequeueFireForSlot) {
          // update the [[executionResult]]
          executionResult := resultUpdate

          // the find first one instruction is finished in this lane
          ffoSuccessImStage2.foreach(_ := responseVec(index).bits.ffoSuccess)
          when(responseVec(index).bits.ffoSuccess && !record.selfCompleted) {
            ffoIndexReg := executionRecord.groupCounter ## Mux1H(
              vSew1H,
              Seq(
                executionRecord.executeIndex ## responseVec(index).bits.data(2, 0),
                executionRecord.executeIndex(1) ## responseVec(index).bits.data(3, 0),
                responseVec(index).bits.data(4, 0)
              )
            )
          }

          // update cross-lane write data
          /** sew:
            *   0:
            *     executeIndex:
            *       0: mask = 0011, head
            *       1: mask = 1100, head
            *       2: mask = 0011, tail
            *       3: mask = 1100, tail
            *   1:
            *     executeIndex:
            *       0: mask = 1111, head
            *       2: mask = 1111, tail
            *
            *   2: not valid in SEW = 2
            */
          if (isLastSlot) {
            when(executionRecord.executeIndex(1)) {
              Stage2crossWriteMSB.foreach { crossWriteData =>
                // update tail
                crossWriteData :=
                  Mux(
                    record.csr.vSew(0),
                    dataDequeue(parameter.datapathWidth - 1, parameter.halfDatapathWidth),
                    Mux(
                      executionRecord.executeIndex(0),
                      dataDequeue(parameter.halfDatapathWidth - 1, 0),
                      crossWriteData(parameter.datapathWidth - 1, parameter.halfDatapathWidth)
                    )
                  ) ## Mux(
                    !executionRecord.executeIndex(0) || record.csr.vSew(0),
                    dataDequeue(parameter.halfDatapathWidth - 1, 0),
                    crossWriteData(parameter.halfDatapathWidth - 1, 0)
                  )
              }
            }.otherwise {
              Stage2crossWriteLSB.foreach { crossWriteData =>
                crossWriteData :=
                  Mux(
                    record.csr.vSew(0),
                    dataDequeue(parameter.datapathWidth - 1, parameter.halfDatapathWidth),
                    Mux(
                      executionRecord.executeIndex(0),
                      dataDequeue(parameter.halfDatapathWidth - 1, 0),
                      crossWriteData(parameter.datapathWidth - 1, parameter.halfDatapathWidth)
                    )
                  ) ## Mux(
                    !executionRecord.executeIndex(0) || record.csr.vSew(0),
                    dataDequeue(parameter.halfDatapathWidth - 1, 0),
                    crossWriteData(parameter.halfDatapathWidth - 1, 0)
                  )
              }
            }
          }
        }

        // update mask result
        if (isLastSlot) {
          val current1HInGroup = Mux1H(
            vSew1H(2, 0),
            Seq(
              // 32bit, 4 bit per data group, it will had 8 data groups -> executeIndex1H << 4 * groupCounter(2, 0)
              executeIndex1H << (executionRecord.groupCounter(2, 0) ## 0.U(2.W)),
              // 2 bit per data group, it will had 16 data groups -> executeIndex1H << 2 * groupCounter(3, 0)
              (executionRecord.executeIndex(1) ## !executionRecord.executeIndex(1)) <<
                (executionRecord.groupCounter(3, 0) ## false.B),
              // 1 bit per data group, it will had 32 data groups -> executeIndex1H << 1 * groupCounter(4, 0)
              1.U << executionRecord.groupCounter(4, 0)
            )
          ).asUInt

          /** update value for [[maskFormatResultUpdate]],
            * it comes from ALU.
            */
          val elementMaskFormatResult: UInt = Mux(responseVec(index).bits.adderMaskResp , current1HInGroup, 0.U)

          /** update value for [[maskFormatResultForGroup]] */
          val maskFormatResultUpdate: UInt = maskFormatResultForGroup.get | elementMaskFormatResult

          // update `maskFormatResultForGroup`
          when(executeDequeueFireForSlot || updateMaskResult.get) {
            maskFormatResultForGroup.foreach(_ := Mux(executeDequeueFireForSlot, maskFormatResultUpdate, 0.U))
          }
          // masked element don't update 'reduceResult'
          val updateReduceResult = (maskNotMaskedElement || maskAsInput) && executeDequeueFireForSlot
          // update `reduceResult`
          when( updateReduceResult || updateMaskResult.get) {
            reduceResult := Mux(updateReduceResult && decodeResult(Decoder.red), dataDequeue, 0.U)
          }
        }
      }

      // --- stage 2 end & stage 3 start ---
      // Since top has only one mask processing unit,
      // all instructions that interact with top are placed in a single slot

      val s3Valid = valid2 && s2ExecuteOver
      val s3Ready = Wire(Bool())
      val s3Fire = s3Valid && s3Ready
      // Used to update valid3 without writing vrf
      val s3DequeueFire: Option[Bool] = Option.when(isLastSlot)(Wire(Bool()))
      val valid3: Option[Bool] = Option.when(isLastSlot)(RegInit(0.U(false.B)))
      // use for cross-lane write
      val groupCounterInStage3: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(7.W)))
      val maskInStage3: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(4.W)))
      val executionResultInStage3 = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))
      val pipeDataInStage3 = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))
      // result for vfirst type instruction
      val ffoIndexRegInStage3 = Option.when(isLastSlot)(RegInit(0.U(log2Ceil(parameter.vLen / 8).W)))
      // pipe vd for ff0
      val pipeVDInStage3: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))
      updateMaskResult.foreach(_ := s3Fire && !sSendResponseInStage2.get)
      // cross write result
      val Stage3crossWriteLSB = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))
      val Stage3crossWriteMSB = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))

      // cross write state
      /** schedule cross lane write LSB */
      val sCrossWriteLSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

      /** schedule cross lane write MSB */
      val sCrossWriteMSB: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

      // data for response to scheduler
      val schedulerResponseData: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))

      // state for response to scheduler
      /** schedule send [[LaneResponse]] to scheduler */
      val sSendResponse: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

      /** wait scheduler send [[LaneResponseFeedback]] */
      val wResponseFeedback: Option[Bool] = Option.when(isLastSlot)(RegInit(true.B))

      val vrfWriteVundle: VRFWriteRequest = new VRFWriteRequest(
        parameter.vrfParam.regNumBits,
        parameter.vrfOffsetBits,
        parameter.instructionIndexBits,
        parameter.datapathWidth
      )

      val vrfWriteQueue: Queue[VRFWriteRequest] =
        Module(new Queue(vrfWriteVundle, entries = 1, pipe = false, flow = false))
      valid3.foreach {data => when(s3DequeueFire.get ^ s3Fire) { data := s3Fire }}

      /** Write queue ready or not need to write. */
      val vrfWriteReady: Bool = vrfWriteQueue.io.enq.ready || decodeResult(Decoder.sWrite)

      if (isLastSlot) {
        // VRF cross write
        /** execute in ALU, try to send cross lane write LSB data to ring */
        val tryCrossWriteSendLSB = valid3.get && !sCrossWriteLSB.get

        /** execute in ALU, try to send cross lane write MSB data to ring */
        val tryCrossWriteSendMSB = valid3.get && !sCrossWriteMSB.get
        crossLaneWrite.bits.sinkIndex := laneIndex(parameter.laneNumberBits - 2, 0) ## (!tryCrossWriteSendLSB)
        crossLaneWrite.bits.sourceIndex := laneIndex
        crossLaneWrite.bits.isTail := laneIndex(parameter.laneNumberBits - 1)
        crossLaneWrite.bits.instructionIndex := record.laneRequest.instructionIndex
        crossLaneWrite.bits.counter := groupCounterInStage3.get
        crossLaneWrite.bits.data := Mux(tryCrossWriteSendLSB, Stage3crossWriteLSB.get, Stage3crossWriteMSB.get)
        crossLaneWrite.bits.mask := Mux(tryCrossWriteSendLSB, maskInStage3.get(1, 0), maskInStage3.get(3, 2))
        crossLaneWrite.valid := tryCrossWriteSendLSB || tryCrossWriteSendMSB

        when(crossLaneWriteReady && crossLaneWrite.valid) {
          sCrossWriteLSB.foreach(_ := true.B)
          when(sCrossWriteLSB.get) {
            sCrossWriteMSB.foreach(_ := true.B)
          }
        }
        // scheduler synchronization
        val schedulerFinish: Bool = (sSendResponse ++ wResponseFeedback).reduce(_ && _)

        // mask request
        laneResponse.valid := valid3.get && !sSendResponse.get
        laneResponse.bits.data := Mux(decodeResult(Decoder.ffo), ffoIndexRegInStage3.get, pipeDataInStage3.get)
        laneResponse.bits.toLSU := record.laneRequest.loadStore
        laneResponse.bits.instructionIndex := record.laneRequest.instructionIndex
        laneResponse.bits.ffoSuccess := record.selfCompleted

        sSendResponse.foreach(state => when(laneResponse.valid) { state := true.B})
        wResponseFeedback.foreach(state => when(laneResponseFeedback.valid) { state := true.B})

        when(laneResponseFeedback.valid && slotOccupied(index)) {
          when(laneResponseFeedback.bits.complete) { record.ffoByOtherLanes := true.B }
          assert(laneResponseFeedback.bits.instructionIndex === record.laneRequest.instructionIndex)
        }

        // enqueue write for last slot
        vrfWriteQueue.io.enq.valid := valid3.get && schedulerFinish && !decodeResult(Decoder.sWrite)

        // UInt(5.W) + UInt(3.W), use `+` here
        vrfWriteQueue.io.enq.bits.vd := record.laneRequest.vd + groupCounterInStage3.get(
          parameter.groupNumberBits - 1,
          parameter.vrfOffsetBits
        )

        vrfWriteQueue.io.enq.bits.offset := groupCounterInStage3.get

        /** what will write into vrf when ffo type instruction finished by other lanes */
        val completeWrite: UInt = Mux(record.laneRequest.mask, (~pipeDataInStage3.get).asUInt & pipeVDInStage3.get, 0.U)
        vrfWriteQueue.io.enq.bits.data := Mux(
          decodeResult(Decoder.nr),
          pipeDataInStage3.get,
          Mux(
            record.ffoByOtherLanes,
            completeWrite,
            executionResultInStage3.get
          )
        )
        vrfWriteQueue.io.enq.bits.last := DontCare
        vrfWriteQueue.io.enq.bits.instructionIndex := record.laneRequest.instructionIndex
        vrfWriteQueue.io.enq.bits.mask := maskInStage3.get

        // Handshake
        /** Cross-lane writing is over */
        val CrossLaneWriteOver: Bool = (sCrossWriteLSB ++ sCrossWriteMSB).reduce(_ && _)

        s3Ready := !valid3.get || (CrossLaneWriteOver && schedulerFinish && vrfWriteReady)
        s3DequeueFire.foreach(_ := valid3.get && CrossLaneWriteOver && schedulerFinish && vrfWriteReady)

        //Update the registers of stage3
        when(s3Fire) {
          groupCounterInStage3.foreach(_ := executionQueue.io.deq.bits.groupCounter)
          maskInStage3.foreach(_ := executionQueue.io.deq.bits.mask)
          executionResultInStage3.foreach(_ := executionResult)
          // todo: update maskFormatResult & reduceResult
          pipeDataInStage3.foreach(_ := Mux(
            decodeResult(Decoder.maskDestination),
            maskFormatResultForGroup.get,
            Mux(
              decodeResult(Decoder.red),
              reduceResult,
              executionQueue.io.deq.bits.pipeData.get
            )
          ))
          ffoIndexRegInStage3.foreach(_ := ffoIndexReg)
          pipeVDInStage3.foreach(_ := executionQueue.io.deq.bits.pipeVD.get)
          // cross write data
          Stage3crossWriteLSB.foreach(_ := Stage2crossWriteLSB.get)
          Stage3crossWriteMSB.foreach(_ := Stage2crossWriteMSB.get)
          // init state
          (sCrossWriteLSB ++ sCrossWriteMSB).foreach(_ := !decodeResult(Decoder.crossWrite))
          // todo: save mask destination result if needSendResponse at stage 2?
          (sSendResponse ++ wResponseFeedback).foreach(
            _ := decodeResult(Decoder.scheduler) || sSendResponseInStage2.get
          )

          // save scheduler data, todo: select result when update 'executionResultInStage3'
          schedulerResponseData.foreach { data =>
            data := Mux(
              record.laneRequest.decodeResult(Decoder.maskDestination),
              maskFormatResultForGroup.get,
              executionResultInStage3.get
            )
          }

          ffoSuccessImStage2.foreach(record.selfCompleted := _)
          // This group found means the next group ended early
          record.ffoByOtherLanes := record.ffoByOtherLanes || record.selfCompleted
        }
      } else {
        // Normal will be one level less
        vrfWriteQueue.io.enq.valid := s3Fire

        // UInt(5.W) + UInt(3.W), use `+` here
        vrfWriteQueue.io.enq.bits.vd := record.laneRequest.vd + executionQueue.io.deq.bits.groupCounter(
          parameter.groupNumberBits - 1,
          parameter.vrfOffsetBits
        )

        vrfWriteQueue.io.enq.bits.offset := executionQueue.io.deq.bits.groupCounter

        vrfWriteQueue.io.enq.bits.data := executionResult
        vrfWriteQueue.io.enq.bits.last := DontCare
        vrfWriteQueue.io.enq.bits.instructionIndex := record.laneRequest.instructionIndex
        vrfWriteQueue.io.enq.bits.mask := executionQueue.io.deq.bits.mask

        // Handshake
        s3Ready := vrfWriteQueue.io.enq.ready
      }
      s2Ready := !valid2 || (s2ExecuteOver && s3Ready && executionQueue.io.enq.ready)
      when(s2Fire ^ s3Fire) {valid2 := s2Fire}
      // s2 enqueue valid & s2 all ready except executionQueue
      executionQueue.io.enq.valid := s2Valid && ((s2ExecuteOver && s3Ready) || !valid2)
      executionQueue.io.deq.ready := s3Ready && s2ExecuteOver

      // --- stage 3 end & stage 4 start ---
      // vrfWriteQueue try to write vrf
      vrfWriteArbiter(index).valid := vrfWriteQueue.io.deq.valid
      vrfWriteArbiter(index).bits := vrfWriteQueue.io.deq.bits
      vrfWriteQueue.io.deq.ready := vrfWriteFire(index)

      pipeClear := !(Seq(valid0, valid1, valid2, vrfWriteQueue.io.deq.valid) ++ valid3).reduce(_ || _)
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
    /**
     * /** enqueue valid for execution unit */
     * val executeEnqueueValid: Vec[Bool] = Wire(Vec(parameter.chainingSize, Bool()))
     *
     * /** request from slot to vfu. */
     * val requestVec: Vec[LaneRequestToVFU] = Wire(Vec(parameter.chainingSize, new LaneRequestToVFU(parameter)))
     *
     * /** enqueue fire signal for execution unit */
     * val executeEnqueueFire: UInt = Wire(UInt(parameter.chainingSize.W))
     *
     * */
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
