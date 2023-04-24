package v

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle

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
  crossLaneVRFWriteEscapeQueueSize: Int)
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
  val maskGroupSize: Int = vLen / datapathWidth

  /** hardware width of [[maskGroupSize]]. */
  val maskGroupSizeBits: Int = log2Ceil(maskGroupSize)

  /** Parameter for [[VRF]] */
  def vrfParam: VRFParam = VRFParam(vLen, laneNumber, datapathWidth, chainingSize)

  /** Parameter for [[LaneShifter]]. */
  def shifterParameter: LaneShifterParameter = LaneShifterParameter(datapathWidth)

  /** Parameter for [[LaneMul]]. */
  def mulParam: LaneMulParam = LaneMulParam(datapathWidth, vLen)
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

  /** from [[V.lsu]] to [[Lane.vrf]], indicate it's the load store is finished, used for chaining.
    * because of load store index EEW, is complicated for lane to calculate whether LSU is finished.
    * let LSU directly tell each lane it is finished.
    */
  val lsuLastReport: UInt = IO(Input(UInt(parameter.chainingSize.W)))

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

  /** read from VRF vs1 for VFU */
  val source1: Vec[UInt] = RegInit(VecInit(Seq.fill(parameter.chainingSize)(0.U(parameter.datapathWidth.W))))

  /** read from VRF vs2 for VFU */
  val source2: Vec[UInt] = RegInit(VecInit(Seq.fill(parameter.chainingSize)(0.U(parameter.datapathWidth.W))))

  /** read from VRF rd for VFU */
  val source3: Vec[UInt] = RegInit(VecInit(Seq.fill(parameter.chainingSize)(0.U(parameter.datapathWidth.W))))

  /** execution result, write to VRF,
    * or goes to [[V]] for complex instructions like reduce
    */
  val result: Vec[UInt] = RegInit(VecInit(Seq.fill(parameter.chainingSize)(0.U(parameter.datapathWidth.W))))
  // 跨lane写的额外用寄存器存储执行的结果和mask

  // wait data for EEW = 2*SEW
  // TODO: do we need to switch to remote waiting?
  /** cross write LSB data to send out to other lanes. */
  val crossWriteDataLSBHalf: UInt = RegInit(0.U(parameter.datapathWidth.W))

  /** cross write LSB mask to send out to other lanes. */
  val crossWriteMaskLSBHalf: UInt = RegInit(0.U((parameter.dataPathByteWidth / 2).W))

  /** cross write MSB data to send out to other lanes. */
  val crossWriteDataMSBHalf: UInt = RegInit(0.U(parameter.datapathWidth.W))

  /** cross write MSB mask to send out to other lanes. */
  val crossWriteMaskMSBHalf: UInt = RegInit(0.U((parameter.dataPathByteWidth / 2).W))

  val maskFormatResult: UInt = RegInit(0.U(parameter.datapathWidth.W))
  val ffoIndexReg:      UInt = RegInit(0.U(log2Ceil(parameter.vLen / 8).W))
  val reduceResult:     Vec[UInt] = RegInit(VecInit(Seq.fill(parameter.chainingSize)(0.U(parameter.datapathWidth.W))))

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

  /** The slots start to shift in these rules:
    * - instruction can only enqueue to the last slot.
    * - all slots can only shift at the same time which means:
    *   if one slot is finished earlier -> 1101,
    *   it will wait for the first slot to finish -> 1100,
    *   and then take two cycles to move to xx11.
    */
  val slotCanShift: Vec[Bool] = Wire(Vec(parameter.chainingSize, Bool()))

  /** cross lane reading port from [[readBusPort]]
    * if [[ReadBusData.sinkIndex]] matches the index of this lane, dequeue from ring
    */
  val readBusDequeue: ValidIO[ReadBusData] = Wire(Valid(new ReadBusData(parameter: LaneParameter)))

  // control signals for VFU, see [[parameter.executeUnitNum]]
  /** enqueue valid for execution unit */
  val executeEnqueueValid: Vec[UInt] = Wire(Vec(parameter.chainingSize, UInt(parameter.executeUnitNum.W)))

  /** enqueue fire signal for execution unit */
  val executeEnqueueFire: UInt = Wire(UInt(parameter.executeUnitNum.W))

  /** for most of time, dequeue is enqueue,
    * for long latency FPU(divider), fire signal is from that unit
    */
  val executeDequeueFire: UInt = Wire(UInt(parameter.executeUnitNum.W))

  /** execution result for each VFU. */
  val executeDequeueData: Vec[UInt] = Wire(Vec(parameter.executeUnitNum, UInt(parameter.datapathWidth.W)))

  /** mask format result out put in adder. */
  val adderMaskResp: Bool = Wire(Bool())

  /** vxsat for saturate. */
  val vxsat: Bool = Wire(Bool())

  /** for each slot, it occupies which VFU. */
  val instructionTypeVec: Vec[UInt] = Wire(Vec(parameter.chainingSize, UInt(parameter.executeUnitNum.W)))

  /** a instruction is finished in this lane. */
  val instructionExecuteFinished: Vec[Bool] = Wire(Vec(parameter.chainingSize, Bool()))

  /** Instructions that read across lane will have an extra set of reads if vl is not aligned. */
  val instructionCrossReadFinished: Bool = Wire(Bool())

  /** valid for requesting mask unit. */
  val maskRequestValids: Vec[Bool] = Wire(Vec(parameter.chainingSize, Bool()))

  /** request for logic instruction type. */
  val logicRequests: Vec[MaskedLogicRequest] = Wire(
    Vec(parameter.chainingSize, new MaskedLogicRequest(parameter.datapathWidth))
  )

  /** request for adder instruction type. */
  val adderRequests: Vec[LaneAdderReq] = Wire(Vec(parameter.chainingSize, new LaneAdderReq(parameter.datapathWidth)))

  /** request for shift instruction type. */
  val shiftRequests: Vec[LaneShifterReq] = Wire(
    Vec(parameter.chainingSize, new LaneShifterReq(parameter.shifterParameter))
  )

  /** request for multipler instruction type. */
  val multiplerRequests: Vec[LaneMulReq] = Wire(Vec(parameter.chainingSize, new LaneMulReq(parameter.mulParam)))

  /** request for divider instruction type. */
  val dividerRequests: Vec[LaneDivRequest] = Wire(
    Vec(parameter.chainingSize, new LaneDivRequest(parameter.datapathWidth))
  )
  val lastDivWriteIndexWire: UInt = Wire(UInt(log2Ceil(parameter.dataPathByteWidth).W))
  val divWrite:              Bool = Wire(Bool())
  val divBusy:               Bool = Wire(Bool())
  val lastDivWriteIndex:     UInt = RegEnable(lastDivWriteIndexWire, 0.U.asTypeOf(lastDivWriteIndexWire), divWrite)
  val divWriteIndex: UInt =
    Mux(divWrite, lastDivWriteIndexWire, lastDivWriteIndex)(log2Ceil(parameter.dataPathByteWidth) - 1, 0)

  /** request for other instruction type. */
  val otherRequests: Vec[OtherUnitReq] = Wire(Vec(parameter.chainingSize, Output(new OtherUnitReq(parameter))))

  val otherResponse: OtherUnitResp = Wire(Output(new OtherUnitResp(parameter.datapathWidth)))

  /** request for mask instruction type. */
  val maskRequests: Vec[LaneResponse] = Wire(Vec(parameter.chainingSize, Output(new LaneResponse(parameter))))

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

  // Slot logic ~1.2k lines
  slotControl.zipWithIndex.foreach {
    case (record, index) =>
      /** The execution is finished
        * - all execution got its result.
        * - VRF written is finished.
        * - not coupling to cross lane read/write.
        */
      val executeFinish =
        // TODO: why we need `sExecute`?
        record.state.sExecute &&
          record.state.wExecuteRes &&
          record.state.sWrite

      /** cross write is done. */
      val crossWriteFinish: Bool = record.state.sCrossWriteLSB && record.state.sCrossWriteMSB

      /** send cross read result is done. */
      val sendCrossReadResultFinish: Bool = record.state.sSendCrossReadResultLSB && record.state.sSendCrossReadResultMSB

      /** transaction between [[Lane]] and [[V]] is done. */
      val schedulerFinish: Bool = record.state.wScheduler && record.state.sScheduler

      /** need mask from [[V]]. */
      val needMaskSource: Bool = record.laneRequest.mask

      /** all read from VRF is done.
        * it need additional one cycle to indicate there is no read in VRF since the read latency of VRF is 1.
        */
      val readVrfRequestFinish: Bool = record.state.sReadVD && record.state.sRead1 && record.state.sRead2

      /** for non-masked instruction, always ready,
        * for masked instruction, need to wait for mask
        */
      val maskReady: Bool = record.mask.valid || !needMaskSource

      /** onehot value of SEW. */
      val vSew1H: UInt = UIntToOH(record.csr.vSew)

      /** the current index of execution. */
      val elementIndex: UInt = Mux1H(
        vSew1H(2, 0),
        Seq(
          // SEW = 8
          (record.groupCounter ## record.executeIndex)(4, 0),
          // SEW = 16
          (record.groupCounter ## record.executeIndex(1))(4, 0),
          // SEW = 32
          record.groupCounter
        )
      )

      /** mask for current element. */
      val maskBits: Bool = record.mask.bits(elementIndex(parameter.datapathWidthBits - 1, 0))

      /** mask bit which will be sent to execution unit.(e.g. input of `adc`) */
      val maskAsInput: Bool = maskBits && (record.laneRequest.decodeResult(Decoder.maskSource) ||
        record.laneRequest.decodeResult(Decoder.gather))

      /** if asserted, the element won't be executed.
        * adc: vm = 0; madc: vm = 0 -> s0 + s1 + c, vm = 1 -> s0 + s1
        */
      val skipEnable: Bool = record.laneRequest.mask &&
        !record.laneRequest.decodeResult(Decoder.maskSource) &&
        !record.laneRequest.decodeResult(Decoder.maskLogic)

      /** This element is skipped due to skipEnable and corresponding mask is 0. */
      val masked: Bool = skipEnable && !maskBits

      // find the next unmasked element.
      /**
        * TODO: fixme logic here.
        */
      val maskForExecutionGroup: UInt = Mux1H(
        Seq(skipEnable && record.mask.valid, !skipEnable),
        Seq(record.mask.bits, (-1.S(parameter.datapathWidth.W)).asUInt)
      )

      /** the current 1H of the executing element in the group. */
      val current1H = UIntToOH(elementIndex(4, 0))

      /** the next element to execute in the group. */
      val next1H =
        ffo((scanLeftOr(current1H) ## false.B) & maskForExecutionGroup)(parameter.datapathWidth - 1, 0)

      /** the index to write to VRF in [[parameter.dataPathByteWidth]].
        * for long latency pipe, the index will follow the pipeline.
        */
      val writeIndex = Mux(
        record.laneRequest.decodeResult(Decoder.divider),
        divWriteIndex,
        record.executeIndex
      )

      /** VRF byte level mask */
      val writeMaskInByte = Mux1H(
        vSew1H(2, 0),
        Seq(
          // TODO: move UIntToOH out
          UIntToOH(writeIndex),
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

      /** is there any data left in this group? */
      val nextOrR: Bool = next1H.orR

      /** the index of next element in this group.(0-31) */
      val nextIndex: UInt = OHToUInt(next1H)

      /** a mask group can represent 8 execution groups.
        *
        * the [[groupCounter]] being all `1` in corresponding SEW indicates
        * this is the last execution group of the mask group.
        *
        * when assert, mask group should be updated in next cycle.
        */
      val lastGroupForMask = Mux1H(
        vSew1H(2, 0),
        Seq(
          record.groupCounter(log2Ceil(parameter.maskGroupWidth / 4) - 1, 0).andR,
          record.groupCounter(log2Ceil(parameter.maskGroupWidth / 2) - 1, 0).andR,
          record.groupCounter(log2Ceil(parameter.maskGroupWidth) - 1, 0).andR
        )
      )

      /** need to update mask when
        * - the last execution group in the mask group
        * - the instruction is finished
        */
      val canUpdateVRFMaskFormat: Bool = lastGroupForMask || instructionExecuteFinished(index)

      /** if the instruction type is `maskDestination`, for each lanes,
        * the result of execution unit should be updated to VRF in mask format,
        * this is a bit level cross lane access, which cannot use the cross lane channel,
        * thus we use [[LaneResponse]] to update to [[V]]
        * and [[V]] should regroup it and use [[vrfWriteChannel]] to send to each [[Lane]]
        */
      val maskTypeDestinationWriteValid: Bool =
        record.laneRequest.decodeResult(Decoder.maskDestination) &&
          canUpdateVRFMaskFormat

      /** the instruction type in the slot is a reduce type. */
      val reduceType: Bool = record.laneRequest.decodeResult(Decoder.red)

      /** TODO: change to decode. */
      val updateReduce = (
        record.laneRequest.decodeResult(Decoder.red) ||
          record.laneRequest.decodeResult(Decoder.popCount)
      ) && !record.laneRequest.loadStore

      /** the instruction type in the slot is a readOnly type. */
      val readOnly: Bool = record.laneRequest.decodeResult(Decoder.readOnly)

      /** reduce should send feedback to at the last cycle of the execution group. */
      val reduceValid = updateReduce && instructionExecuteFinished(index)

      /** [[Lane]] need response from [[V]] for some instructions.
        * TODO: use decoder
        */
      val needResponse: Bool = (record.laneRequest.loadStore || reduceValid || readOnly ||
        maskTypeDestinationWriteValid || record.laneRequest.decodeResult(Decoder.ffo)) && slotActive(index)

      // CSR
      /** if `vl = N`, the last element index is `N-1`, for each `x`, is a bit inside `vl`
        *
        * xxxxx   xxx xx -> vsew = 0 (1 element -> 8 bits)
        * xxxxxx  xxx  x -> vsew = 1 (1 element -> 16 bits)
        * xxxxxxx xxx    -> vsew = 2 (1 element -> 32 bits)
        * |       |   |
        *             execute index
        *         lane index
        * group index
        *
        * TODO: we truncate the vl from TOP, do we need - 1 in [[V]]?
        *       take care of `vl = 0`
        */
      val lastElementIndex: UInt = (record.csr.vl - 1.U)(parameter.vlMaxBits - 2, 0)

      /** For an instruction, the last group is not executed by all lanes,
        * here is the index of last group of the instruction
        */
      val lastGroupIndex: UInt = Mux1H(
        vSew1H(2, 0),
        Seq(
          lastElementIndex(parameter.vlMaxBits - 2, parameter.laneNumberBits + 2),
          lastElementIndex(parameter.vlMaxBits - 2, parameter.laneNumberBits + 1),
          lastElementIndex(parameter.vlMaxBits - 2, parameter.laneNumberBits)
        )
      )

      /** the lane that the last element locate */
      val lastLaneIndex: UInt = Mux1H(
        vSew1H(2, 0),
        Seq(
          lastElementIndex(parameter.laneNumberBits + 2 - 1, 2),
          lastElementIndex(parameter.laneNumberBits + 1 - 1, 1),
          lastElementIndex(parameter.laneNumberBits - 1, 0)
        )
      )

      /** Used to calculate the last group of mask, which will only be effective when [[isEndLane]] */
      val lastElementExecuteIndex: UInt = Mux1H(
        vSew1H(1, 0),
        Seq(
          lastElementIndex(1, 0),
          lastElementIndex(0) ## false.B
        )
      )

      /** The relative position of the last lane determines the processing of the last group. */
      val lanePositionLargerThanEndLane: Bool = laneIndex > lastLaneIndex

      /** This lane is the last group. */
      val isEndLane: Bool = laneIndex === lastLaneIndex

      /** for this lane, which group is the last group. */
      val lastGroupForLane: UInt = lastGroupIndex - lanePositionLargerThanEndLane

      /** when [[InstructionControlRecord.executeIndex]] reaches [[slotGroupFinishedIndex]], the group in the slot is finished.
        * 00 -> 11
        * 01 -> 10
        * 10 -> 00
        *
        * TODO: 64bit
        */
      val slotGroupFinishedIndex: UInt = !record.csr.vSew(1) ## !record.csr.vSew.orR

      /** mask logic is in bit level granularity:
        *
        * xxx   xxx     xxxxx(1 element -> 1 bits)
        * |     |       |
        *               execute index
        *       lane index
        * group index
        * TODO: better name.
        */
      val vlTail: UInt = record.csr.vl(parameter.datapathWidthBits - 1, 0)

      /** lane index in [[vlTail]]
        * TODO: better name.
        */
      val vlBody: UInt =
        record.csr.vl(parameter.datapathWidthBits + parameter.laneNumberBits - 1, parameter.datapathWidthBits)

      /** group index in [[vlTail]]
        * TODO: better name.
        */
      val vlHead: UInt = record.csr.vl(parameter.vlMaxBits - 1, parameter.datapathWidthBits + parameter.laneNumberBits)

      /** use mask to fix the case that `vl` is not in the multiple of [[parameter.datapathWidth]].
        * it will fill the LSB of mask to `0`, mask it to not execute those elements.
        */
      val lastGroupMask = scanRightOr(UIntToOH(vlTail)) >> 1

      /** `vl` is not in the multiple of [[parameter.datapathWidth]]. */
      val dataPathMisaligned = vlTail.orR

      /** how many groups will be executed for the instruction.
        * if [[dataPathMisaligned]], the last group will be executed.
        */
      val maskLogicGroupCount = (vlHead ## vlBody) - !dataPathMisaligned

      /** which lane should execute the last group. */
      val lastLaneIndexForMaskLogic: UInt = maskLogicGroupCount(parameter.laneNumberBits - 1, 0)

      /** should this lane execute the last group? */
      val isLastLaneForMaskLogic: Bool = lastLaneIndexForMaskLogic === laneIndex

      /** TODO: bug? */
      val lastGroupCountForMaskLogic: UInt = maskLogicGroupCount >> parameter.laneNumberBits

      /** for mask logic, the group is the last group. */
      val lastGroupForMaskLogic: Bool = lastGroupCountForMaskLogic === record.groupCounter

      /** mask logic will complete at the end of group.
        * TODO: move `&& record.laneRequest.decodeResult(Decoder.maskLogic)` to [[lastGroupForMaskLogic]]
        */
      val maskLogicWillCompleted: Bool = lastGroupForMaskLogic && record.laneRequest.decodeResult(Decoder.maskLogic)

      /** the last element is inside this group. */
      val bordersForMaskLogic: Bool = lastGroupForMaskLogic && isLastLaneForMaskLogic &&
        dataPathMisaligned && record.laneRequest.decodeResult(Decoder.maskLogic)

      /** if [[bordersForMaskLogic]], use [[lastGroupMask]] to mask the result otherwise use [[fullMask]]. */
      val maskCorrect = Mux(bordersForMaskLogic, lastGroupMask, fullMask)

      /** no need to waif for [[laneResponseFeedback]]
        * TODO: use decoder.
        */
      val noFeedBack: Bool = !(readOnly || record.laneRequest.loadStore)

      /** TODO: move to [[V]]. */
      val nr = record.laneRequest.decodeResult(Decoder.nr)

      /** the execution unit need more than one cycle,
        * divider.
        */
      val longLatency: Bool = instructionTypeVec(index)(4)

      /** the long latency execution unit is masked. */
      val maskedLongLatency: Bool = masked && longLatency
      if (index != 0) {
        // read only
        val decodeResult: DecodeBundle = record.laneRequest.decodeResult
        val needCrossRead = decodeResult(Decoder.crossRead)
        val needCrossWrite = decodeResult(Decoder.crossWrite)

        /** select from VFU, send to [[result]], [[crossWriteDataLSBHalf]], [[crossWriteDataMSBHalf]]. */
        val dataDequeue: UInt = Mux1H(instructionTypeVec(index), executeDequeueData)

        /** fire of [[dataDequeue]] */
        val dataDequeueFire: Bool = (instructionTypeVec(index) & executeDequeueFire).orR

        /** [[record.groupCounter]] & [[record.executeIndex]] is used as index of current data sending to VFU.
          * By default, data is not masked. due the the logic in [[next1H]], it is not masked.
          * when updating [[record.mask.bits]], the pointer is updated to the first item of mask group.
          * but this element might be masked.
          * So firstMasked is used to take care of this.
          */
        /*val firstMasked: Bool = Wire(Bool())*/
        // TODO: move this to verification module
        when(needCrossRead) {
          assert(record.csr.vSew != 2.U)
        }
        slotActive(index) :=
          // slot should alive
          slotOccupied(index) &&
          // head should alive, if not, the slot should shift to make head alive
          slotOccupied.head &&
          // cross lane instruction should execute in the first slot
          !record.laneRequest.decodeResult(Decoder.specialSlot) &&
          // mask should ready for masked instruction
          maskReady

        // wait read result
        val readNext0 = RegNext(vrfReadRequest(index)(0).fire)
        val readNext1 = RegNext(vrfReadRequest(index)(1).fire)
        val readNext2 = RegNext(vrfReadRequest(index)(2).fire)
        // shift slot // !record.state.sExecute
        slotCanShift(index) := !((readNext0 || readNext1 || readNext2) && slotOccupied(index))

        // vs1 read
        vrfReadRequest(index)(0).valid := !record.state.sRead1 && slotActive(index)
        vrfReadRequest(index)(0).bits.offset := record.groupCounter(parameter.vrfOffsetBits - 1, 0)
        // todo: when vlmul > 0 use ## rather than +
        vrfReadRequest(index)(0).bits.vs := record.laneRequest.vs1 + record.groupCounter(
          parameter.groupNumberBits - 1,
          parameter.vrfOffsetBits
        )
        // used for hazard detection
        vrfReadRequest(index)(0).bits.instructionIndex := record.laneRequest.instructionIndex

        // vs2 read
        vrfReadRequest(index)(1).valid := !record.state.sRead2 && slotActive(index)
        vrfReadRequest(index)(1).bits.offset := record.groupCounter(parameter.vrfOffsetBits - 1, 0)
        // todo: when vlmul > 0 use ## rather than +
        // TODO: pull Mux to standalone signal
        vrfReadRequest(index)(1).bits.vs := record.laneRequest.vs2 +
          record.groupCounter(parameter.groupNumberBits - 1, parameter.vrfOffsetBits)
        vrfReadRequest(index)(1).bits.instructionIndex := record.laneRequest.instructionIndex

        // vd read
        vrfReadRequest(index)(2).valid := !record.state.sReadVD && slotActive(index)
        vrfReadRequest(index)(2).bits.offset := record.groupCounter(parameter.vrfOffsetBits - 1, 0)
        vrfReadRequest(index)(2).bits.vs := record.laneRequest.vd +
          record.groupCounter(parameter.groupNumberBits - 1, parameter.vrfOffsetBits)
        // for hazard detection
        vrfReadRequest(index)(2).bits.instructionIndex := record.laneRequest.instructionIndex

        /** all read operation is finished. */
        val readFinish = RegNext(readVrfRequestFinish) && readVrfRequestFinish

        // state machine control
        when(vrfReadRequest(index)(0).fire) {
          record.state.sRead1 := true.B
        }
        when(readNext0) {
          source1(index) := vrfReadResult(index)(0)
        }
        when(vrfReadRequest(index)(1).fire) {
          record.state.sRead2 := true.B
        }
        when(readNext1) {
          source2(index) := vrfReadResult(index)(1)
        }
        when(vrfReadRequest(index)(2).fire) {
          record.state.sReadVD := true.B
          source3(index) := vrfReadResult(index)(2)
        }
        when(readNext2) {
          source3(index) := vrfReadResult(index)(2)
        }

        /** 这一组的mask已经没有剩余了 */
        val maskNeedUpdate = !nextOrR
        val nextGroupCountMSB: UInt = Mux1H(
          vSew1H(1, 0),
          Seq(
            record.groupCounter(parameter.groupNumberBits - 1, parameter.groupNumberBits - 3),
            false.B ## record.groupCounter(parameter.groupNumberBits - 1, parameter.groupNumberBits - 2)
          )
        ) + maskNeedUpdate
        val indexInLane = nextGroupCountMSB ## nextIndex
        // csrInterface.vSew 只会取值0, 1, 2,需要特别处理移位
        val nextIntermediateVolume = (indexInLane << record.csr.vSew).asUInt
        val nextGroupCount = nextIntermediateVolume(parameter.groupNumberBits + 1, 2)
        val nextExecuteIndex = nextIntermediateVolume(1, 0)

        /** 虽然没有计算完一组,但是这一组剩余的都被mask去掉了 */
        val maskFilterEnd = skipEnable && (nextGroupCount =/= record.groupCounter)

        /** 需要一个除vl导致的end来决定下一个的 element index 是什么 */
        val dataDepletion = writeIndex === slotGroupFinishedIndex || maskFilterEnd

        /** 这一组计算全完成了 */
        val groupEnd = dataDepletion || instructionExecuteFinished(index)

        /** 计算当前这一组的 vrf mask
          * 已知：mask mask1H executeIndex
          * sew match:
          * 0:
          * executeIndex match:
          * 0: 0001
          * 1: 0010
          * 2: 0100
          * 3: 1000
          * 1:
          * executeIndex(0) match:
          * 0: 0011
          * 1: 1100
          * 2:
          * 1111
          */
        val executeByteEnable = Mux1H(
          vSew1H(2, 0),
          Seq(
            UIntToOH(record.executeIndex),
            record.executeIndex(1) ## record.executeIndex(1) ## !record.executeIndex(1) ## !record.executeIndex(1),
            15.U(4.W)
          )
        )
        val executeBitEnable: UInt = FillInterleaved(8, executeByteEnable)

        def CollapseOperand(data: UInt, enable: Bool = true.B, sign: Bool = false.B): UInt = {
          val dataMasked: UInt = data & executeBitEnable
          val select:     UInt = Mux(enable, vSew1H(2, 0), 4.U(3.W))
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

        // 处理操作数
        /**
          * src1： src1有 IXV 三种类型,只有V类型的需要移位
          */
        val finalSource1 = CollapseOperand(
          Mux(reduceType, reduceResult(index), source1(index)),
          decodeResult(Decoder.vtype) && !reduceType,
          !decodeResult(Decoder.unsigned0)
        )

        /** source2 一定是V类型的 */
        val finalSource2 = CollapseOperand(source2(index), true.B, !decodeResult(Decoder.unsigned1))

        /** source3 有两种：adc & ma, c等处理mask的时候再处理 */
        val finalSource3 = CollapseOperand(source3(index))

        val nextElementIndex = Mux1H(
          vSew1H,
          Seq(
            indexInLane(indexInLane.getWidth - 1, 2) ## laneIndex ## indexInLane(1, 0),
            indexInLane(indexInLane.getWidth - 1, 1) ## laneIndex ## indexInLane(0),
            indexInLane ## laneIndex
          )
        )
        instructionExecuteFinished(index) := nextElementIndex >= record.csr.vl || maskLogicWillCompleted
        // 假如这个单元执行的是logic的类型的,请求应该是什么样子的
        val logicRequest = Wire(new MaskedLogicRequest(parameter.datapathWidth))
        logicRequest.src.head := finalSource2
        logicRequest.src(1) := finalSource1
        logicRequest.src(2) := maskCorrect
        logicRequest.src(3) := finalSource3
        logicRequest.opcode := decodeResult(Decoder.uop)
        // 在手动做Mux1H
        logicRequests(index) := maskAnd(
          slotOccupied(index) && decodeResult(Decoder.logic) && !decodeResult(Decoder.other),
          logicRequest
        )

        // adder 的
        val adderRequest = Wire(new LaneAdderReq(parameter.datapathWidth))
        adderRequest.src := VecInit(Seq(finalSource1, finalSource2))
        adderRequest.mask := maskAsInput
        adderRequest.opcode := decodeResult(Decoder.uop)
        adderRequest.sign := !decodeResult(Decoder.unsigned1)
        adderRequest.reverse := decodeResult(Decoder.reverse)
        adderRequest.average := decodeResult(Decoder.average)
        adderRequest.saturat := decodeResult(Decoder.saturate)
        adderRequest.vxrm := record.csr.vxrm
        adderRequest.vSew := record.csr.vSew
        adderRequests(index) := maskAnd(
          slotOccupied(index) && decodeResult(Decoder.adder) && !decodeResult(Decoder.other),
          adderRequest
        )

        // shift 的
        val shiftRequest = Wire(new LaneShifterReq(parameter.shifterParameter))
        shiftRequest.src := finalSource2
        shiftRequest.shifterSize := Mux1H(
          vSew1H(2, 1),
          Seq(false.B ## finalSource1(3), finalSource1(4, 3))
        ) ## finalSource1(2, 0)
        shiftRequest.opcode := decodeResult(Decoder.uop)
        shiftRequests(index) := maskAnd(
          slotOccupied(index) && decodeResult(Decoder.shift) && !decodeResult(Decoder.other),
          shiftRequest
        )
        shiftRequest.vxrm := record.csr.vxrm

        // mul
        val mulRequest: LaneMulReq = Wire(new LaneMulReq(parameter.mulParam))
        mulRequest.src := VecInit(Seq(finalSource1, finalSource2, finalSource3))
        mulRequest.opcode := decodeResult(Decoder.uop)
        mulRequest.saturate := decodeResult(Decoder.saturate)
        mulRequest.vSew := record.csr.vSew
        mulRequest.vxrm := record.csr.vxrm
        multiplerRequests(index) := maskAnd(
          slotOccupied(index) && decodeResult(Decoder.multiplier) && !decodeResult(Decoder.other),
          mulRequest
        )

        // div
        val divRequest = Wire(new LaneDivRequest(parameter.datapathWidth))
        divRequest.src := VecInit(Seq(finalSource1, finalSource2))
        divRequest.rem := decodeResult(Decoder.uop)(0)
        divRequest.sign := !decodeResult(Decoder.unsigned0)
        divRequest.index := record.executeIndex
        dividerRequests(index) := maskAnd(
          slotOccupied(index) && decodeResult(Decoder.divider) && !decodeResult(Decoder.other),
          divRequest
        )

        // other
        val otherRequest: OtherUnitReq = Wire(Output(new OtherUnitReq(parameter)))
        otherRequest.src := VecInit(Seq(finalSource1, finalSource2, finalSource3))
        otherRequest.popInit := reduceResult(index)
        otherRequest.opcode := decodeResult(Decoder.uop)(2, 0)
        otherRequest.specialOpcode := decodeResult(Decoder.specialUop)
        otherRequest.imm := record.laneRequest.vs1
        otherRequest.extendType.valid := decodeResult(Decoder.uop)(3)
        otherRequest.extendType.bits.elements.foreach { case (s, d) => d := decodeResult.elements(s) }
        otherRequest.laneIndex := laneIndex
        otherRequest.groupIndex := record.groupCounter
        otherRequest.executeIndex := record.executeIndex
        otherRequest.sign := !decodeResult(Decoder.unsigned0)
        otherRequest.mask := maskAsInput || !record.laneRequest.mask
        otherRequest.complete := record.schedulerComplete || record.selfCompleted
        otherRequest.maskType := record.laneRequest.mask
        otherRequest.vSew := record.csr.vSew
        otherRequest.vxrm := record.csr.vxrm
        otherRequests(index) := maskAnd(slotOccupied(index) && decodeResult(Decoder.other), otherRequest)

        // 往scheduler的执行任务compress viota
        val maskRequest: LaneResponse = Wire(Output(new LaneResponse(parameter)))
        val canSendMaskRequest = needResponse && readFinish && record.state.sExecute
        val maskValid = canSendMaskRequest && !record.state.sScheduler
        val noNeedWaitScheduler: Bool = !(canSendMaskRequest && !record.initState.sScheduler) || schedulerFinish
        // 往外边发的是原始的数据
        maskRequest.data := Mux(
          record.laneRequest.decodeResult(Decoder.maskDestination),
          maskFormatResult,
          Mux(
            updateReduce,
            reduceResult(index),
            source2(index)
          )
        )
        maskRequest.toLSU := record.laneRequest.loadStore
        maskRequest.instructionIndex := record.laneRequest.instructionIndex
        maskRequest.ffoSuccess := record.selfCompleted
        maskRequests(index) := maskAnd(slotOccupied(index) && maskValid, maskRequest)
        maskRequestValids(index) := maskValid

        when(
          laneResponseFeedback.valid && laneResponseFeedback.bits.instructionIndex === record.laneRequest.instructionIndex
        ) {
          record.state.wScheduler := true.B
        }
        when(maskValid) {
          record.state.sScheduler := true.B
        }
        instructionTypeVec(index) := record.laneRequest.instType
        executeEnqueueValid(index) := maskAnd(readFinish && !record.state.sExecute, instructionTypeVec(index))
        when((instructionTypeVec(index) & executeEnqueueFire).orR) {
          when(groupEnd) {
            record.state.sExecute := true.B
          }.otherwise {
            record.executeIndex := nextExecuteIndex
          }
        }

        // todo: 暂时先这样,处理mask的时候需要修
        // TODO: hardware effort is too large since 5 bits dynamic shifting is too large.
        val executeResult = (dataDequeue << dataOffset).asUInt(parameter.datapathWidth - 1, 0)
        val resultUpdate: UInt = (executeResult & writeMaskInBit) | (result(index) & (~writeMaskInBit).asUInt)
        when(dataDequeueFire) {
          when(groupEnd) {
            record.state.wExecuteRes := true.B
          }
          result(index) := resultUpdate
          record.selfCompleted := otherResponse.ffoSuccess
          when(!masked) {
            record.vrfWriteMask := record.vrfWriteMask | executeByteEnable
            when(updateReduce) {
              reduceResult(index) := dataDequeue
            }
          }
        }
        // 写rf
        vrfWriteArbiter(index).valid := record.state.wExecuteRes && !record.state.sWrite &&
        slotActive(index) && noNeedWaitScheduler
        vrfWriteArbiter(index).bits.vd := record.laneRequest.vd + record.groupCounter(
          parameter.groupNumberBits - 1,
          parameter.vrfOffsetBits
        )
        vrfWriteArbiter(index).bits.offset := record.groupCounter
        vrfWriteArbiter(index).bits.data := Mux(record.schedulerComplete, 0.U, result(index))
        vrfWriteArbiter(index).bits.last := instructionExecuteFinished(index)
        vrfWriteArbiter(index).bits.instructionIndex := record.laneRequest.instructionIndex
        vrfWriteArbiter(index).bits.mask := record.vrfWriteMask
        when(vrfWriteFire(index)) {
          record.state.sWrite := true.B
        }
        instructionFinishedVec(index) := 0.U(parameter.chainingSize.W)
        val maskUnhindered = maskRequestFireOH(index) || !maskNeedUpdate
        when((record.state.asUInt.andR && maskUnhindered) || record.instructionFinished) {
          when((instructionExecuteFinished(index) && noFeedBack) || record.instructionFinished) {
            slotOccupied(index) := false.B
            when(slotOccupied(index)) {
              instructionFinishedVec(index) := UIntToOH(
                record.laneRequest.instructionIndex(parameter.instructionIndexBits - 2, 0)
              )
            }
          }.otherwise {
            record.state := record.initState
            record.groupCounter := nextGroupCount
            record.executeIndex := nextExecuteIndex
            record.vrfWriteMask := 0.U
            when(maskRequestFireOH(index)) {
              record.mask.valid := true.B
              record.mask.bits := maskInput
              record.maskGroupedOrR := maskGroupedOrR
            }
          }
        }
        when(
          laneResponseFeedback.bits.complete && laneResponseFeedback.bits.instructionIndex === record.laneRequest.instructionIndex
        ) {
          // 例如:别的lane找到了第一个1
          record.schedulerComplete := true.B
          when(record.initState.wExecuteRes) {
            slotOccupied(index) := false.B
          }
        }
        // mask 更换
        slotMaskRequestVec(index).valid := maskNeedUpdate
        slotMaskRequestVec(index).bits := nextGroupCountMSB
      } else { // slotNumber == 0
        val decodeResult: DecodeBundle = record.laneRequest.decodeResult
        val needCrossRead = decodeResult(Decoder.crossRead)

        /** cross read has two case
          * - read vs2
          * - read vd: only vwmacc
          */
        val crossReadVS2: Bool = needCrossRead && !decodeResult(Decoder.vwmacc)

        /** State machine may jump through the group if the mask is all 0.
          * For these case it cannot jump through:
          * [[needCrossRead]]: although we may not need execution unit if it is masked,
          *                    we still need to access VRF for another lane
          * [[record.laneRequest.special]]: We need to synchronize with [[V]] every group
          *                                 TODO: uarch about the synchronization
          * [[nr]] will ignore mask
          */
        val alwaysNextGroup: Bool = needCrossRead || record.laneRequest.special || nr

        /** select from VFU, send to [[result]], [[crossWriteDataLSBHalf]], [[crossWriteDataMSBHalf]]. */
        val dataDequeue: UInt = Mux1H(instructionTypeVec(index), executeDequeueData)

        /** fire of [[dataDequeue]] */
        val dataDequeueFire: Bool = (instructionTypeVec(index) & executeDequeueFire).orR

        // TODO: move this to verification module
        when(needCrossRead) {
          assert(record.csr.vSew != 2.U)
        }

        // TODO: for index = 0, slotOccupied(index) === slotOccupied.head
        slotActive(index) :=
          // slot should alive
          slotOccupied(index) &&
          // head should alive, if not, the slot should shift to make head alive
          slotOccupied.head &&
          // mask should ready for masked instruction
          maskReady

        // shift slot
        slotCanShift(index) := !(record.state.sExecute && slotOccupied(index))

        // vs1 read
        vrfReadRequest(index)(0).valid := !record.state.sRead1 && slotActive(index)
        vrfReadRequest(index)(0).bits.offset := record.groupCounter(parameter.vrfOffsetBits - 1, 0)
        vrfReadRequest(index)(0).bits.vs := Mux(
          record.laneRequest.decodeResult(Decoder.maskLogic) &&
            !record.laneRequest.decodeResult(Decoder.logic),
          // read v0 for (15. Vector Mask Instructions)
          0.U,
          // todo: when vlmul > 0 use ## rather than +
          record.laneRequest.vs1 + record.groupCounter(
            parameter.groupNumberBits - 1,
            parameter.vrfOffsetBits
          )
        )
        // used for hazard detection
        vrfReadRequest(index)(0).bits.instructionIndex := record.laneRequest.instructionIndex

        // vs2 read
        vrfReadRequest(index)(1).valid := !(record.state.sRead2 && record.state.sCrossReadLSB) && slotActive(index)
        vrfReadRequest(index)(1).bits.offset := Mux(
          record.state.sRead2,
          // cross lane LSB
          record.groupCounter(parameter.vrfOffsetBits - 2, 0) ## false.B,
          // normal read
          record.groupCounter(parameter.vrfOffsetBits - 1, 0)
        )
        // todo: when vlmul > 0 use ## rather than +
        // TODO: pull Mux to standalone signal
        vrfReadRequest(index)(1).bits.vs := Mux(
          record.laneRequest.decodeResult(Decoder.vwmacc) && record.state.sRead2,
          // cross read vd for vwmacc, since it need dual [[dataPathWidth]], use vs2 port to read LSB part of it.
          record.laneRequest.vd,
          // read vs2 for other instruction
          record.laneRequest.vs2
        ) + Mux(
          record.state.sRead2,
          // cross lane
          record.groupCounter(parameter.groupNumberBits - 2, parameter.vrfOffsetBits - 1),
          // no cross lane
          record.groupCounter(parameter.groupNumberBits - 1, parameter.vrfOffsetBits)
        )
        vrfReadRequest(index)(1).bits.instructionIndex := record.laneRequest.instructionIndex

        // vd read
        vrfReadRequest(index)(2).valid := !(record.state.sReadVD && record.state.sCrossReadMSB) && slotActive(index)
        vrfReadRequest(index)(2).bits.offset := Mux(
          record.state.sReadVD,
          // cross lane MSB
          record.groupCounter(parameter.vrfOffsetBits - 2, 0) ## true.B,
          // normal read
          record.groupCounter(parameter.vrfOffsetBits - 1, 0)
        )
        vrfReadRequest(index)(2).bits.vs := Mux(
          record.state.sReadVD && !record.laneRequest.decodeResult(Decoder.vwmacc),
          // cross lane access use vs2
          record.laneRequest.vs2,
          // normal read vd or cross read vd for vwmacc
          record.laneRequest.vd
        ) +
          Mux(
            record.state.sReadVD,
            record.groupCounter(parameter.groupNumberBits - 2, parameter.vrfOffsetBits - 1),
            record.groupCounter(parameter.groupNumberBits - 1, parameter.vrfOffsetBits)
          )
        // for hazard detection
        vrfReadRequest(index)(2).bits.instructionIndex := record.laneRequest.instructionIndex

        /** all read operation is finished. */
        val readFinish =
          // wait one cycle for VRF read latency.
          RegNext(readVrfRequestFinish, false.B) &&
            // RegNext(readVrfRequestFinish) is not initialized, to avoid the invalid value of last group,
            // fanout readVrfRequestFinish directly.
            readVrfRequestFinish &&
            // wait for cross lane read result
            record.state.wCrossReadLSB &&
            record.state.wCrossReadMSB &&
            record.state.sCrossReadLSB &&
            record.state.sCrossReadMSB

        // state machine control
        // change state machine when read source1
        when(vrfReadRequest(index)(0).fire) {
          record.state.sRead1 := true.B
        }

        // read result from source1 need read latency
        when(RegNext(vrfReadRequest(index)(0).fire)) {
          // todo: datapath Mux
          source1(index) := vrfReadResult(index)(0)
        }

        // the priority of `sRead2` is higher than `sCrossReadLSB`
        when(vrfReadRequest(index)(1).fire) {
          record.state.sRead2 := true.B
          when(record.state.sRead2) {
            record.state.sCrossReadLSB := true.B
          }
        }

        // read result from source2 need read latency
        when(RegNext(vrfReadRequest(index)(1).fire)) {
          when(RegNext(record.state.sRead2)) {
            crossReadLSBOut := vrfReadResult(index)(1)
          }.otherwise {
            source2(index) := vrfReadResult(index)(1)
          }
        }

        // the priority of `sReadVD` is higher than `sCrossReadMSB`
        when(vrfReadRequest(index)(2).fire) {
          record.state.sReadVD := true.B
          when(record.state.sReadVD) {
            record.state.sCrossReadMSB := true.B
          }
        }

        // read result from vd need read latency
        when(RegNext(vrfReadRequest(index)(2).fire)) {
          when(RegNext(record.state.sReadVD)) {
            crossReadMSBOut := vrfReadResult(index)(2)
          }.otherwise {
            source3(index) := vrfReadResult(index)(2)
          }
        }

        // VRF cross lane read:
        // for each cross lane read access:
        // it always access datapath width of VRF in two lanes.
        // the source lane will consume: [[crossReadLSBOut]] and [[crossReadMSBOut]]
        // it should be sent out to corresponding lane to [[crossLaneRead.bits.sinkIndex]]

        // 1. group all VRF together, index them under the datapath width
        // 2. the cross read/write take dual size of datapath width.
        //
        // example of cross lane read
        //  0| 1| 2| 3| 4| 5| 6| 7
        //  8| 9|10|11|12|13|14|15
        // 16|17|18|19|20|21|22|23
        // 24|25|26|27|28|29|30|31

        /** for cross lane read LSB is read from VRF, ready to send out to ring. */
        val crossReadDataReadyLSB: Bool = record.state.sCrossReadLSB && RegNext(record.state.sCrossReadLSB)

        /** for cross lane read MSB is read from VRF, ready to send out to ring. */
        val crossReadDataReadyMSB: Bool = record.state.sCrossReadMSB && RegNext(record.state.sCrossReadMSB)

        /** read data from RF, try to send cross lane read LSB data to ring */
        val tryCrossReadSendLSB: Bool =
          crossReadDataReadyLSB && !record.state.sSendCrossReadResultLSB && slotOccupied.head

        /** read data from RF, try to send cross lane read MSB data to ring */
        val tryCrossReadSendMSB: Bool =
          crossReadDataReadyMSB && !record.state.sSendCrossReadResultMSB && slotOccupied.head
        // TODO: use [[record.state.sSendCrossReadResultLSB]]
        crossLaneRead.bits.sinkIndex := (!tryCrossReadSendLSB) ## laneIndex(parameter.laneNumberBits - 1, 1)
        crossLaneRead.bits.isTail := laneIndex(0)
        crossLaneRead.bits.sourceIndex := laneIndex
        crossLaneRead.bits.instructionIndex := record.laneRequest.instructionIndex
        crossLaneRead.bits.counter := record.groupCounter
        // TODO: use [[record.state.sSendCrossReadResultLSB]]
        crossLaneRead.bits.data := Mux(tryCrossReadSendLSB, crossReadLSBOut, crossReadMSBOut)
        crossLaneRead.valid := tryCrossReadSendLSB || tryCrossReadSendMSB

        // VRF cross write
        /** execute in ALU, try to send cross lane write LSB data to ring */
        val tryCrossWriteSendLSB = record.state.sExecute && !record.state.sCrossWriteLSB && slotOccupied.head

        /** execute in ALU, try to send cross lane write MSB data to ring */
        val tryCrossWriteSendMSB = record.state.sExecute && !record.state.sCrossWriteMSB && slotOccupied.head
        crossLaneWrite.bits.sinkIndex := laneIndex(parameter.laneNumberBits - 2, 0) ## (!tryCrossWriteSendLSB)
        crossLaneWrite.bits.sourceIndex := laneIndex
        crossLaneWrite.bits.isTail := laneIndex(parameter.laneNumberBits - 1)
        crossLaneWrite.bits.instructionIndex := record.laneRequest.instructionIndex
        crossLaneWrite.bits.counter := record.groupCounter
        crossLaneWrite.bits.data := Mux(tryCrossWriteSendLSB, crossWriteDataLSBHalf, crossWriteDataMSBHalf)
        crossLaneWrite.bits.mask := Mux(tryCrossWriteSendLSB, crossWriteMaskLSBHalf, crossWriteMaskMSBHalf)
        crossLaneWrite.valid := tryCrossWriteSendLSB || tryCrossWriteSendMSB

        // cross read receive.
        when(readBusDequeue.valid) {
          assert(readBusDequeue.bits.instructionIndex === record.laneRequest.instructionIndex)
          when(readBusDequeue.bits.isTail) {
            record.state.wCrossReadMSB := true.B
            crossReadMSBIn := readBusDequeue.bits.data
          }.otherwise {
            record.state.wCrossReadLSB := true.B
            crossReadLSBIn := readBusDequeue.bits.data
          }
        }

        // todo: handling self cross read for first and end lane.
        // maintain cross read send state machine.
        when(crossLaneReadReady && crossLaneRead.valid) {
          when(tryCrossReadSendLSB) {
            record.state.sSendCrossReadResultLSB := true.B
          }.otherwise {
            record.state.sSendCrossReadResultMSB := true.B
          }
        }
        // maintain cross write send state machine.
        when(crossLaneWriteReady && crossLaneWrite.valid) {
          record.state.sCrossWriteLSB := true.B
          when(record.state.sCrossWriteLSB) {
            record.state.sCrossWriteMSB := true.B
          }
        }

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
        when(dataDequeueFire && !masked) {
          when(record.executeIndex(1)) {
            // update tail
            crossWriteDataMSBHalf :=
              Mux(
                record.csr.vSew(0),
                dataDequeue(parameter.datapathWidth - 1, parameter.halfDatapathWidth),
                Mux(
                  record.executeIndex(0),
                  dataDequeue(parameter.halfDatapathWidth - 1, 0),
                  crossWriteDataMSBHalf(parameter.datapathWidth - 1, parameter.halfDatapathWidth)
                )
              ) ## Mux(
                !record.executeIndex(0) || record.csr.vSew(0),
                dataDequeue(parameter.halfDatapathWidth - 1, 0),
                crossWriteDataMSBHalf(parameter.halfDatapathWidth - 1, 0)
              )
            crossWriteMaskMSBHalf :=
              (record.executeIndex(0) || record.csr.vSew(0) || crossWriteMaskMSBHalf(1)) ##
                (!record.executeIndex(0) || record.csr.vSew(0) || crossWriteMaskMSBHalf(0))
          }.otherwise {
            // update head
            crossWriteDataLSBHalf :=
              Mux(
                record.csr.vSew(0),
                dataDequeue(parameter.datapathWidth - 1, parameter.halfDatapathWidth),
                Mux(
                  record.executeIndex(0),
                  dataDequeue(parameter.halfDatapathWidth - 1, 0),
                  crossWriteDataLSBHalf(parameter.datapathWidth - 1, parameter.halfDatapathWidth)
                )
              ) ## Mux(
                !record.executeIndex(0) || record.csr.vSew(0),
                dataDequeue(parameter.halfDatapathWidth - 1, 0),
                crossWriteDataLSBHalf(parameter.halfDatapathWidth - 1, 0)
              )
            crossWriteMaskLSBHalf :=
              (record.executeIndex(0) || record.csr.vSew(0) || crossWriteMaskLSBHalf(1)) ##
                (!record.executeIndex(0) || record.csr.vSew(0) || crossWriteMaskLSBHalf(0))
          }
        }

        // clear mask when group change.
        when(record.state.asUInt.andR) {
          crossWriteMaskLSBHalf := 0.U
          crossWriteMaskMSBHalf := 0.U
        }

        /** we have used mask inside mask group, and need to request from [[V]] */
        val maskNeedUpdate = !nextOrR && (!alwaysNextGroup || lastGroupForMask)

        /** the MSB part of [[nextElementIndexInLane]]
          * [[nextIndex]] is the log2 of [[parameter.datapathWidth]],
          * it contains the higher bits of element index of lanes
          * thus [[nextGroupCountMSB]] has the MSB suffix.
          */
        val nextGroupCountMSB: UInt = Mux1H(
          vSew1H(1, 0),
          Seq(
            record.groupCounter(parameter.groupNumberBits - 1, parameter.groupNumberBits - 3),
            false.B ## record.groupCounter(parameter.groupNumberBits - 1, parameter.groupNumberBits - 2)
          )
        ) + maskNeedUpdate

        /** the next element index in lane to execute */
        val nextElementIndexInLane = nextGroupCountMSB ## nextIndex

        /** the data offset of next element in lane to execute
          * TODO: [[record.csr.vSew]] only has value 0,1,2 for 32bits.
          */
        val nextElementOffset = (nextElementIndexInLane << record.csr.vSew).asUInt

        /** TODO: start from here.
          * mask 后 ffo 计算出来的下一次计算的 element 属于哪一个 group
          */
        val nextGroupMasked: UInt = nextElementOffset(parameter.groupNumberBits + 1, 2)
        val nextGroupCount = Mux(
          alwaysNextGroup,
          record.groupCounter + 1.U,
          nextGroupMasked
        )

        /** 虽然没有计算完一组,但是这一组剩余的都被mask去掉了 */
        val maskFilterEnd = skipEnable && (nextGroupMasked =/= record.groupCounter)

        /** 这是会执行的最后一组 */
        val lastExecuteGroup: Bool = lastGroupForLane === record.groupCounter

        val lastExecuteIndex = Mux(
          lastExecuteGroup && isEndLane && !record.laneRequest.decodeResult(Decoder.maskLogic),
          lastElementExecuteIndex,
          slotGroupFinishedIndex
        )

        /**
          * 需要一个除vl导致的end来决定下一个的 element index 是什么
          * 在vl没对齐的情况下，最后一组的结束的时候的 [[record.executeIndex]] 需要修正
          */
        val groupEnd = (writeIndex === lastExecuteIndex) || maskFilterEnd
        val enqGroupEnd = (record.executeIndex === lastExecuteIndex) || maskFilterEnd

        /** 只会发生在跨lane读 */
        val waitCrossRead = lastGroupForLane < record.groupCounter && noFeedBack
        val lastVRFWrite: Bool = lastGroupForLane < nextGroupCount

        val nextExecuteIndex = Mux(
          alwaysNextGroup && groupEnd,
          0.U,
          nextElementOffset(1, 0)
        )

        /** 计算当前这一组的 vrf mask
          * 已知：mask mask1H executeIndex
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
        val executeByteEnable = Mux1H(
          vSew1H(2, 0),
          Seq(
            UIntToOH(record.executeIndex),
            record.executeIndex(1) ## record.executeIndex(1) ## !record.executeIndex(1) ## !record.executeIndex(1),
            15.U(4.W)
          )
        )
        val executeBitEnable: UInt = FillInterleaved(8, executeByteEnable)

        def CollapseOperand(data: UInt, enable: Bool = true.B, sign: Bool = false.B): UInt = {
          val dataMasked: UInt = data & executeBitEnable
          val select:     UInt = Mux(enable, vSew1H(2, 0), 4.U(3.W))
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
          val doubleBitEnable = FillInterleaved(16, executeByteEnable)
          val doubleDataMasked: UInt = (crossReadMSBIn ## crossReadLSBIn) & doubleBitEnable
          val select:           UInt = vSew1H(1, 0)
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
        // 处理操作数
        val src1IsReduceResult: Bool = reduceType && !record.laneRequest.decodeResult(Decoder.popCount)

        /**
          * src1： src1有 IXV 三种类型,只有V类型的需要移位
          */
        val finalSource1 = CollapseOperand(
          Mux(src1IsReduceResult, reduceResult(index), source1(index)),
          decodeResult(Decoder.vtype) && !src1IsReduceResult,
          !decodeResult(Decoder.unsigned0)
        )

        /** source2 一定是V类型的 */
        val doubleCollapse = CollapseDoubleOperand(!decodeResult(Decoder.unsigned1))
        val finalSource2 = Mux(
          crossReadVS2,
          doubleCollapse,
          CollapseOperand(source2(index), true.B, !decodeResult(Decoder.unsigned1))
        )
        val nrEnd: Bool = record.groupCounter === (record.laneRequest.vs1) ## 3.U(2.W)
        instructionExecuteFinished(index) := Mux(
          nr,
          nrEnd,
          waitCrossRead || (lastExecuteGroup && groupEnd) || maskLogicWillCompleted
        )
        instructionCrossReadFinished := waitCrossRead || readFinish

        /** source3 有两种：adc & ma, c等处理mask的时候再处理 */
        val finalSource3 = Mux(decodeResult(Decoder.vwmacc), doubleCollapse, CollapseOperand(source3(index)))
        // 假如这个单元执行的是logic的类型的,请求应该是什么样子的
        val logicRequest = Wire(new MaskedLogicRequest(parameter.datapathWidth))
        logicRequest.src.head := finalSource2
        logicRequest.src(1) := finalSource1
        logicRequest.src(2) := maskCorrect
        logicRequest.src(3) := finalSource3
        logicRequest.opcode := decodeResult(Decoder.uop)
        // 在手动做Mux1H
        logicRequests(index) := maskAnd(
          slotOccupied(index) && decodeResult(Decoder.logic) && !decodeResult(Decoder.other),
          logicRequest
        )

        // adder 的
        val adderRequest = Wire(new LaneAdderReq(parameter.datapathWidth))
        adderRequest.src := VecInit(Seq(finalSource1, finalSource2))
        adderRequest.mask := maskAsInput
        adderRequest.opcode := decodeResult(Decoder.uop)
        adderRequest.sign := !decodeResult(Decoder.unsigned1)
        adderRequest.reverse := decodeResult(Decoder.reverse)
        adderRequest.average := decodeResult(Decoder.average)
        adderRequest.saturat := decodeResult(Decoder.saturate)
        adderRequest.vxrm := record.csr.vxrm
        adderRequest.vSew := record.csr.vSew
        adderRequests(index) := maskAnd(
          slotOccupied(index) && decodeResult(Decoder.adder) && !decodeResult(Decoder.other),
          adderRequest
        )

        // shift 的
        val shiftRequest = Wire(new LaneShifterReq(parameter.shifterParameter))
        shiftRequest.src := finalSource2
        // 2 * sew 有额外1bit的
        shiftRequest.shifterSize := Mux1H(
          Mux(needCrossRead, vSew1H(1, 0), vSew1H(2, 1)),
          Seq(false.B ## finalSource1(3), finalSource1(4, 3))
        ) ## finalSource1(2, 0)
        shiftRequest.opcode := decodeResult(Decoder.uop)
        shiftRequests(index) := maskAnd(
          slotOccupied(index) && decodeResult(Decoder.shift) && !decodeResult(Decoder.other),
          shiftRequest
        )
        shiftRequest.vxrm := record.csr.vxrm

        // mul
        val mulRequest: LaneMulReq = Wire(new LaneMulReq(parameter.mulParam))
        mulRequest.src := VecInit(Seq(finalSource1, finalSource2, finalSource3))
        mulRequest.opcode := decodeResult(Decoder.uop)
        mulRequest.saturate := decodeResult(Decoder.saturate)
        mulRequest.vSew := record.csr.vSew
        mulRequest.vxrm := record.csr.vxrm
        multiplerRequests(index) := maskAnd(
          slotOccupied(index) && decodeResult(Decoder.multiplier) && !decodeResult(Decoder.other),
          mulRequest
        )

        // div
        val divRequest = Wire(new LaneDivRequest(parameter.datapathWidth))
        divRequest.src := VecInit(Seq(finalSource1, finalSource2))
        divRequest.rem := decodeResult(Decoder.uop)(0)
        divRequest.sign := !decodeResult(Decoder.unsigned0)
        divRequest.index := record.executeIndex
        dividerRequests(index) := maskAnd(
          slotOccupied(index) && decodeResult(Decoder.divider) && !decodeResult(Decoder.other),
          divRequest
        )

        // other
        val otherRequest: OtherUnitReq = Wire(Output(new OtherUnitReq(parameter)))
        otherRequest.src := VecInit(Seq(finalSource1, finalSource2, finalSource3))
        otherRequest.popInit := reduceResult(index)
        otherRequest.opcode := decodeResult(Decoder.uop)(2, 0)
        otherRequest.specialOpcode := decodeResult(Decoder.specialUop)
        otherRequest.imm := record.laneRequest.vs1
        otherRequest.extendType.valid := decodeResult(Decoder.uop)(3)
        otherRequest.extendType.bits.elements.foreach { case (s, d) => d := decodeResult.elements(s) }
        otherRequest.laneIndex := laneIndex
        otherRequest.groupIndex := record.groupCounter
        otherRequest.executeIndex := record.executeIndex
        otherRequest.sign := !decodeResult(Decoder.unsigned0)
        // todo: vmv.v.* decode to mv instead of merge or delete mv
        otherRequest.mask := maskAsInput || !record.laneRequest.mask
        otherRequest.complete := record.schedulerComplete || record.selfCompleted
        otherRequest.maskType := record.laneRequest.mask
        otherRequest.vSew := record.csr.vSew
        otherRequest.vxrm := record.csr.vxrm
        otherRequests(index) := maskAnd(slotOccupied(index) && decodeResult(Decoder.other), otherRequest)

        // 往scheduler的执行任务compress viota
        val maskRequest: LaneResponse = Wire(Output(new LaneResponse(parameter)))

        /** mask类型的指令并不是每一轮的状态机都会需要与scheduler交流
          * 只有mask destination 类型的才会有scheduler与状态机不对齐的情况
          *   我们用[[maskTypeDestinationWriteValid]]区分这种
          * 有每轮需要交换和只需要交换一次的区别(compress & red)
          */
        val canSendMaskRequest = needResponse && readFinish && record.state.sExecute
        val maskValid = canSendMaskRequest && !record.state.sScheduler
        val noNeedWaitScheduler: Bool = !(canSendMaskRequest && !record.initState.sScheduler) || schedulerFinish
        // 往外边发的是原始的数据
        maskRequest.data := Mux(
          // todo: decode
          record.laneRequest.decodeResult(Decoder.maskDestination) && !record.laneRequest.loadStore,
          maskFormatResult,
          Mux(
            updateReduce,
            reduceResult(index),
            Mux(
              record.laneRequest.decodeResult(Decoder.gather) && !record.laneRequest.loadStore,
              source1(index),
              Mux(
                record.laneRequest.decodeResult(Decoder.ffo) && !record.laneRequest.loadStore,
                ffoIndexReg,
                source2(index)
              )
            )
          )
        )
        maskRequest.toLSU := record.laneRequest.loadStore
        maskRequest.instructionIndex := record.laneRequest.instructionIndex
        maskRequest.ffoSuccess := record.selfCompleted
        maskRequests(index) := maskAnd(slotOccupied(index) && maskValid, maskRequest)
        maskRequestValids(index) := maskValid

        when(
          laneResponseFeedback.valid && laneResponseFeedback.bits.instructionIndex === record.laneRequest.instructionIndex
        ) {
          record.state.wScheduler := true.B
        }
        when(maskValid) {
          record.state.sScheduler := true.B
        }
        instructionTypeVec(index) := record.laneRequest.instType
        // 只有longLatency的才在masked的情况下不进执行单元
        executeEnqueueValid(index) := maskAnd(
          readFinish && !record.state.sExecute && !maskedLongLatency,
          instructionTypeVec(index)
        )
        // todo: 不用在lane执行的maskUnit的指令不要把sExecute初始值设0
        when((instructionTypeVec(index) & executeEnqueueFire).orR || maskedLongLatency) {
          when(enqGroupEnd) {
            record.state.sExecute := true.B
          }.otherwise {
            record.executeIndex := nextExecuteIndex
          }
        }

        // todo: 暂时先这样,处理mask的时候需要修
        val executeResult = (dataDequeue << dataOffset).asUInt(parameter.datapathWidth - 1, 0)
        // TODO: remove it.
        val writeByteEnable = Mux1H(
          vSew1H(2, 0),
          Seq(
            UIntToOH(writeIndex),
            writeIndex(1) ## writeIndex(1) ## !writeIndex(1) ## !writeIndex(1),
            15.U(4.W)
          )
        )
        val resultUpdate: UInt = (executeResult & writeMaskInBit) | (result(index) & (~writeMaskInBit).asUInt)
        // 第一组div整个被跳过了的情况
        when(dataDequeueFire || (longLatency && groupEnd && record.state.sExecute && !divBusy)) {
          when(groupEnd && !(divBusy && longLatency)) {
            record.state.wExecuteRes := true.B
          }
          result(index) := resultUpdate
          record.selfCompleted := otherResponse.ffoSuccess
          when(otherResponse.ffoSuccess && !record.selfCompleted) {
            ffoIndexReg := record.groupCounter ## Mux1H(
              vSew1H,
              Seq(
                record.executeIndex ## otherResponse.data(2, 0),
                record.executeIndex(1) ## otherResponse.data(3, 0),
                otherResponse.data(4, 0)
              )
            )
          }
          // div的mask指挥影响req,不会影响回应
          when(!masked || (longLatency && divWrite)) {
            record.vrfWriteMask := record.vrfWriteMask | writeByteEnable
            when(updateReduce) {
              reduceResult(index) := dataDequeue
            }
          }
        }

        // 更新mask类型的结果
        val elementMaskFormatResult: UInt = Mux(adderMaskResp && !masked, current1H, 0.U)
        val maskFormatResultUpdate:  UInt = maskFormatResult | elementMaskFormatResult
        when(dataDequeueFire || maskValid) {
          maskFormatResult := Mux(maskValid, 0.U, maskFormatResultUpdate)
        }

        // 写rf
        val completeWrite = Mux(record.laneRequest.mask, (~source1(index)).asUInt & source3(index), 0.U)
        vrfWriteArbiter(index).valid := record.state.wExecuteRes && !record.state.sWrite && readFinish &&
        slotActive(index) && noNeedWaitScheduler
        vrfWriteArbiter(index).bits.vd := record.laneRequest.vd + record.groupCounter(
          parameter.groupNumberBits - 1,
          parameter.vrfOffsetBits
        )
        vrfWriteArbiter(index).bits.offset := record.groupCounter
        vrfWriteArbiter(index).bits.data := Mux(
          record.schedulerComplete,
          completeWrite,
          Mux(nr, source2(index), result(index))
        )
        // todo: 是否条件有多余
        vrfWriteArbiter(index).bits.last := instructionExecuteFinished(index) || lastVRFWrite
        vrfWriteArbiter(index).bits.instructionIndex := record.laneRequest.instructionIndex
        vrfWriteArbiter(index).bits.mask := record.vrfWriteMask | Fill(4, nr)
        when(vrfWriteFire(index)) {
          record.state.sWrite := true.B
        }
        instructionFinishedVec(index) := 0.U(parameter.chainingSize.W)
        val maskUnhindered = maskRequestFireOH(index) || !maskNeedUpdate
        val stateCheck = Mux(
          waitCrossRead,
          readFinish,
          (readFinish && executeFinish && sendCrossReadResultFinish && crossWriteFinish) && (!needResponse || schedulerFinish) && maskUnhindered
        )
        val crossReadReady: Bool = !needCrossRead || instructionCrossReadFinished
        when(stateCheck || record.instructionFinished) {
          when((instructionExecuteFinished(index) && crossReadReady && noFeedBack) || record.instructionFinished) {
            slotOccupied(index) := false.B
            maskFormatResult := 0.U
            when(slotOccupied(index)) {
              instructionFinishedVec(index) := UIntToOH(
                record.laneRequest.instructionIndex(parameter.instructionIndexBits - 2, 0)
              )
            }
          }.otherwise {
            record.state := record.initState
            record.groupCounter := nextGroupCount
            record.executeIndex := nextExecuteIndex
            record.vrfWriteMask := 0.U
            when(maskRequestFireOH(index)) {
              record.mask.valid := true.B
              record.mask.bits := maskInput
              record.maskGroupedOrR := maskGroupedOrR
            }
          }
        }
        when(
          laneResponseFeedback.bits.complete && laneResponseFeedback.valid &&
            laneResponseFeedback.bits.instructionIndex === record.laneRequest.instructionIndex
        ) {
          // 例如:别的lane找到了第一个1
          record.schedulerComplete := true.B
          when(record.initState.wExecuteRes) {
            slotOccupied(index) := false.B
          }
        }
        // mask 更换
        slotMaskRequestVec(index).valid := maskNeedUpdate
        slotMaskRequestVec(index).bits := nextGroupCountMSB
      }
  }

  // 处理读环的
  {
    val readBusDataReg: ValidIO[ReadBusData] = RegInit(0.U.asTypeOf(Valid(new ReadBusData(parameter))))
    val readBusDequeueMatch = readBusPort.enq.bits.sinkIndex === laneIndex &&
      readBusPort.enq.bits.counter === slotControl.head.groupCounter
    readBusDequeue.valid := readBusDequeueMatch && readBusPort.enq.valid
    readBusDequeue.bits := readBusPort.enq.bits
    // 暂时优先级策略是环上的优先
    readBusPort.enq.ready := true.B
    readBusDataReg.valid := false.B

    when(readBusPort.enq.valid) {
      when(!readBusDequeueMatch) {
        readBusDataReg.valid := true.B
        readBusDataReg.bits := readBusPort.enq.bits
      }
    }

    // 试图进环
    readBusPort.deq.valid := readBusDataReg.valid || crossLaneRead.valid
    readBusPort.deq.bits := Mux(readBusDataReg.valid, readBusDataReg.bits, crossLaneRead.bits)
    crossLaneReadReady := !readBusDataReg.valid
  }

  // 处理写环
  {
    val writeBusDataReg: ValidIO[WriteBusData] = RegInit(0.U.asTypeOf(Valid(new WriteBusData(parameter))))
    // 策略依然是环上的优先,如果queue满了继续转
    val writeBusIndexMatch = writeBusPort.enq.bits.sinkIndex === laneIndex && crossLaneWriteQueue.io.enq.ready
    writeBusPort.enq.ready := true.B
    writeBusDataReg.valid := false.B
    crossLaneWriteQueue.io.enq.bits.vd := slotControl.head.laneRequest.vd + writeBusPort.enq.bits.counter(3, 1)
    crossLaneWriteQueue.io.enq.bits.offset := writeBusPort.enq.bits.counter ## writeBusPort.enq.bits.isTail
    crossLaneWriteQueue.io.enq.bits.data := writeBusPort.enq.bits.data
    crossLaneWriteQueue.io.enq.bits.last := instructionExecuteFinished.head && writeBusPort.enq.bits.isTail
    crossLaneWriteQueue.io.enq.bits.instructionIndex := slotControl.head.laneRequest.instructionIndex
    crossLaneWriteQueue.io.enq.bits.mask := FillInterleaved(2, writeBusPort.enq.bits.mask)
    //writeBusPort.enq.bits
    crossLaneWriteQueue.io.enq.valid := false.B

    when(writeBusPort.enq.valid) {
      when(writeBusIndexMatch) {
        crossLaneWriteQueue.io.enq.valid := true.B
      }.otherwise {
        writeBusDataReg.valid := true.B
        writeBusDataReg.bits := writeBusPort.enq.bits
      }
    }

    // 进写环
    writeBusPort.deq.valid := writeBusDataReg.valid || crossLaneWrite.valid
    writeBusPort.deq.bits := Mux(writeBusDataReg.valid, writeBusDataReg.bits, crossLaneWrite.bits)
    crossLaneWriteReady := !writeBusDataReg.valid
  }

  // VFU
  // TODO: reuse logic, adder, multiplier datapath
  {
    val logicUnit: MaskedLogic = Module(new MaskedLogic(parameter.datapathWidth))
    val adder:     LaneAdder = Module(new LaneAdder(parameter.datapathWidth))
    val shifter:   LaneShifter = Module(new LaneShifter(parameter.shifterParameter))
    val mul:       LaneMul = Module(new LaneMul(parameter.mulParam))
    val div:       LaneDiv = Module(new LaneDiv(parameter.datapathWidth))
    val otherUnit: OtherUnit = Module(new OtherUnit(parameter))

    // 连接执行单元的请求
    logicUnit.req := VecInit(logicRequests.map(_.asUInt))
      .reduce(_ | _)
      .asTypeOf(new MaskedLogicRequest(parameter.datapathWidth))
    adder.req := VecInit(adderRequests.map(_.asUInt)).reduce(_ | _).asTypeOf(new LaneAdderReq(parameter.datapathWidth))
    shifter.req := VecInit(shiftRequests.map(_.asUInt))
      .reduce(_ | _)
      .asTypeOf(new LaneShifterReq(parameter.shifterParameter))
    mul.req := VecInit(multiplerRequests.map(_.asUInt)).reduce(_ | _).asTypeOf(new LaneMulReq(parameter.mulParam))
    div.req.bits := VecInit(dividerRequests.map(_.asUInt))
      .reduce(_ | _)
      .asTypeOf(new LaneDivRequest(parameter.datapathWidth))
    otherUnit.req := VecInit(otherRequests.map(_.asUInt)).reduce(_ | _).asTypeOf(Output(new OtherUnitReq(parameter)))
    laneResponse.bits := VecInit(maskRequests.map(_.asUInt))
      .reduce(_ | _)
      .asTypeOf(Output(new LaneResponse(parameter)))
    laneResponse.valid := maskRequestValids.asUInt.orR
    // 执行单元的其他连接
    otherResponse := otherUnit.resp
    lastDivWriteIndexWire := div.index
    divWrite := div.resp.valid
    divBusy := div.busy

    // 连接执行结果
    executeDequeueData := VecInit(
      Seq(logicUnit.resp, adder.resp.data, shifter.resp, mul.resp, div.resp.bits, otherUnit.resp.data)
    )
    executeDequeueFire := executeEnqueueFire(5) ## div.resp.valid ## executeEnqueueFire(3, 0)
    // 执行单元入口握手
    val tryToUseExecuteUnit = VecInit(executeEnqueueValid.map(_.asBools).transpose.map(VecInit(_).asUInt.orR)).asUInt
    executeEnqueueFire := tryToUseExecuteUnit & (true.B ## div.req.ready ## 15.U(4.W))
    div.req.valid := tryToUseExecuteUnit(4)
    adderMaskResp := adder.resp.singleResult
    // todo: vssra
    vxsat := adder.resp.vxsat
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
  // TODO: fix me with decode
  entranceControl.state := laneRequest.bits.initState
  entranceControl.initState := laneRequest.bits.initState
  // TODO: in scalar core, raise illegal instruction exception when vstart is nonzero.
  //   see [[https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#37-vector-start-index-csr-vstart]]
  //   "Such implementations are permitted to raise an illegal instruction exception
  //   when attempting to execute a vector arithmetic instruction when vstart is nonzero."
  entranceControl.executeIndex := 0.U
  entranceControl.schedulerComplete := false.B
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
  // todo: vStart(2,0) > lane index
  // which group to start.
  // TODO: set to 0.
  entranceControl.groupCounter := (csrInterface.vStart >> 3).asUInt

  /** type of execution unit.
    * TODO: use decoder
    */
  val entranceInstType: UInt = laneRequest.bits.instType

  /** slot inside [[Lane]] is ready to shift.
    * don't shift when feedback.
    */
  val shiftReady = slotCanShift.asUInt.andR && !laneResponseFeedback.valid

  // handshake
  laneRequest.ready := !slotOccupied.head && vrf.instructionWriteReport.ready && shiftReady

  // Slot shift logic
  when(
    // the first slot is not occupied
    !slotOccupied.head &&
      (
        // no instruction is incoming or the next instruction is rejected, but 0th slot is free, and others are not.
        slotOccupied.asUInt.orR ||
          // new instruction enqueue
          laneRequest.valid
      ) &&
      // slots is ready to shift
      shiftReady
  ) {
    slotOccupied := VecInit(slotOccupied.tail :+ laneRequest.valid)
    source1 := VecInit(source1.tail :+ laneRequest.bits.readFromScalar)
    slotControl := VecInit(slotControl.tail :+ entranceControl)
    result := VecInit(result.tail :+ 0.U(parameter.datapathWidth.W))
    reduceResult := VecInit(reduceResult.tail :+ 0.U(parameter.datapathWidth.W))
    source2 := VecInit(source2.tail :+ 0.U(parameter.datapathWidth.W))
    source3 := VecInit(source3.tail :+ 0.U(parameter.datapathWidth.W))
    crossWriteMaskLSBHalf := 0.U
    crossWriteMaskMSBHalf := 0.U
  }

  vrf.flush := maskUnitFlushVrf
  // normal instruction, LSU instruction will be report to VRF.
  vrf.instructionWriteReport.valid := (laneRequest.fire || (!laneRequest.bits.store && laneRequest.bits.loadStore)) && !entranceControl.instructionFinished
  vrf.instructionWriteReport.bits.instIndex := laneRequest.bits.instructionIndex
  vrf.instructionWriteReport.bits.offset := 0.U //todo
  vrf.instructionWriteReport.bits.vdOffset := 0.U
  vrf.instructionWriteReport.bits.vd.bits := laneRequest.bits.vd
  vrf.instructionWriteReport.bits.vd.valid := !laneRequest.bits.initState.sWrite || (laneRequest.bits.loadStore && !laneRequest.bits.store)
  vrf.instructionWriteReport.bits.vs2 := laneRequest.bits.vs2
  vrf.instructionWriteReport.bits.vs1.bits := laneRequest.bits.vs1
  vrf.instructionWriteReport.bits.vs1.valid := laneRequest.bits.decodeResult(Decoder.vtype)
  // TODO: move ma to [[V]]
  vrf.instructionWriteReport.bits.ma := laneRequest.bits.ma
  // 暂时认为ld都是无序写寄存器的
  vrf.instructionWriteReport.bits.unOrderWrite := (laneRequest.bits.loadStore && !laneRequest.bits.store) || laneRequest.bits
    .decodeResult(Decoder.other)
  vrf.instructionWriteReport.bits.seg.valid := laneRequest.bits.loadStore && laneRequest.bits.segment.orR
  vrf.instructionWriteReport.bits.seg.bits := laneRequest.bits.segment
  vrf.instructionWriteReport.bits.eew := laneRequest.bits.loadStoreEEW
  vrf.instructionWriteReport.bits.ls := laneRequest.bits.loadStore
  vrf.instructionWriteReport.bits.st := laneRequest.bits.store
  vrf.instructionWriteReport.bits.widen := laneRequest.bits.decodeResult(Decoder.crossWrite)
  vrf.instructionWriteReport.bits.stFinish := false.B
  vrf.instructionWriteReport.bits.mul := Mux(csrInterface.vlmul(2), 0.U, csrInterface.vlmul(1, 0))
  vrf.lsuLastReport := lsuLastReport
  vrf.lsuWriteBufferClear := lsuVRFWriteBufferClear
  instructionFinished := instructionFinishedVec.reduce(_ | _)
}
