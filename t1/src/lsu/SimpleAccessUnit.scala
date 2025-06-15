// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lsu

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.probe._
import chisel3.util._
import chisel3.ltl._
import chisel3.ltl.Sequence._
import org.chipsalliance.t1.rtl._
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}

/** @param datapathWidth
  *   ELEN
  * @param chainingSize
  *   how many instructions can be chained
  * @param vLen
  *   VLEN
  * @param laneNumber
  *   how many lanes in the vector processor
  * @param paWidth
  *   physical address width
  * @note
  *   MSHR group: The memory access of a single instruction will be grouped into MSHR group. Because indexed load/store
  *   need to access VRF to get the offset of memory address to access, we use the maximum count of transactions of
  *   indexed load/store in each memory access operation for each lanes to calculate the size of MSHR. The MSHR group
  *   maintains a group of transactions with a set of base address.
  *
  * Refactor Plan:
  *   - merge memory request for cacheline not per element -- natively interleaving memory request since bank bits is
  *     not in cacheline tags -- save bandwidth in A Channel -- save bandwidth in L2 Cache(improve efficiency in cache
  *     directory) -- save request queue size in L2(less back pressure on bus) -- use PutPartial mask for masked store
  *     -- burst will block other memory requests until it is finished(this is limited by TileLink)
  *   - memory request hazard detection for multiple instructions -- unit stride without segment: instruction order,
  *     base address per instruction, current mask group index, mask inside mask group -- unit stride with segment:
  *     instruction order, base address per instruction, nf per instruction, current mask group index, mask inside mask
  *     group -- stride without segment: instruction order, stride per instruction, base address per instruction,
  *     current mask group index, mask inside mask group -- stride with segment: instruction order, stride per
  *     instruction, base address per instruction, nf per instruction, current mask group index, mask inside mask group
  *     -- indexed without segment -- indexed with segment based on the mask group granularity to detect hazard for unit
  *     stride and stride instruction
  */
case class MSHRParam(
  chainingSize:     Int,
  datapathWidth:    Int,
  vLen:             Int,
  eLen:             Int,
  laneNumber:       Int,
  paWidth:          Int,
  lsuTransposeSize: Int,
  lsuReadShifter:   Int,
  vrfReadLatency: Int) {

  /** see [[LaneParameter.lmulMax]] */
  val lmulMax: Int = 8

  /** see [[LaneParameter.sewMin]] */
  val sewMin: Int = 8

  /** see [[LaneParameter.vlMax]] */
  val vlMax: Int = vLen * lmulMax / sewMin

  /** see [[LaneParameter.vlMaxBits]] */
  val vlMaxBits: Int = log2Ceil(vlMax) + 1

  /** the maximum address offsets number can be accessed from lanes for one time. */
  val maxOffsetPerLaneAccess: Int = datapathWidth * laneNumber / sewMin

  /** the maximum size of memory requests a MSHR can maintain.
    *
    * @note
    *   this is mask size
    */
  val maxOffsetGroupSize: Int = vLen / maxOffsetPerLaneAccess

  /** The hardware length of [[maxOffsetPerLaneAccess]] */
  val maxOffsetPerLaneAccessBits: Int = log2Ceil(maxOffsetPerLaneAccess)

  /** The hardware length of [[maxOffsetGroupSize]] `+1` is because we always use the next group to decide whether the
    * current group is the last group.
    */
  val maxOffsetGroupSizeBits: Int = log2Ceil(maxOffsetGroupSize + 1)

  /** See [[VParameter.sourceWidth]], due to we are in the MSHR, the `log2Ceil(lsuMSHRSize)` is dropped.
    */
  val sourceWidth: Int = {
    maxOffsetPerLaneAccessBits + // offset group
      3                          // segment index, this is decided by spec.
  }

  /** See [[VParameter.maskGroupWidth]] */
  val maskGroupWidth: Int = maxOffsetPerLaneAccess

  /** See [[VParameter.maskGroupSize]] */
  val maskGroupSize: Int = vLen / maskGroupWidth

  /** The hardware length of [[maskGroupSize]] */
  val maskGroupSizeBits: Int = log2Ceil(maskGroupSize)

  /** see [[VRFParam.regNumBits]] */
  val regNumBits: Int = log2Ceil(32)

  /** see [[LaneParameter.instructionIndexBits]] */
  val instructionIndexBits: Int = log2Ceil(chainingSize) + 1

  /** see [[LaneParameter.singleGroupSize]] */
  val singleGroupSize: Int = vLen / datapathWidth / laneNumber

  /** see [[LaneParameter.vrfOffsetBits]] */
  val vrfOffsetBits: Int = log2Ceil(singleGroupSize)

  /** offset bit for a cache line */
  val cacheLineBits: Int = log2Ceil(lsuTransposeSize)

  /** The maximum number of cache lines that will be accessed, a counter is needed. +1 Corresponding unaligned case
    */
  val cacheLineIndexBits: Int = log2Ceil(vLen / lsuTransposeSize + 1)

  // outstanding of MaskExchangeUnit.maskReq
  // todo: param from T1Param
  val maskRequestQueueSize: Int = 8

  // outstanding of StoreUnit.vrfReadDataPorts
  // todo: param from T1Param
  val storeUnitReadOutStanding: Int = 8

  // One round trip is required
  val lsuReadShifterLatency: Int = 2 * lsuReadShifter

  val dataPathByteBits: Int = log2Ceil(datapathWidth / 8)
}

/** Miss Status Handler Register this is used to record the outstanding memory access request for each instruction. it
  * contains 3 stages for tl.a:
  *   - s0: access lane for the offset of the memory address
  *   - s1: send VRF read request; calculate memory address based on s0 result
  *   - s2: send tilelink memory request.
  *
  * tl.d is handled independently.
  */
@instantiable
class SimpleAccessUnit(param: MSHRParam) extends Module with LSUPublic {

  /** [[LSURequest]] from LSU see [[LSU.request]]
    */
  @public
  val lsuRequest: ValidIO[LSURequest] = IO(Flipped(Valid(new LSURequest(param.datapathWidth, param.chainingSize))))

  /** read channel to [[V]], which will redirect it to [[Lane.vrf]]. see [[LSU.vrfReadDataPorts]]
    */
  @public
  val vrfReadDataPorts: DecoupledIO[VRFReadRequest] = IO(
    Decoupled(new VRFReadRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits))
  )

  /** hard wire form Top. see [[LSU.vrfReadResults]]
    */
  @public
  val vrfReadResults: ValidIO[UInt] = IO(Input(Valid(UInt(param.datapathWidth.W))))

  /** offset of indexed load/store instructions. */
  @public
  val offsetReadResult: Vec[DecoupledIO[UInt]] = IO(
    Vec(param.laneNumber, Flipped(Decoupled(UInt(param.datapathWidth.W))))
  )

  /** mask from [[V]] see [[LSU.maskInput]]
    */
  @public
  val maskInput: UInt = IO(Input(UInt(param.maskGroupWidth.W)))

  /** the address of the mask group in the [[V]]. see [[LSU.maskSelect]]
    */
  @public
  val maskSelect: ValidIO[UInt] = IO(Valid(UInt(param.maskGroupSizeBits.W)))

  @public
  val memReadRequest:  DecoupledIO[SimpleMemRequest]      = IO(Decoupled(new SimpleMemRequest(param)))
  @public
  val memReadResponse: DecoupledIO[SimpleMemReadResponse] = IO(Flipped(Decoupled(new SimpleMemReadResponse(param))))
  @public
  val memWriteRequest: DecoupledIO[SimpleMemWrite]        = IO(Decoupled(new SimpleMemWrite(param)))

  /** write channel to [[V]], which will redirect it to [[Lane.vrf]]. see [[LSU.vrfWritePort]]
    */
  @public
  val vrfWritePort: DecoupledIO[VRFWriteRequest] = IO(
    Decoupled(
      new VRFWriteRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits, param.datapathWidth)
    )
  )

  /** the CSR interface from [[V]], latch them here. TODO: merge to [[LSURequest]]
    */
  @public
  val csrInterface: CSRInterface = IO(Input(new CSRInterface(param.vlMaxBits)))

  /** notify [[LSU]] the status of [[MSHR]] */
  @public
  val status: SimpleAccessStatus = IO(Output(new SimpleAccessStatus(param.laneNumber, param.instructionIndexBits)))

  // other unit probe
  @public
  val probe = IO(Output(Probe(new MemoryWriteProbe(param), layers.Verification)))

  @public
  val offsetRelease: Vec[Bool] = IO(Output(Vec(param.laneNumber, Bool())))

  val requestOffset:  Bool               = Wire(Bool())
  val stateIdle:      Bool               = Wire(Bool())
  val waitQueueDeq:   Vec[Bool]          = Wire(Vec(param.laneNumber, Bool()))
  val offsetQueueVec: Seq[QueueIO[UInt]] = offsetReadResult.zipWithIndex.map { case (req, index) =>
    val queue:   QueueIO[UInt] = Queue.io(chiselTypeOf(req.bits), param.maskRequestQueueSize)
    val deqLock: Bool          = RegInit(false.B)
    waitQueueDeq(index)  := deqLock
    when(lsuRequest.valid || requestOffset || queue.deq.fire) {
      deqLock := queue.deq.fire
    }
    offsetRelease(index) := queue.deq.fire
    queue.enq.valid      := req.valid && !stateIdle
    queue.enq.bits       := req.bits
    queue.deq.ready      := !deqLock || stateIdle
    req.ready            := queue.enq.ready && !stateIdle
    queue
  }

  val s0Fire:         Bool = Wire(Bool())
  val s1Fire:         Bool = Wire(Bool())
  val memRequestFire: Bool = memReadRequest.fire || memWriteRequest.fire
  val s2Fire:         Bool = memRequestFire

  /** request from LSU. */
  val lsuRequestReg: LSURequest = RegEnable(lsuRequest.bits, 0.U.asTypeOf(lsuRequest.bits), lsuRequest.valid)

  /** latch CSR. TODO: merge to [[lsuRequestReg]]
    */
  val csrInterfaceReg: CSRInterface = RegEnable(csrInterface, 0.U.asTypeOf(csrInterface), lsuRequest.valid)

  /** load whole VRF register. See
    * [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#79-vector-loadstore-whole-register-instructions]]
    * TODO: RegEnable(requestIsWholeRegisterLoadStore)
    */
  val isWholeRegisterLoadStore: Bool = lsuRequestReg.instructionInformation.mop === 0.U &&
    lsuRequestReg.instructionInformation.lumop === 8.U

  /** indicate the current instruction is a segment load store. */
  val isSegmentLoadStore: Bool = lsuRequestReg.instructionInformation.nf.orR && !isWholeRegisterLoadStore

  /** indicate the current instruction is a load/store to mask. TODO: RegEnable(requestIsMaskLoadStore)
    */
  val isMaskLoadStore: Bool =
    lsuRequestReg.instructionInformation.mop === 0.U && lsuRequestReg.instructionInformation.lumop(0)

  /** indicate the current instruction use mask to load/store. */
  val isMaskedLoadStore: Bool = lsuRequestReg.instructionInformation.maskedLoadStore

  /** indicate the current instruction is an indexed load/store(unordered/ordered). */
  val isIndexedLoadStore: Bool = lsuRequestReg.instructionInformation.mop(0)

  /** indicate the current request from Scheduler is a segment load store. This is used to calculate the next cycle of
    * EEW.
    */
  val requestIsWholeRegisterLoadStore: Bool = lsuRequest.bits.instructionInformation.mop === 0.U &&
    lsuRequest.bits.instructionInformation.lumop === 8.U

  /** indicate the current request from Scheduler is a load/store to mask. This is used to calculate the next cycle of
    * EEW.
    */
  val requestIsMaskLoadStore: Bool = lsuRequest.bits.instructionInformation.mop === 0.U &&
    lsuRequest.bits.instructionInformation.lumop(0)

  /** EEW of current request. see
    * [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#73-vector-loadstore-width-encoding]]
    * Table 11. Width encoding for vector loads and stores. for indexed load store.
    */
  val requestEEW: UInt =
    Mux(
      lsuRequest.bits.instructionInformation.mop(0),
      csrInterface.vSew,
      Mux(
        requestIsMaskLoadStore,
        0.U,
        Mux(requestIsWholeRegisterLoadStore, 2.U, lsuRequest.bits.instructionInformation.eew)
      )
    )

  /** nf of current request. */
  val requestNF: UInt = Mux(requestIsWholeRegisterLoadStore, 0.U, lsuRequest.bits.instructionInformation.nf)

  // latch lsuRequest
  /** for segment load/store, the data width to access for a group of element in the memory in byte. TODO:
    * MuxOH(requestEEW, (reqNF +& 1.U))
    */
  val dataWidthForSegmentLoadStore: UInt = RegEnable(
    (requestNF +& 1.U) * (1.U << requestEEW).asUInt(2, 0),
    0.U,
    lsuRequest.valid
  )

  /** expand EEW from [[requestEEW]] TODO: dedup with [[dataWidthForSegmentLoadStore]]
    */
  val elementByteWidth: UInt = RegEnable((1.U << requestEEW).asUInt(2, 0), 0.U, lsuRequest.valid)

  /** for segment instructions, the interval between VRF index accessing. e.g. vs0, vs2, vs4 ... for lmul less than 1,
    * the interval will be fixed to 1(ignore the frac lmul)
    */
  val segmentInstructionIndexInterval: UInt =
    RegEnable(
      Mux(
        // if vlmul(2) is 1, lmul is less than 1, see table in
        // [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#342-vector-register-grouping-vlmul20]]
        csrInterface.vlmul(2),
        1.U,
        (1.U << csrInterface.vlmul(1, 0)).asUInt(3, 0)
      ),
      0.U,
      lsuRequest.valid
    )

  /** a vector of bit indicate which memory transactions in D channel ack from memory bus. */
  val outstandingTLDMessages: UInt = RegInit(0.U(param.maxOffsetPerLaneAccess.W))

  /** There is no outstanding transactions in this MSHR. */
  val noOutstandingMessages: Bool = outstandingTLDMessages === 0.U

  /** the storeage of a group of offset for indexed instructions.
    *
    * @note
    *   this group is the offset group.
    */
  val indexedInstructionOffsets: Vec[ValidIO[UInt]] = RegInit(
    VecInit(Seq.fill(param.laneNumber)(0.U.asTypeOf(Valid(UInt(param.datapathWidth.W)))))
  )

  /** enable signal to update the offset group. */
  val updateOffsetGroupEnable: Bool = WireDefault(false.B)

  /** the current index of the offset group. */
  val groupIndex: UInt = RegInit(0.U(param.maxOffsetGroupSizeBits.W))

  /** used for update [[groupIndex]]. todo: vstart
    */
  val nextGroupIndex: UInt = Mux(lsuRequest.valid, 0.U, groupIndex + 1.U)
  when(updateOffsetGroupEnable) {
    groupIndex := nextGroupIndex
  }

  // TODO: remove me.
  val indexOfIndexedInstructionOffsetsNext: UInt = Wire(UInt(2.W))

  /** the current index in offset group for [[indexedInstructionOffsets]] TODO: remove `val
    * indexOfIndexedInstructionOffsetsNext: UInt = Wire(UInt(2.W))`
    */
  val indexOfIndexedInstructionOffsets: UInt =
    RegEnable(indexOfIndexedInstructionOffsetsNext, lsuRequest.valid || offsetQueueVec.head.deq.fire)
  indexOfIndexedInstructionOffsetsNext := Mux(lsuRequest.valid, 3.U(2.W), indexOfIndexedInstructionOffsets + 1.U)

  /** record the used [[indexedInstructionOffsets]] for sending memory transactions. */
  val usedIndexedInstructionOffsets: Vec[Bool] = Wire(Vec(param.laneNumber, Bool()))

  indexedInstructionOffsets.zipWithIndex.foreach { case (offset, index) =>
    // offsetReadResult(index).valid: new offset came
    // (offset.valid && !usedIndexedInstructionOffsets(index)): old unused offset
    offset.valid := offsetQueueVec(index).deq.fire ||
      (offset.valid && !usedIndexedInstructionOffsets(index) && !status.last)
    // select from new and old.
    offset.bits  := Mux(offsetQueueVec(index).deq.fire, offsetQueueVec(index).deq.bits, offset.bits)
  }

  /** register to latch mask */
  val maskReg: UInt = RegEnable(maskInput, 0.U, maskSelect.fire || lsuRequest.valid)

  /** the index of segment in TileLink message. */
  val segmentIndex: UInt = RegInit(0.U(3.W))

  /** counter to maintain [[segmentIndex]] */
  val segmentIndexNext: UInt = segmentIndex + 1.U

  /** signal indicates this is the last transaction for the element(without handshake) */
  val segmentEnd: Bool = segmentIndex === lsuRequestReg.instructionInformation.nf
  // update [[segmentIndex]]
  when((isSegmentLoadStore && s0Fire) || lsuRequest.valid) {
    segmentIndex := Mux(segmentEnd || lsuRequest.valid, 0.U, segmentIndexNext)
  }

  // [[!isSegmentLoadStore]]: segSize = 1 -> always segmentEnd
  val lastElementForSegment = !isSegmentLoadStore || segmentEnd

  /** signal indicates this is the last transaction for the element(with handshake) */
  val segmentEndWithHandshake: Bool = s0Fire && lastElementForSegment

  // states for [[state]]
  val idle :: sRequest :: wResponse :: Nil = Enum(3)

  /** MSHR state machine idle: the MSHR is in the idle state [[sRequest]]：require all data are ready for sending to s0
    * [[wResponse]]: data has been send to s0, the MSHR is waiting for response on TileLink D channel.
    *
    * TODO: add performance monitor on the FSM.
    */
  val state: UInt = RegInit(idle)

  // select next element.
  /** a vector of bit indicate which memory transactions in A channel are enqueued to TileLink A channel s0. if a
    * transaction is enqueued to s0, the corresponding bit will be assert.
    */
  val sentMemoryRequests: UInt = RegInit(0.U(param.maxOffsetPerLaneAccess.W))

  /** unsent memory transactions to s0. */
  val unsentMemoryRequests: UInt = (~sentMemoryRequests).asUInt

  /** mask [[unsentMemoryRequests]] */
  val maskedUnsentMemoryRequests: UInt = (maskReg & unsentMemoryRequests).asUInt(param.maskGroupWidth - 1, 0)

  /** the find the next [[maskedUnsentMemoryRequests]] */
  val findFirstMaskedUnsentMemoryRequests: UInt = ffo(maskedUnsentMemoryRequests)

  /** the find the next [[unsentMemoryRequests]] */
  val findFirstUnsentMemoryRequestsNext: UInt = (sentMemoryRequests ## true.B) & unsentMemoryRequests

  /** the next element used for memory request. TODO: find first one after Mux?
    */
  val nextElementForMemoryRequest: UInt =
    Mux(
      isMaskedLoadStore,
      findFirstMaskedUnsentMemoryRequests,
      findFirstUnsentMemoryRequestsNext
    )(param.maskGroupWidth - 1, 0)

  /** index of the next element for a mask group used for memory request. */
  val nextElementForMemoryRequestIndex: UInt =
    OHToUInt(nextElementForMemoryRequest)(param.maxOffsetPerLaneAccessBits - 1, 0)
  // update [[sentMemoryRequests]]
  when(segmentEndWithHandshake || updateOffsetGroupEnable) {
    // TODO: after moving find first one, sentMemoryRequests := nextElementForMemoryRequest || sentMemoryRequests
    sentMemoryRequests := Mux(updateOffsetGroupEnable, 0.U, scanRightOr(nextElementForMemoryRequest))
  }

  /** EEW for element. */
  val dataEEW: UInt =
    Mux(
      isIndexedLoadStore,
      // EEW from CSR
      csrInterfaceReg.vSew,
      // EEW from intermediate
      Mux(
        isMaskLoadStore,
        // For mask load store, EEW is fixed to 8.
        0.U,
        Mux(
          isWholeRegisterLoadStore,
          // for whole register load store, just use the maximum EEW
          2.U,
          // otherwise use intermediate from instruction.
          lsuRequestReg.instructionInformation.eew
        )
      )
    )

  /** 1H version for [[dataEEW]] */
  val dataEEWOH: UInt = UIntToOH(dataEEW)

  /** no more masked memory request. */
  val noMoreMaskedUnsentMemoryRequests: Bool = maskedUnsentMemoryRequests === 0.U

  /** mask need to be updated in the next cycle, this is the signal to update mask. this signal only apply to mask type
    * instruction.
    */
  val maskGroupEndAndRequestNewMask: Bool = (
    noMoreMaskedUnsentMemoryRequests ||
      (nextElementForMemoryRequest(param.maskGroupWidth - 1) && segmentEndWithHandshake)
  ) && isMaskedLoadStore

  /** the end of mask group. TODO: duplicate with [[maskGroupEndAndRequestNewMask]]
    */
  val maskGroupEnd: Bool = {
    // mask type instruction
    maskGroupEndAndRequestNewMask ||
    // segment type instruction
    (
      // the last element for the mask group
      nextElementForMemoryRequest(param.maskGroupWidth - 1) &&
        // enqueue to pipeline
        s0Fire &&
        // the last segment for element
        lastElementForSegment
    )
  }

  maskSelect.valid := maskGroupEndAndRequestNewMask

  /** EEW for offset instructions. offset EEW is always from instruction.
    */
  val offsetEEW: UInt = lsuRequestReg.instructionInformation.eew

  /** onehot of [[offsetEEW]] */
  val offsetEEWOH = UIntToOH(offsetEEW)(2, 0)

  // for each instruction, if using indexed memory load store instructions,
  // it corresponds to multiple memory requests(depending on nf and elements)
  // for each requests, the address of requests depends on `base`(from scalar rs1) and `offset`(from vrf vs2)
  // `offset` is the value of the element(read from VRF)
  // for each offset group, LSU will read `laneNumber` * `datapathWidth` from lanes,
  // it will be store to [[indexedInstructionOffsets]]
  // then extract the offset from [[indexedInstructionOffsets]]:
  // we define [[globalOffsetOfIndexedInstructionOffsets]] with [[nextElementForMemoryRequestIndex]] and [[offsetEEW]]
  // the MSB of [[globalOffsetOfIndexedInstructionOffsets]] is the offset group of current memory request,
  // this is used for match offset group
  // the LSB of [[globalOffsetOfIndexedInstructionOffsets]] is offset of the offset group
  // this is used for extract the current offset need to be used from [[indexedInstructionOffsets]]

  val offsetIndex: UInt = groupIndex ## nextElementForMemoryRequestIndex

  /** global(instruction level) offset to [[indexedInstructionOffsets]] [[nextElementForMemoryRequestIndex]] is the
    * current index of memory request. use [[offsetEEW]] to multiply this index TODO: use Mux1H here
    */
  val globalOffsetOfIndexedInstructionOffsets: UInt =
    (offsetIndex << offsetEEW).asUInt(nextElementForMemoryRequestIndex.getWidth + 1, 0)

  /** MSB of [[globalOffsetOfIndexedInstructionOffsets]], indicate the offset group of current memory request. */
  val offsetGroupIndexOfMemoryRequest: UInt =
    globalOffsetOfIndexedInstructionOffsets(
      nextElementForMemoryRequestIndex.getWidth + 1,
      nextElementForMemoryRequestIndex.getWidth
    )

  /** LSB of [[globalOffsetOfIndexedInstructionOffsets]], used for extract the current offset need to be used from
    * [[indexedInstructionOffsets]]
    */
  val offsetOfOffsetGroup: UInt =
    globalOffsetOfIndexedInstructionOffsets(nextElementForMemoryRequestIndex.getWidth - 1, 0)

  /** the LSB of extract value [[indexedInstructionOffsets]] */
  val offsetOfCurrentMemoryRequest: UInt =
    // shift [[indexedInstructionOffsets]] to extract the current offset for memory request
    (VecInit(indexedInstructionOffsets.map(_.bits)).asUInt >> (offsetOfOffsetGroup ## 0.U(3.W)))
      // the LSB of shift result is the what we need.
      .asUInt(param.datapathWidth - 1, 0) &
      // use SEW to mask the shift result
      FillInterleaved(8, offsetEEWOH(2) ## offsetEEWOH(2) ## !offsetEEWOH(0) ## true.B)

  /** check offset we are using is valid or not. */
  val offsetValidCheck: Bool =
    (
      VecInit(indexedInstructionOffsets.map(_.valid)).asUInt >> (
        // offsetOfOffsetGroup is in byte level
        offsetOfOffsetGroup >>
          // shift it to word level
          log2Ceil(param.datapathWidth / 8)
      ).asUInt
    ).asUInt(0)

  /** if the current memory request matches offset group. */
  val offsetGroupMatch: UInt = offsetGroupIndexOfMemoryRequest ^ indexOfIndexedInstructionOffsets

  /** for the case that EEW!=0, offset group maybe misaligned with the mask group
    *   - eew = 0, offset group is aligned with mask group
    *   - eew = 1, one mask group corresponds 2 offset group, [[offsetGroupIndexOfMemoryRequest(0)]] need to match
    *     [[indexOfIndexedInstructionOffsets(0)]]
    *   - eew = 2, one mask group corresponds 4 offset group, [[offsetGroupIndexOfMemoryRequest]] need to match
    *     [[indexOfIndexedInstructionOffsets]]
    */
  val offsetGroupCheck: Bool = (!offsetEEW(0) || !offsetGroupMatch(0)) && (!offsetEEW(1) || offsetGroupMatch === 0.U)

  /** offset for unit stride instruction. */
  val offsetForUnitStride: UInt = groupIndex ## nextElementForMemoryRequestIndex

  /** offset for stride instruction. */
  val offsetForStride: UInt = (groupIndex ## nextElementForMemoryRequestIndex) * lsuRequestReg.rs2Data

  /** final offset for element */
  val baseOffsetForElement: UInt = Mux(
    isIndexedLoadStore,
    offsetOfCurrentMemoryRequest,
    Mux(
      lsuRequestReg.instructionInformation.mop(1),
      offsetForStride,
      offsetForUnitStride * dataWidthForSegmentLoadStore
    )
  )

  /** which lane does the current [[offsetOfOffsetGroup]] represent. */
  val laneOfOffsetOfOffsetGroup: UInt =
    if (param.laneNumber > 1)
      UIntToOH(
        offsetOfOffsetGroup(
          log2Ceil(param.datapathWidth / 8) + log2Ceil(param.laneNumber) - 1,
          log2Ceil(param.datapathWidth / 8)
        )
      )
    else 0.U

  /** one of [[indexedInstructionOffsets]] is exhausted. */
  val indexedInstructionOffsetExhausted: Bool =
    Mux1H(
      offsetEEWOH,
      Seq(
        // EEW = 8
        offsetIndex(param.dataPathByteBits - 1, 0).andR,
        // EEW = 16
        offsetIndex(param.dataPathByteBits - 2, 0).andR,
        // EEW = 32
        if (param.dataPathByteBits > 2) offsetIndex(param.dataPathByteBits - 3, 0).andR else true.B
      )
    )

  /** 各个类型的换组的标志: 3. 如果是index类型的,那么在index耗尽的时候需要更换index,只有在index的粒度8的时候index和mask才同时耗尽. 4.
    * 如果index与mask不匹配,index在mask中的偏移由[[indexOfIndexedInstructionOffsets]]记录. 5. unit stride 和 stride 类型的在没有mask的前提下
    * [[nextElementForMemoryRequest]] 最高位拉高才换组.
    */

  // update [[usedIndexedInstructionOffsets]]
  Seq.tabulate(param.laneNumber) { i =>
    usedIndexedInstructionOffsets(i) :=
      (
        // the offset of the memory transaction belongs to lane
        laneOfOffsetOfOffsetGroup(i) &&
          // the last memory transaction is sent
          segmentEndWithHandshake &&
          // the tail of [[laneOfOffsetOfOffsetGroup]] is sent
          indexedInstructionOffsetExhausted
      ) ||
        // change offset group
        (requestOffset && waitQueueDeq.asUInt.andR) ||
        // change mask group
        // TODO: remove [[maskNeedUpdate]]?
        maskGroupEndAndRequestNewMask
  }

  /** onehot version of LSB of `tlPort.a.bits.source` */
  val memoryRequestSourceOH: UInt = Wire(UInt(param.maxOffsetPerLaneAccess.W))

  /** detect the case segment load store hazard. */
  val sourceFree: Bool = !(memoryRequestSourceOH & outstandingTLDMessages).orR

  /** current state machine is on [[sRequest]]. */
  val stateIsRequest: Bool = state === sRequest

  /** next element of instruction for memory request. */
  val nextElementIndex: UInt =
    // for [[wResponse]] being able to address, don't update [[groupIndex]] for now,
    // choose [[nextGroupIndex]], since [[sRequest]] has already send all memory requests in the [[groupIndex]]
    Mux(stateIsRequest, groupIndex, nextGroupIndex) ##
      Mux(stateIsRequest, nextElementForMemoryRequestIndex, 0.U(nextElementForMemoryRequestIndex.getWidth.W))

  /** evl for [[isWholeRegisterLoadStore]] instruction type. we use the maximum [[dataEEW]] to handle it.
    */
  val wholeEvl: UInt =
    (lsuRequestReg.instructionInformation.nf +& 1.U) ## 0.U(log2Ceil(param.vLen / param.datapathWidth).W)

  /** evl for the instruction */
  val evl: UInt = Mux(
    isWholeRegisterLoadStore,
    wholeEvl,
    Mux(
      isMaskLoadStore,
      csrInterfaceReg.vl(param.vlMaxBits - 1, 3) + csrInterfaceReg.vl(2, 0).orR,
      csrInterfaceReg.vl
    )
  )

  /** the current element is the last element to execute in the pipeline. */
  val last: Bool = nextElementIndex >= evl

  /** no need mask, there still exist unsent masked requests, don't need to update mask. */
  val maskCheck: Bool = !isMaskedLoadStore || !noMoreMaskedUnsentMemoryRequests

  /** no need index, when use a index, check it is valid or not. */
  val indexCheck: Bool = !isIndexedLoadStore || (offsetValidCheck && offsetGroupCheck)

  // handle fault only first
  /** the current TileLink message in A Channel is the first transaction in this instruction. */
  val firstMemoryRequestOfInstruction: Bool =
    RegEnable(lsuRequest.valid, false.B, lsuRequest.valid || memReadRequest.fire)

  /** if assert, need to wait for memory response, this is used for fault only first instruction. */
  val waitFirstMemoryResponseForFaultOnlyFirst: Bool =
    RegEnable(
      lsuRequest.valid && lsuRequest.bits.instructionInformation.fof,
      false.B,
      lsuRequest.valid || memReadResponse.fire
    )

  /** signal to check the the first memory request is responded for fault only first instruction. */
  val fofCheck: Bool = firstMemoryRequestOfInstruction || !waitFirstMemoryResponseForFaultOnlyFirst

  /** all check is ready, being able to send request to pipeline. */
  val stateReady: Bool = stateIsRequest && maskCheck && indexCheck && fofCheck

  // state === idle: All the remaining elements are removed by the mask,
  // but there is still offset left.
  /** signal to request offset in the pipeline, only assert for one cycle. */
  requestOffset := stateIsRequest && maskCheck && !indexCheck && fofCheck

  val s0DequeueFire: Bool = Wire(Bool())

  /** valid signal to enqueue to s0. */
  val s0EnqueueValid: Bool = stateReady && !last

  /** there exist valid signal inside s0. */
  val s0Valid: Bool = RegEnable(s0Fire, false.B, s0Fire ^ s0DequeueFire)

  /** request enqueue to s0. */
  val s0Wire: MSHRStage0Bundle = Wire(new MSHRStage0Bundle(param))

  /** request inside s0. */
  val s0Reg: MSHRStage0Bundle = RegEnable(s0Wire, 0.U.asTypeOf(s0Wire), s0Fire)

  /** ready signal to enqueue to s1. */
  val s1EnqueueReady: Bool = Wire(Bool())

  /** element index enqueuing to s0. */
  val s0ElementIndex: UInt = groupIndex ## nextElementForMemoryRequestIndex

  // Reading vrf may take multiple cycles and requires additional information to be stored
  val s1EnqQueue:     QueueIO[SimpleAccessStage1] =
    Queue.io(new SimpleAccessStage1(param), param.vrfReadLatency + param.lsuReadShifterLatency + 2)
  val s1EnqDataQueue: QueueIO[UInt]               =
    Queue.io(UInt(param.datapathWidth.W), param.vrfReadLatency + param.lsuReadShifterLatency + 2)

  /** which byte to access in VRF, e.g. VLEN=1024,datapath=32,laneNumber=8 XXXXXXXXXX <- 10 bits for element(32bits)
    * index XX <- 2 bits for SEW XXXXXXXXXX <- strip MSB for the constraint that sew*vlmax <= 8*VLEN <-
    * [[storeBaseByteOffset]] XX <- offset for datapath XXX <- offset for lane XX <- offset for vs in lane(one vector
    * register is split to 4 lines in each lane) XXX <- offset for vector register
    */
  val storeBaseByteOffset: UInt = (s0ElementIndex << dataEEW).asUInt(log2Ceil(param.vLen) - 1, 0)

  // s0 calculate register offset
  s0Wire.readVS        :=
    // base vs in the instruction
    lsuRequestReg.instructionInformation.vs3 +
      // vs offset for segment instructions
      Mux(
        isSegmentLoadStore,
        // TODO strip to 3 bits
        segmentIndex * segmentInstructionIndexInterval,
        0.U
      ) +
      // vs offset for element index
      storeBaseByteOffset(
        // VLEN=1024 -> 9
        log2Ceil(param.vLen) - 1,
        // VLEN=1024,datapath=32,laneNumber=8 -> 7
        log2Ceil(param.datapathWidth / 8) + log2Ceil(param.laneNumber) + param.vrfOffsetBits
      )
  s0Wire.offsetForVSInLane.foreach {
    _ := storeBaseByteOffset(
      // VLEN=1024,datapath=32,laneNumber=8 -> 6
      log2Ceil(param.datapathWidth / 8) + log2Ceil(param.laneNumber) + param.vrfOffsetBits - 1,
      // VLEN=1024,datapath=32,laneNumber=8 -> 5
      log2Ceil(param.datapathWidth / 8) + log2Ceil(param.laneNumber)
    )
  }
  s0Wire.addressOffset := baseOffsetForElement + (elementByteWidth * segmentIndex)
  s0Wire.indexInGroup  := nextElementForMemoryRequestIndex
  s0Wire.segmentIndex  := segmentIndex
  s0Wire.offsetForLane := {
    if (param.laneNumber > 1)
      storeBaseByteOffset(
        // datapath=32,laneNumber=8 -> 4
        log2Ceil(param.datapathWidth / 8) + log2Ceil(param.laneNumber) - 1,
        // datapath=32 -> 2
        log2Ceil(param.datapathWidth / 8)
      )
    else 0.U
  }

  // s1 access VRF
  // TODO: perf `lsuRequestReg.instructionInformation.isStore && vrfReadDataPorts.ready` to check the VRF bandwidth
  //       limitation affecting to LSU store.
  vrfReadDataPorts.valid                 := s0Valid && lsuRequestReg.instructionInformation.isStore && s1EnqQueue.enq.ready
  vrfReadDataPorts.bits.offset           := s0Reg.offsetForVSInLane.getOrElse(DontCare)
  vrfReadDataPorts.bits.vs               := s0Reg.readVS
  vrfReadDataPorts.bits.readSource       := 2.U
  vrfReadDataPorts.bits.instructionIndex := lsuRequestReg.instructionIndex

  /** ready to read VRF to store to memory. */
  val readReady: Bool = !lsuRequestReg.instructionInformation.isStore || vrfReadDataPorts.ready

  /** data is valid in s1 */
  val s1Valid: Bool = RegEnable(s1Fire, false.B, s1Fire ^ s2Fire)

  /** request enqueue to s1. */
  val s1Wire: SimpleAccessStage1 = Wire(new SimpleAccessStage1(param))

  /** request inside s1. */
  val s1Reg: SimpleAccessStage1 = RegEnable(s1Wire, 0.U.asTypeOf(s1Wire), s1Fire)

  val memRequestReady = Mux(lsuRequestReg.instructionInformation.isStore, memWriteRequest.ready, memReadRequest.ready)

  /** ready to enqueue to s2. */
  val s2EnqueueReady: Bool = memRequestReady && sourceFree

  s1EnqueueReady := s2EnqueueReady || !s1Valid

  /** ready signal to enqueue to s0. */
  val s0EnqueueReady: Bool = (s1EnqQueue.enq.ready && readReady) || !s0Valid
  s0Fire := s0EnqueueReady && s0EnqueueValid

  /** pipeline is flushed. */
  val pipelineClear: Bool = !s0Valid && !s1Valid && !s1EnqQueue.deq.valid

  s0DequeueFire                        := s1EnqQueue.enq.fire
  s1EnqQueue.enq.valid                 := s0Valid && readReady
  s1EnqQueue.enq.bits.address          := lsuRequestReg.rs1Data + s0Reg.addressOffset
  s1EnqQueue.enq.bits.indexInMaskGroup := s0Reg.indexInGroup
  s1EnqQueue.enq.bits.segmentIndex     := s0Reg.segmentIndex
  s1EnqQueue.enq.bits.readData         := DontCare
  // pipe read data
  s1EnqDataQueue.enq.valid             := vrfReadResults.valid
  AssertProperty(BoolSequence(s1EnqDataQueue.enq.ready || !vrfReadResults.valid))
  s1EnqDataQueue.enq.bits              := vrfReadResults.bits

  s1Wire.address          := s1EnqQueue.deq.bits.address
  s1Wire.indexInMaskGroup := s1EnqQueue.deq.bits.indexInMaskGroup
  s1Wire.segmentIndex     := s1EnqQueue.deq.bits.segmentIndex
  s1Wire.readData         := s1EnqDataQueue.deq.bits

  val s1DataEnqValid: Bool = s1EnqDataQueue.deq.valid || !lsuRequestReg.instructionInformation.isStore
  val s1EnqValid:     Bool = s1DataEnqValid && s1EnqQueue.deq.valid
  s1Fire                   := s1EnqValid && s1EnqueueReady
  s1EnqQueue.deq.ready     := s1EnqueueReady && s1DataEnqValid
  s1EnqDataQueue.deq.ready := s1EnqueueReady

  val addressInBeatByte: UInt = s1Reg.address(log2Ceil(param.eLen / 8) - 1, 0)
  // 1 -> 1 2 -> 3 4 -> 15
  val baseMask:          UInt = dataEEWOH(2) ## dataEEWOH(2) ## !dataEEWOH(0) ## true.B

  /** compute the mask for store transaction. */
  val storeMask: UInt = (baseMask << addressInBeatByte).asUInt(param.eLen / 8 - 1, 0)

  /** offset caused by element index(byte level) in the datapath. */
  val storeOffsetByIndex: UInt =
    (s1Reg.indexInMaskGroup(param.dataPathByteBits - 1, 0) << dataEEW).asUInt(param.dataPathByteBits - 1, 0)

  val addressOffSetForDataPath: Int = log2Ceil(param.eLen / 8)

  /** align the VRF index in datapath and memory. TODO: use Mux1H to select(only 4 cases).
    */
  val storeData: UInt =
    ((s1Reg.readData << (addressInBeatByte ## 0.U(3.W))) >> (storeOffsetByIndex ## 0.U(3.W))).asUInt
  // only PutFull / Get for now

  /** source for memory request make volatile field LSB to reduce the source conflict.
    */
  val memoryRequestSource: UInt = Mux(
    isSegmentLoadStore,
    s1Reg.indexInMaskGroup ## s1Reg.segmentIndex,
    s1Reg.indexInMaskGroup
  )
  memoryRequestSourceOH       := UIntToOH(memoryRequestSource(log2Ceil(param.maxOffsetPerLaneAccess) - 1, 0))
  // offset index + segment index
  // log(32)-> 5    log(8) -> 3
  memReadRequest.bits.source  := memoryRequestSource
  memReadRequest.bits.address := (s1Reg.address >> addressOffSetForDataPath) << addressOffSetForDataPath
  memReadRequest.bits.size    := log2Ceil(param.eLen / 8).U
  memReadRequest.valid        := s1Valid && sourceFree && !lsuRequestReg.instructionInformation.isStore

  memWriteRequest.valid        := s1Valid && sourceFree && lsuRequestReg.instructionInformation.isStore
  memWriteRequest.bits.address := s1Reg.address
  memWriteRequest.bits.size    := dataEEW
  memWriteRequest.bits.data    := storeData
  memWriteRequest.bits.mask    := storeMask
  memWriteRequest.bits.source  := memoryRequestSource

  val offsetRecord = RegInit(
    VecInit(Seq.fill(memoryRequestSourceOH.getWidth)(0.U(log2Ceil(param.eLen / 8).W)))
  )

  offsetRecord.zipWithIndex.foreach { case (d, i) =>
    when(memReadRequest.fire && memoryRequestSourceOH(i)) {
      d := s1Reg.address
    }
  }

  // Handle response

  /** extract `indexInMaskGroup` from response. */
  val indexInMaskGroupResponse: UInt = Mux(
    isSegmentLoadStore,
    (memReadResponse.bits.source >> 3).asUInt,
    memReadResponse.bits.source
  )(param.maxOffsetPerLaneAccessBits - 1, 0)

  /** the LSB(maskGroupWidth) for response, MSHR only maintains maskGroupWidth of request. */
  val responseSourceLSBOH: UInt = UIntToOH(memReadResponse.bits.source(log2Ceil(param.maxOffsetPerLaneAccess) - 1, 0))

  val loadBaseElementIndex = groupIndex ## indexInMaskGroupResponse

  /** which byte to access in VRF for load instruction. see [[storeBaseByteOffset]]
    */
  val loadBaseByteOffset: UInt = (loadBaseElementIndex << dataEEW).asUInt
  vrfWritePort.valid := memReadResponse.valid
  val addressOffset =
    offsetRecord(memReadResponse.bits.source(log2Ceil(param.maxOffsetPerLaneAccess) - 1, 0)) ## 0.U(3.W)
  memReadResponse.ready := vrfWritePort.ready

  // TODO: handle alignment for VRF and memory
  vrfWritePort.bits.data             := (
    ((memReadResponse.bits.data >> addressOffset) << (loadBaseByteOffset(param.dataPathByteBits - 1, 0) ## 0.U(3.W)))
  ).asUInt
  vrfWritePort.bits.last             := last
  vrfWritePort.bits.instructionIndex := lsuRequestReg.instructionIndex
  vrfWritePort.bits.mask             := Mux1H(
    dataEEWOH(2, 0),
    Seq(
      UIntToOH(loadBaseElementIndex(param.dataPathByteBits - 1, 0)),
      FillInterleaved(2, UIntToOH(loadBaseElementIndex(param.dataPathByteBits - 2, 0))),
      if (param.dataPathByteBits >= 3)
        FillInterleaved(4, UIntToOH(loadBaseElementIndex(param.dataPathByteBits - 3, 0)))
      else
        -1.S(vrfWritePort.bits.mask.getWidth.W).asUInt
    )
  )
  // calculate vd register offset
  vrfWritePort.bits.vd               :=
    // base vd in the instruction
    lsuRequestReg.instructionInformation.vs3 +
      // vd offset for segment instructions
      Mux(
        isSegmentLoadStore,
        memReadResponse.bits.source(2, 0) * segmentInstructionIndexInterval,
        0.U
      ) +
      // vd offset for element index
      loadBaseByteOffset(
        // VLEN=1024 -> 9
        log2Ceil(param.vLen) - 1,
        // VLEN=1024,datapath=32,laneNumber=8 -> 7
        log2Ceil(param.datapathWidth / 8) + log2Ceil(param.laneNumber) + param.vrfOffsetBits
      )
  val writeOffset: UInt = if (param.vrfOffsetBits > 0) {
    loadBaseByteOffset(
      // VLEN=1024,datapath=32,laneNumber=8 -> 6
      log2Ceil(param.datapathWidth / 8) + log2Ceil(param.laneNumber) + param.vrfOffsetBits - 1,
      // VLEN=1024,datapath=32,laneNumber=8 -> 5
      log2Ceil(param.datapathWidth / 8) + log2Ceil(param.laneNumber)
    )
  } else 0.U
  vrfWritePort.bits.offset := writeOffset

  // update [[outstandingTLDMessages]]
  when((memReadResponse.fire || memReadRequest.fire) && !lsuRequestReg.instructionInformation.isStore) {
    // 同时进出得让相应被拉高
    outstandingTLDMessages := (outstandingTLDMessages &
      // free outstanding source since got response from memory.
      ~Mux(
        memReadResponse.fire,
        responseSourceLSBOH,
        0.U(param.maxOffsetPerLaneAccess.W)
      ): UInt) |
      // allocate outstanding source since corresponding memory request has been issued.
      Mux(
        memReadRequest.fire,
        memoryRequestSourceOH,
        0.U(param.maxOffsetPerLaneAccess.W)
      )
  }

  // update state
  when(state === sRequest) {
    when(last || maskGroupEnd) {
      state := wResponse
    }
  }
  // if all responses are cleared
  when(
    state === wResponse && noOutstandingMessages && pipelineClear
    // TODO: cosim bug for multiple response for same request!!!
      && !memReadResponse.valid
  ) {
    // switch state to idle
    when(last) {
      state := idle
    }
      // change mask group
      .otherwise {
        state                   := sRequest
        updateOffsetGroupEnable := true.B
      }
  }
  // handle corner case for vl=0
  val invalidInstruction:     Bool = csrInterface.vl === 0.U && !requestIsWholeRegisterLoadStore && lsuRequest.valid
  val invalidInstructionNext: Bool = RegNext(invalidInstruction)
  val allElementsMasked:      Bool = state === idle && offsetQueueVec.map(_.deq.fire).reduce(_ || _)

  // change state to request
  when(lsuRequest.valid && !invalidInstruction) {
    state                   := sRequest
    updateOffsetGroupEnable := true.B
  }

  // expose state for this MSHR
  status.instructionIndex := lsuRequestReg.instructionIndex

  /** the current state is idle. */
  stateIdle                := state === idle
  status.idle              := stateIdle
  status.last              := (!RegNext(stateIdle) && stateIdle) || invalidInstructionNext || allElementsMasked
  status.changeMaskGroup   := updateOffsetGroupEnable
  // which lane to access
  status.targetLane        := {
    if (param.laneNumber > 1)
      UIntToOH(
        Mux(
          lsuRequestReg.instructionInformation.isStore,
          // read VRF
          s0Reg.offsetForLane,
          // write VRF
          loadBaseByteOffset(
            // datapath=32,laneNumber=8 -> 4
            log2Ceil(param.datapathWidth / 8) + log2Ceil(param.laneNumber) - 1,
            // datapath=32 -> 2
            log2Ceil(param.datapathWidth / 8)
          )
        )
      )
    else 1.U
  }
  status.waitFirstResponse := waitFirstMemoryResponseForFaultOnlyFirst
  status.isStore           := lsuRequestReg.instructionInformation.isStore
  status.isIndexLS         := lsuRequestReg.instructionInformation.mop(0)
  // which mask to request
  maskSelect.bits          := nextGroupIndex
  status.startAddress      := DontCare
  status.endAddress        := DontCare

  /** probes for monitoring internal signal
    */
  val dataOffset = (s1EnqQueue.deq.bits.indexInMaskGroup << dataEEW)(1, 0) ## 0.U(3.W)

  @public
  val lsuRequestValidProbe = IO(Output(Probe(Bool(), layers.Verification)))

  @public
  val s0EnqueueValidProbe = IO(Output(Probe(Bool(), layers.Verification)))
  @public
  val stateIsRequestProbe = IO(Output(Probe(Bool(), layers.Verification)))
  @public
  val maskCheckProbe      = IO(Output(Probe(Bool(), layers.Verification)))
  @public
  val indexCheckProbe     = IO(Output(Probe(Bool(), layers.Verification)))
  @public
  val fofCheckProbe       = IO(Output(Probe(Bool(), layers.Verification)))

  @public
  val s0FireProbe: Bool = IO(Output(Probe(chiselTypeOf(s0Fire), layers.Verification)))

  @public
  val s1FireProbe: Bool = IO(Output(Probe(chiselTypeOf(s1Fire), layers.Verification)))

//  @public
//  val tlPortAReadyProbe = IO(Output(Probe(Bool())))
//  define(tlPortAReadyProbe, ProbeValue(tlPort.a.ready))
//  @public
//  val tlPortAValidProbe = IO(Output(Probe(Bool())))
//  define(tlPortAValidProbe, ProbeValue(tlPort.a.valid))

  @public
  val s1ValidProbe    = IO(Output(Probe(Bool(), layers.Verification)))
  @public
  val sourceFreeProbe = IO(Output(Probe(Bool(), layers.Verification)))

  @public
  val s2FireProbe: Bool = IO(Output(Probe(chiselTypeOf(s2Fire), layers.Verification)))

//  @public
//  val tlPortDReadyProbe = IO(Output(Probe(Bool())))
//  define(tlPortDReadyProbe, ProbeValue(tlPort.d.ready))
//  @public
//  val tlPortDValidProbe = IO(Output(Probe(Bool())))
//  define(tlPortDValidProbe, ProbeValue(tlPort.d.valid))

  @public
  val stateValueProbe: UInt = IO(Output(Probe(chiselTypeOf(state), layers.Verification)))

  @public
  val vrfWritePortIsValidProbe: Bool = IO(Output(Probe(Bool(), layers.Verification)))
  @public
  val vrfWritePortIsReadyProbe: Bool = IO(Output(Probe(Bool(), layers.Verification)))

  layer.block(layers.Verification) {
    val probeWire = Wire(new MemoryWriteProbe(param))
    define(probe, ProbeValue(probeWire))
    probeWire.valid   := memWriteRequest.fire
    probeWire.index   := 2.U
    probeWire.data    := memWriteRequest.bits.data
    probeWire.mask    := memWriteRequest.bits.mask
    probeWire.address := memWriteRequest.bits.address
    define(lsuRequestValidProbe, ProbeValue(lsuRequest.valid))
    define(s0EnqueueValidProbe, ProbeValue(s0EnqueueValid))
    define(stateIsRequestProbe, ProbeValue(stateIsRequest))
    define(maskCheckProbe, ProbeValue(maskCheck))
    define(indexCheckProbe, ProbeValue(indexCheck))
    define(fofCheckProbe, ProbeValue(fofCheck))
    define(s0FireProbe, ProbeValue(s0Fire))
    define(s1FireProbe, ProbeValue(s1Fire))
    define(s1ValidProbe, ProbeValue(s1Valid))
    define(sourceFreeProbe, ProbeValue(sourceFree))
    define(s2FireProbe, ProbeValue(s2Fire))
    define(stateValueProbe, ProbeValue(state))
    define(vrfWritePortIsValidProbe, ProbeValue(vrfWritePort.valid))
    define(vrfWritePortIsReadyProbe, ProbeValue(vrfWritePort.ready))
  }

}
