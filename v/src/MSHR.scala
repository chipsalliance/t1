package v

import chisel3._
import chisel3.util._
import tilelink.{TLBundle, TLBundleParameter, TLChannelA, TLChannelAParameter, TLChannelDParameter}

/**
  * @param datapathWidth ELEN
  * @param chainingSize  how many instructions can be chained
  * @param vLen          VLEN
  * @param laneNumber    how many lanes in the vector processor
  * @param paWidth       physical address width
  * @note
  * MSHR group:
  * The memory access of a single instruction will be grouped into MSHR group.
  * Because indexed load/store need to access VRF to get the offset of memory address to access,
  * we use the maximum count of transactions of indexed load/store in each memory access operation for each lanes
  * to calculate the size of MSHR.
  * The MSHR group maintains a group of transactions with a set of base address.
  *
  * Refactor Plan:
  * - merge memory request for cacheline not per element
  * -- natively interleaving memory request since bank bits is not in cacheline tags
  * -- save bandwidth in A Channel
  * -- save bandwidth in L2 Cache(improve efficiency in cache directory)
  * -- save request queue size in L2(less back pressure on bus)
  * -- use PutPartial mask for masked store
  * -- burst will block other memory requests until it is finished(this is limited by TileLink)
  *
  * - memory request hazard detection for multiple instructions
  * -- unit stride without segment: instruction order, base address per instruction, current mask group index, mask inside mask group
  * -- unit stride with segment: instruction order, base address per instruction, nf per instruction, current mask group index, mask inside mask group
  * -- stride without segment: instruction order, stride per instruction, base address per instruction, current mask group index, mask inside mask group
  * -- stride with segment: instruction order, stride per instruction, base address per instruction, nf per instruction, current mask group index, mask inside mask group
  * -- indexed without segment
  * -- indexed with segment
  * based on the mask group granularity to detect hazard for unit stride and stride instruction
  */
case class MSHRParam(
  chainingSize:  Int,
  datapathWidth: Int,
  vLen:          Int,
  laneNumber:    Int,
  paWidth:       Int,
  cacheLineSize: Int,
  outerTLParam:  TLBundleParameter) {

  /** see [[LaneParameter.lmulMax]] */
  val lmulMax: Int = 8

  /** see [[LaneParameter.sewMin]] */
  val sewMin: Int = 8

  /** see [[LaneParameter.vlMax]] */
  val vlMax = vLen * lmulMax / sewMin

  /** see [[LaneParameter.vlMaxBits]] */
  val vlMaxBits: Int = log2Ceil(vlMax) + 1

  /** the maximum address offsets number can be accessed from lanes for one time. */
  val maxOffsetPerLaneAccess: Int = datapathWidth * laneNumber / sewMin

  /** the maximum size of memory requests a MSHR can maintain.
    *
    * @note this is mask size
    */
  val maxOffsetGroupSize: Int = vLen / maxOffsetPerLaneAccess

  /** The hardware length of [[maxOffsetPerLaneAccess]] */
  val maxOffsetPerLaneAccessBits: Int = log2Ceil(maxOffsetPerLaneAccess)

  /** The hardware length of [[maxOffsetGroupSize]]
    * `+1` is because we always use the next group to decide whether the current group is the last group.
    */
  val maxOffsetGroupSizeBits: Int = log2Ceil(maxOffsetGroupSize + 1)

  /** See [[VParameter.sourceWidth]], due to we are in the MSHR, the `log2Ceil(lsuMSHRSize)` is dropped.
    */
  val sourceWidth: Int = {
    maxOffsetPerLaneAccessBits + // offset group
      3 // segment index, this is decided by spec.
  }

  /** See [[VParameter.maskGroupWidth]] */
  val maskGroupWidth: Int = maxOffsetPerLaneAccess

  /** See [[VParameter.maskGroupSize]] */
  val maskGroupSize: Int = vLen / maskGroupWidth

  /** The hardware length of [[maskGroupSize]] */
  val maskGroupSizeBits: Int = log2Ceil(maskGroupSize)

  /** override [[LSUParam.tlParam]] to purge the MSHR source. */
  val tlParam: TLBundleParameter = outerTLParam.copy(
    a = outerTLParam.a.copy(sourceWidth = sourceWidth),
    d = outerTLParam.d.copy(sourceWidth = sourceWidth)
  )

  /** see [[VRFParam.regNumBits]] */
  val regNumBits: Int = log2Ceil(32)

  /** see [[LaneParameter.instructionIndexBits]] */
  val instructionIndexBits: Int = log2Ceil(chainingSize) + 1

  /** see [[LaneParameter.singleGroupSize]] */
  val singleGroupSize: Int = vLen / datapathWidth / laneNumber

  /** see [[LaneParameter.vrfOffsetBits]] */
  val vrfOffsetBits: Int = log2Ceil(singleGroupSize)

  /** offset bit for a cache line */
  val cacheLineBits: Int = log2Ceil(cacheLineSize)

  val bustCount = cacheLineSize * 8 / datapathWidth

  val bustCountBits: Int = log2Ceil(bustCount)

  /** The maximum number of cache lines that will be accessed, a counter is needed.
    * +1 Corresponding unaligned case
    * */
  val cacheLineIndexBits: Int = log2Ceil(vLen/cacheLineSize + 1)
}

class MSHRStatus(laneNumber: Int) extends Bundle {

  /** the current instruction in this MSHR. */
  val instructionIndex: UInt = UInt(3.W)

  /** indicate this MSHR is idle. */
  val idle: Bool = Bool()

  /** the MSHR finished the current offset group,
    * need to notify Scheduler for next index group.
    */
  val offsetGroupEnd: Bool = Bool()

  /** the current lane that this MSHR is accessing. */
  val targetLane: UInt = UInt(laneNumber.W)

  /** wait for the fault for fault-only-first instruction. */
  val waitFirstResponse: Bool = Bool()

  /** indicate this is the last cycle for a MSHR */
  val last: Bool = Bool()

  val changeMaskGroup: Bool = Bool()
}

class MSHRStage0Bundle(param: MSHRParam) extends Bundle {
  // 读的相关
  val readVS: UInt = UInt(param.regNumBits.W)
  // 访问寄存器的 offset, 代表第几个32bit
  val offsetForVSInLane: UInt = UInt(param.vrfOffsetBits.W)

  // 由于 stride 需要乘, 其他的类型也有相应的 offset, 所以我们先把 offset 算出来
  val addressOffset: UInt = UInt(param.paWidth.W)
  val segmentIndex:  UInt = UInt(3.W)
  val offsetForLane: UInt = UInt(log2Ceil(param.laneNumber).W)

  // 在一个组内的offset
  val indexInGroup: UInt = UInt(param.maxOffsetPerLaneAccessBits.W)
}

class MSHRStage1Bundle(param: MSHRParam) extends Bundle {
  val indexInMaskGroup: UInt = UInt(param.maxOffsetPerLaneAccessBits.W)
  val segmentIndex:     UInt = UInt(3.W)

  // 访问l2的地址
  val address: UInt = UInt(param.paWidth.W)
  val dataForCacheLine: UInt = UInt((param.cacheLineSize * 8).W)
  val maskForCacheLine: UInt = UInt(param.cacheLineSize.W)
  val cacheIndex = UInt(log2Ceil(param.vLen / param.cacheLineSize).W)
}

/** Miss Status Handler Register
  * this is used to record the outstanding memory access request for each instruction.
  * it contains 3 stages for tl.a:
  * - s0: access lane for the offset of the memory address
  * - s1: send VRF read request; calculate memory address based on s0 result
  * - s2: send tilelink memory request.
  *
  * tl.d is handled independently.
  */
class MSHR(param: MSHRParam) extends Module {

  /** [[LSURequest]] from LSU
    * see [[LSU.request]]
    */
  val lsuRequest: ValidIO[LSURequest] = IO(Flipped(Valid(new LSURequest(param.datapathWidth))))

  /** read channel to [[V]], which will redirect it to [[Lane.vrf]].
    * see [[LSU.vrfReadDataPorts]]
    */
  val vrfReadDataPorts: DecoupledIO[VRFReadRequest] = IO(
    Decoupled(new VRFReadRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits))
  )

  /** hard wire form Top.
    * see [[LSU.vrfReadResults]]
    */
  val vrfReadResults: UInt = IO(Input(UInt(param.datapathWidth.W)))

  /** offset of indexed load/store instructions. */
  val offsetReadResult: Vec[ValidIO[UInt]] = IO(Vec(param.laneNumber, Flipped(Valid(UInt(param.datapathWidth.W)))))

  /** mask from [[V]]
    * see [[LSU.maskInput]]
    */
  val maskInput: UInt = IO(Input(UInt(param.maskGroupWidth.W)))

  /** the address of the mask group in the [[V]].
    * see [[LSU.maskSelect]]
    */
  val maskSelect: ValidIO[UInt] = IO(Valid(UInt(param.maskGroupSizeBits.W)))

  /** TileLink Port which will be route to the [[LSU.tlPort]]. */
  val tlPort: TLBundle = IO(param.tlParam.bundle())

  /** write channel to [[V]], which will redirect it to [[Lane.vrf]].
    * see [[LSU.vrfWritePort]]
    */
  val vrfWritePort: DecoupledIO[VRFWriteRequest] = IO(
    Decoupled(
      new VRFWriteRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits, param.datapathWidth)
    )
  )

  /** the CSR interface from [[V]], latch them here.
    * TODO: merge to [[LSURequest]]
    */
  val csrInterface: CSRInterface = IO(Input(new CSRInterface(param.vlMaxBits)))

  /** notify [[LSU]] the status of [[MSHR]] */
  val status: MSHRStatus = IO(Output(new MSHRStatus(param.laneNumber)))

  val s0Fire: Bool = Wire(Bool())
  val s1Fire: Bool = Wire(Bool())
  val s2Fire: Bool = Wire(Bool())
  val tlPortAFire: Bool = Wire(Bool())

  /** request from LSU. */
  val lsuRequestReg: LSURequest = RegEnable(lsuRequest.bits, 0.U.asTypeOf(lsuRequest.bits), lsuRequest.valid)

  /** Always merge into cache line */
  val alwaysMerge: Bool = RegEnable(
    (lsuRequest.bits.instructionInformation.mop ## lsuRequest.bits.instructionInformation.lumop) === 0.U,
    false.B,
    lsuRequest.valid
  )

  val mergeStore = alwaysMerge && lsuRequestReg.instructionInformation.isStore
  val mergeLoad = alwaysMerge && !lsuRequestReg.instructionInformation.isStore

  val nFiled: UInt = lsuRequest.bits.instructionInformation.nf +& 1.U
  val nFiledReg: UInt = RegEnable(nFiled, 0.U, lsuRequest.valid)

  /** latch CSR.
    * TODO: merge to [[lsuRequestReg]]
    */
  val csrInterfaceReg: CSRInterface = RegEnable(csrInterface, 0.U.asTypeOf(csrInterface), lsuRequest.valid)

  /** load whole VRF register.
    * See [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#79-vector-loadstore-whole-register-instructions]]
    * TODO: RegEnable(requestIsWholeRegisterLoadStore)
    */
  val isWholeRegisterLoadStore: Bool = lsuRequestReg.instructionInformation.mop === 0.U &&
    lsuRequestReg.instructionInformation.lumop === 8.U

  /** indicate the current instruction is a segment load store. */
  val isSegmentLoadStore: Bool = lsuRequestReg.instructionInformation.nf.orR && !isWholeRegisterLoadStore

  /** indicate the current instruction is a load/store to mask.
    * TODO: RegEnable(requestIsMaskLoadStore)
    */
  val isMaskLoadStore: Bool =
    lsuRequestReg.instructionInformation.mop === 0.U && lsuRequestReg.instructionInformation.lumop(0)

  /** indicate the current instruction use mask to load/store. */
  val isMaskedLoadStore: Bool = lsuRequestReg.instructionInformation.maskedLoadStore

  /** indicate the current instruction is an indexed load/store(unordered/ordered). */
  val isIndexedLoadStore: Bool = lsuRequestReg.instructionInformation.mop(0)

  /** indicate the current request from Scheduler is a segment load store.
    * This is used to calculate the next cycle of EEW.
    */
  val requestIsWholeRegisterLoadStore: Bool = lsuRequest.bits.instructionInformation.mop === 0.U &&
    lsuRequest.bits.instructionInformation.lumop === 8.U

  /** indicate the current request from Scheduler is a load/store to mask.
    * This is used to calculate the next cycle of EEW.
    */
  val requestIsMaskLoadStore: Bool = lsuRequest.bits.instructionInformation.mop === 0.U &&
    lsuRequest.bits.instructionInformation.lumop(0)

  /** EEW of current request.
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#73-vector-loadstore-width-encoding]]
    * Table 11. Width encoding for vector loads and stores.
    * for indexed load store.
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
  /** for segment load/store, the data width to access for a group of element in the memory in byte.
    * TODO: MuxOH(requestEEW, (reqNF +& 1.U))
    */
  val dataWidthForSegmentLoadStore: UInt = RegEnable(
    nFiled * (1.U << requestEEW).asUInt(2, 0),
    0.U,
    lsuRequest.valid
  )

  /** expand EEW from [[requestEEW]]
    * TODO: dedup with [[dataWidthForSegmentLoadStore]]
    */
  val elementByteWidth: UInt = RegEnable((1.U << requestEEW).asUInt(2, 0), 0.U, lsuRequest.valid)

  /** expand SEW from [[csrInterface]] */
  val sew1HReg: UInt = RegEnable(UIntToOH(csrInterface.vSew)(2, 0), 0.U, lsuRequest.valid)

  /** for segment instructions, the interval between VRF index accessing.
    * e.g. vs0, vs2, vs4 ...
    * for lmul less than 1, the interval will be fixed to 1(ignore the frac lmul)
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
      // TODO: reset to 0.U
      1.U,
      lsuRequest.valid
    )

  /** a vector of bit indicate which memory transactions in D channel ack from memory bus. */
  val outstandingTLDMessages: UInt = RegInit(0.U(param.maxOffsetPerLaneAccess.W))

  /** There is no outstanding transactions in this MSHR. */
  val noOutstandingMessages: Bool = outstandingTLDMessages === 0.U

  /** the storeage of a group of offset for indexed instructions.
    *
    * @note this group is the offset group.
    */
  val indexedInstructionOffsets: Vec[ValidIO[UInt]] = RegInit(
    VecInit(Seq.fill(param.laneNumber)(0.U.asTypeOf(Valid(UInt(param.datapathWidth.W)))))
  )

  /** enable signal to update the offset group. */
  val updateOffsetGroupEnable: Bool = WireDefault(false.B)

  /** the current index of the offset group. */
  val groupIndex: UInt = RegInit(0.U(param.maxOffsetGroupSizeBits.W))

  /** used for update [[groupIndex]].
    * todo: vstart
    */
  val nextGroupIndex: UInt = Mux(lsuRequest.valid, 0.U, groupIndex + 1.U)
  when(updateOffsetGroupEnable) {
    groupIndex := nextGroupIndex
  }

  // TODO: remove me.
  val indexOfIndexedInstructionOffsetsNext: UInt = Wire(UInt(2.W))

  /** the current index in offset group for [[indexedInstructionOffsets]]
    * TODO: remove `val indexOfIndexedInstructionOffsetsNext: UInt = Wire(UInt(2.W))`
    */
  val indexOfIndexedInstructionOffsets: UInt =
    RegEnable(indexOfIndexedInstructionOffsetsNext, lsuRequest.valid || offsetReadResult.head.valid)
  indexOfIndexedInstructionOffsetsNext := Mux(lsuRequest.valid, 3.U(2.W), indexOfIndexedInstructionOffsets + 1.U)

  /** record the used [[indexedInstructionOffsets]] for sending memory transactions. */
  val usedIndexedInstructionOffsets: Vec[Bool] = Wire(Vec(param.laneNumber, Bool()))

  indexedInstructionOffsets.zipWithIndex.foreach {
    case (offset, index) =>
      // offsetReadResult(index).valid: new offset came
      // (offset.valid && !usedIndexedInstructionOffsets(index)): old unused offset
      offset.valid := offsetReadResult(index).valid || (offset.valid && !usedIndexedInstructionOffsets(index))
      // select from new and old.
      offset.bits := Mux(offsetReadResult(index).valid, offsetReadResult(index).bits, offset.bits)
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

  // TODO: why [[!isSegmentLoadStore]]? alias segmentEnd
  val lastElementForSegment = !isSegmentLoadStore || segmentEnd

  /** signal indicates this is the last transaction for the element(with handshake) */
  val segmentEndWithHandshake: Bool = s0Fire && lastElementForSegment

  // states for [[state]]
  val idle :: sRequest :: wResponse :: Nil = Enum(3)

  /** MSHR state machine
    * idle: the MSHR is in the idle state
    * [[sRequest]]：require all data are ready for sending to s0
    * [[wResponse]]: data has been send to s0, the MSHR is waiting for response on TileLink D channel.
    *
    * TODO: add performance monitor on the FSM.
    */
  val state: UInt = RegInit(idle)

  // select next element.
  /** a vector of bit indicate which memory transactions in A channel are enqueued to TileLink A channel s0.
    * if a transaction is enqueued to s0, the corresponding bit will be assert.
    */
  val sentMemoryRequests: UInt = RegInit(0.U(param.maxOffsetPerLaneAccess.W))

  /** unsent memory transactions to s0. */
  val unsentMemoryRequests: UInt = (~sentMemoryRequests).asUInt

  /** mask [[unsentMemoryRequests]]
    * TODO: maskFilter = maskReg & unsentMemoryRequests
    */
  val maskedUnsentMemoryRequests: UInt = Wire(UInt(param.maskGroupWidth.W))
  maskedUnsentMemoryRequests := maskReg & unsentMemoryRequests

  /** the find the next [[maskedUnsentMemoryRequests]] */
  val findFirstMaskedUnsentMemoryRequests: UInt = ffo(maskedUnsentMemoryRequests)

  /** the find the next [[unsentMemoryRequests]] */
  val findFirstUnsentMemoryRequestsNext: UInt = (sentMemoryRequests ## true.B) & unsentMemoryRequests

  /** the next element used for memory request.
    * TODO: find first one after Mux?
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

  /** mask need to be updated in the next cycle, this is the signal to update mask.
    * this signal only apply to mask type instruction.
    */
  val maskGroupEndAndRequestNewMask: Bool = (
    noMoreMaskedUnsentMemoryRequests ||
      (nextElementForMemoryRequest(param.maskGroupWidth - 1) && segmentEndWithHandshake)
  ) && isMaskedLoadStore

  /** the end of mask group.
    * TODO: duplicate with [[maskGroupEndAndRequestNewMask]]
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

  /** EEW for offset instructions.
    * offset EEW is always from instruction.
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

  /** global(instruction level) offset to [[indexedInstructionOffsets]]
    * [[nextElementForMemoryRequestIndex]] is the current index of memory request.
    * use [[offsetEEW]] to multiply this index
    * TODO: use Mux1H here
    */
  val globalOffsetOfIndexedInstructionOffsets: UInt =
    (nextElementForMemoryRequestIndex << offsetEEW).asUInt(nextElementForMemoryRequestIndex.getWidth + 1, 0)

  /** MSB of [[globalOffsetOfIndexedInstructionOffsets]], indicate the offset group of current memory request. */
  val offsetGroupIndexOfMemoryRequest: UInt =
    globalOffsetOfIndexedInstructionOffsets(
      nextElementForMemoryRequestIndex.getWidth + 1,
      nextElementForMemoryRequestIndex.getWidth
    )

  /** LSB of [[globalOffsetOfIndexedInstructionOffsets]],
    * used for extract the current offset need to be used from [[indexedInstructionOffsets]]
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
      FillInterleaved(8, sew1HReg(2) ## sew1HReg(2) ## !sew1HReg(0) ## true.B)

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

  /**
    * for the case that EEW!=0, offset group maybe misaligned with the mask group
    * - eew = 0, offset group is aligned with mask group
    * - eew = 1, one mask group corresponds 2 offset group, [[offsetGroupIndexOfMemoryRequest(0)]] need to match [[indexOfIndexedInstructionOffsets(0)]]
    * - eew = 2, one mask group corresponds 4 offset group, [[offsetGroupIndexOfMemoryRequest]] need to match [[indexOfIndexedInstructionOffsets]]
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
  val laneOfOffsetOfOffsetGroup: UInt = UIntToOH(
    offsetOfOffsetGroup(
      log2Ceil(param.datapathWidth / 8) + log2Ceil(param.laneNumber) - 1,
      log2Ceil(param.datapathWidth / 8)
    )
  )

  /** one of [[indexedInstructionOffsets]] is exhausted. */
  val indexedInstructionOffsetExhausted: Bool =
    Mux1H(
      offsetEEWOH,
      Seq(
        // EEW = 8
        offsetOfOffsetGroup(1, 0).andR,
        // EEW = 16
        offsetOfOffsetGroup(1),
        // EEW = 32
        true.B
      )
    )

  /**
    * 各个类型的换组的标志:
    * 3. 如果是index类型的,那么在index耗尽的时候需要更换index,只有在index的粒度8的时候index和mask才同时耗尽.
    * 4. 如果index与mask不匹配,index在mask中的偏移由[[indexOfIndexedInstructionOffsets]]记录.
    * 5. unit stride 和 stride 类型的在没有mask的前提下 [[nextElementForMemoryRequest]] 最高位拉高才换组.
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
      status.offsetGroupEnd ||
      // change mask group
      // TODO: remove [[maskNeedUpdate]]?
      maskGroupEndAndRequestNewMask
  }

  /** onehot version of LSB of `tlPort.a.bits.source` */
  val memoryRequestSourceOH: UInt = UIntToOH(tlPort.a.bits.source(log2Ceil(param.maxOffsetPerLaneAccess) - 1, 0))

  /** detect the case segment load store hazard. */
  val sourceFree: Bool = !(memoryRequestSourceOH & outstandingTLDMessages).orR

  /** current state machine is on [[sRequest]]. */
  val stateIsRequest: Bool = state === sRequest

  /** next element of instruction for memory request. */
  val nextElementIndex: UInt =
    // for [[wResponse]] being able to address, don't update [[groupIndex]] for now,
    // choose [[nextGroupIndex]], since [[sRequest]] has already send all memory requests in the [[groupIndex]]
    Mux(stateIsRequest, groupIndex, nextGroupIndex) ##
      nextElementForMemoryRequestIndex

  /** evl for [[isWholeRegisterLoadStore]] instruction type.
    * we use the maximum [[dataEEW]] to handle it.
    */
  val wholeEvl: UInt =
    nFiledReg ## 0.U(log2Ceil(param.vLen / param.datapathWidth).W)

  /** evl for the instruction */
  val evl: UInt = Mux(
    isWholeRegisterLoadStore,
    wholeEvl,
    Mux(
      isMaskLoadStore,
      // TODO: bug: ceil + csrInterfaceReg.vl(2,0).orR
      csrInterfaceReg.vl(param.vlMaxBits - 1, 3),
      csrInterfaceReg.vl
    )
  )

  /** signal indicate that the offset group for all lanes are valid. */
  val allOffsetValid: Bool = VecInit(indexedInstructionOffsets.map(_.valid)).asUInt.andR

  /** signal used for aligning offset groups. */
  val offsetGroupsAlign: Bool = RegInit(false.B)
  // to fix the bug that after the first group being used, the second group is not valid,
  // MSHR will change group by mistake.
  // TODO: need perf the case, if there are too much "misalignment", use a state vector for each lane(bit) in the group.
  when(!offsetGroupsAlign && allOffsetValid) {
    offsetGroupsAlign := true.B
  }.elsewhen(status.offsetGroupEnd) {
    offsetGroupsAlign := false.B
  }

  /** the current element is the last element to execute in the pipeline. */
  val last: Bool = nextElementIndex >= evl

  /** no need mask, there still exist unsent masked requests, don't need to update mask. */
  val maskCheck: Bool = !isMaskedLoadStore || !noMoreMaskedUnsentMemoryRequests

  /** no need index, when use a index, check it is valid or not. */
  val indexCheck: Bool = !isIndexedLoadStore || (offsetValidCheck && offsetGroupCheck && offsetGroupsAlign)

  // handle fault only first
  /** the current TileLink message in A Channel is the first transaction in this instruction. */
  val firstMemoryRequestOfInstruction: Bool = RegEnable(lsuRequest.valid, false.B, lsuRequest.valid || tlPort.a.fire)

  /** if assert, need to wait for memory response, this is used for fault only first instruction. */
  val waitFirstMemoryResponseForFaultOnlyFirst: Bool =
    RegEnable(
      lsuRequest.valid && lsuRequest.bits.instructionInformation.fof,
      false.B,
      lsuRequest.valid || tlPort.d.fire
    )

  /** signal to check the the first memory request is responded for fault only first instruction. */
  val fofCheck: Bool = firstMemoryRequestOfInstruction || !waitFirstMemoryResponseForFaultOnlyFirst

  /** all check is ready, being able to send request to pipeline. */
  val stateReady: Bool = stateIsRequest && maskCheck && indexCheck && fofCheck

  /** only need to request offset when changing offset group,
    * don't send request for the first offset group for each instruction.
    */
  val needRequestOffset: Bool =
    RegEnable(offsetReadResult.head.valid, false.B, offsetReadResult.head.valid || lsuRequest.valid)

  /** signal to request offset in the pipeline, only assert for one cycle. */
  val requestOffset: Bool = stateIsRequest && maskCheck && !indexCheck && fofCheck

  /** latch [[requestOffset]] */
  val requestOffsetNext: Bool = RegNext(requestOffset)

  // ask Scheduler to change offset group
  status.offsetGroupEnd := needRequestOffset && requestOffset && !requestOffsetNext

  /** valid signal to enqueue to s0. */
  val s0EnqueueValid: Bool = stateReady && !last

  /** there exist valid signal inside s0. */
  val s0Valid: Bool = RegEnable(s0Fire, false.B, s0Fire ^ vrfReadDataPorts.fire)

  /** request enqueue to s0. */
  val s0Wire: MSHRStage0Bundle = Wire(new MSHRStage0Bundle(param))

  /** request inside s0. */
  val s0Reg: MSHRStage0Bundle = RegEnable(s0Wire, 0.U.asTypeOf(s0Wire), s0Fire)

  /** ready signal to enqueue to s1. */
  val s1EnqueueReady: Bool = Wire(Bool())

  /** element index enqueuing to s0. */
  val s0ElementIndex: UInt = groupIndex ## nextElementForMemoryRequestIndex

  /** which byte to access in VRF, e.g.
    * VLEN=1024,datapath=32,laneNumber=8
    * XXXXXXXXXX   <- 10 bits for element(32bits) index
    *           XX <- 2 bits for SEW
    *   XXXXXXXXXX <- strip MSB for the constraint that sew*vlmax <= 8*VLEN <- [[storeBaseByteOffset]]
    *           XX <- offset for datapath
    *        XXX   <- offset for lane
    *      XX      <- offset for vs in lane(one vector register is split to 4 lines in each lane)
    *   XXX        <- offset for vector register
    */
  val storeBaseByteOffset: UInt = (s0ElementIndex << dataEEW).asUInt(log2Ceil(param.vLen) - 1, 0)

  // s0 calculate register offset
  s0Wire.readVS :=
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
  s0Wire.offsetForVSInLane := storeBaseByteOffset(
    // VLEN=1024,datapath=32,laneNumber=8 -> 6
    log2Ceil(param.datapathWidth / 8) + log2Ceil(param.laneNumber) + param.vrfOffsetBits - 1,
    // VLEN=1024,datapath=32,laneNumber=8 -> 5
    log2Ceil(param.datapathWidth / 8) + log2Ceil(param.laneNumber)
  )
  s0Wire.addressOffset := baseOffsetForElement + (elementByteWidth * segmentIndex)
  s0Wire.indexInGroup := nextElementForMemoryRequestIndex
  s0Wire.segmentIndex := segmentIndex
  s0Wire.offsetForLane := storeBaseByteOffset(
    // datapath=32,laneNumber=8 -> 4
    log2Ceil(param.datapathWidth / 8) + log2Ceil(param.laneNumber) - 1,
    // datapath=32 -> 2
    log2Ceil(param.datapathWidth / 8)
  )

  // merge unit for unit stride start -----
  /** Which cache line to start at */
  val startCacheLine: UInt = lsuRequestReg.rs1Data(param.paWidth - 1, param.cacheLineBits)

//    /** How many byte will be accessed by this instruction */
//    val bytePerInstruction = ((nFiled * csrInterface.vl) << lsuRequest.bits.instructionInformation.eew).asUInt
//
//
//    /** How many cache lines will be accessed by this instruction
//      * nFiled * vl * (2 ** eew) / 32
//      * TODO: 暂时只有unit stride
//      */
//    val cacheLineNumber: UInt = bytePerInstruction(param.cacheLineIndexBits - 1, param.cacheLineBits) +
//      bytePerInstruction(param.cacheLineBits - 1, 0).orR
//
//    val cacheLineNumberReg: UInt = RegEnable(cacheLineNumber, 0.U, lsuRequest.valid)

  val mergeUnitDequeueFire: Bool = Wire(Bool())

  // cache line index
  val cacheLineIndex = RegInit(0.U(param.cacheLineIndexBits.W))

  // update cacheLineIndex
  when(lsuRequest.valid || mergeUnitDequeueFire) {
    cacheLineIndex := Mux(lsuRequest.valid, 0.U, cacheLineIndex + 1.U)
  }

  //
  val dataShifter: UInt = RegInit(0.U((param.cacheLineSize * 8 + param.datapathWidth).W))
  val maskShifter: UInt = RegInit(0.U((param.cacheLineSize + (param.datapathWidth / 8)).W))
  // 这个cache line 已经有多少个byte了
  val cacheByteCounter: UInt = RegInit(0.U(param.cacheLineBits.W))

  val readFire: Bool = vrfReadDataPorts.fire
  val readFireNext: Bool = RegNext(readFire, false.B)
  val nextPtr: UInt = Wire(UInt(param.cacheLineBits.W))
  val updateData: UInt = Wire(UInt((param.cacheLineSize * 8 + param.datapathWidth).W))
  val updateMask: UInt = Wire(UInt((param.cacheLineSize + (param.datapathWidth / 8)).W))

  // update cacheByteCounter
  when(lsuRequest.valid || readFireNext) {
    cacheByteCounter :=
      Mux(readFireNext, nextPtr, lsuRequest.bits.rs1Data(param.cacheLineBits - 1, 0))
    dataShifter := Mux(readFireNext, updateData, 0.U)
    maskShifter := Mux(readFireNext, updateMask, 0.U)
    // todo: need update mask
  }

  // todo: add group index
  // (10 bit * 3bit) + 3bit
  val relativeAddress: UInt = (s0Reg.indexInGroup * nFiledReg) + s0Reg.segmentIndex
  // 保留上一次的相对偏移来算地址差值
  val previousRelativeAddress: UInt = RegEnable(Mux(readFire, relativeAddress, 0.U), 0.U, readFire || lsuRequest.valid)

  // 这一次的数据与上一次数据的差值
  val addressDifference: UInt = ((relativeAddress - previousRelativeAddress) << dataEEW).asUInt
  // 需要处理一次跳多个 cache line 的情况, 在 32 * 32 的情况下一次最多会跳过3个cache line(32 * 32 是 4 个)
  val addressDifferenceReg: UInt = RegEnable(addressDifference, 0.U, readFire)

  val dataOffsetInDataPath: UInt = RegEnable(Mux1H(dataEEWOH(1, 0), Seq(
    s0Reg.indexInGroup(1, 0),
    s0Reg.indexInGroup(0) ## false.B,
  )), 0.U, readFire)
  // 32 - cacheByteCounter
  val cacheByteCounterComplementaryCode: UInt = (~cacheByteCounter).asUInt + 1.U

  val vrfReadResultsAligned: UInt = Mux1H(
    UIntToOH(dataOffsetInDataPath),
    Seq.tabulate(4) {baseOffset => (vrfReadResults >> (baseOffset * 8)).asUInt}
  )
  // addressDifferenceReg 是当前正读的数据与前一个数据的起始地址的差, 所以是先移后拼
  // todo: byte 为粒度的移动需要优化
  updateData :=
    vrfReadResultsAligned ## (dataShifter >> (addressDifferenceReg ## 0.U(3.W)))(param.cacheLineSize * 8 - 1, 0)

  val readMask: UInt = dataEEWOH(2) ## dataEEWOH(2) ## !dataEEWOH(0) ## true.B
  updateMask := readMask ## (maskShifter >> addressDifferenceReg)(param.cacheLineSize - 1, 0)

  // sew = 8 的时候地址关于 byte 对齐
  // sew = 16的时候地址需要关于 2byte对齐
  // sew = 32的时候地址需要关于 4byte对齐
  // element 不会跨cache line
  // 但是我们可能会在 sew = 8 的时候将访问vrf的请求合并,
  // 所以我们在跨 cache line 的时候既要把数据压进去, 又要扣出一个 cache line 出来
  // updateData: 在压数据
  // cacheLineUpdate: 在把 cache line 扣出来
  // todo: 同样需要优化
  val cacheLineToNextStage: UInt =
    (dataShifter >> (cacheByteCounterComplementaryCode ## 0.U(3.W)))(param.cacheLineSize * 8 - 1, 0)

  val maskToNextStage: UInt = (maskShifter >> cacheByteCounterComplementaryCode)(param.cacheLineSize - 1, 0)

  val nextCacheByteCounter: UInt = cacheByteCounter +& addressDifferenceReg

  val CrossCacheLine: Bool = (nextCacheByteCounter >> param.cacheLineBits).asUInt.orR
  nextPtr := nextCacheByteCounter(param.cacheLineBits - 1, 0)

  // todo: 暂时认为下一级是 ready 的, 反压数据后边再处理吧, 思路是放一个缓存 data 的queue 只有 ready 的时候才能 s1 ready
  val cacheLineValid: Bool = Mux(readFireNext, CrossCacheLine, !readFire && last)

  mergeUnitDequeueFire := cacheLineValid// && ready

  val mergeUnitValid: Bool = RegEnable(!cacheLineValid, false.B, readFire || cacheLineValid)
  // cacheLineValid & cacheLineUpdate 作为这一级的输出
  // todo: 维护 cache line 的valid处理最后一个cache line,
  //       assert(cacheByteCounterComplementaryCode === lsuRequest.bits.rs1Data(param.cacheLineBits - 1, 0))

  // merge unit for unit stride end -----

  // s1 access VRF
  // TODO: perf `lsuRequestReg.instructionInformation.isStore && vrfReadDataPorts.ready` to check the VRF bandwidth
  //       limitation affecting to LSU store.
  vrfReadDataPorts.valid := s0Valid && lsuRequestReg.instructionInformation.isStore && s1EnqueueReady
  vrfReadDataPorts.bits.offset := s0Reg.offsetForVSInLane
  vrfReadDataPorts.bits.vs := s0Reg.readVS
  vrfReadDataPorts.bits.instructionIndex := lsuRequestReg.instructionIndex

  /** ready to read VRF to store to memory. */
  val readReady: Bool = !lsuRequestReg.instructionInformation.isStore || vrfReadDataPorts.ready

  /** valid signal to enqueue to s1 */
  val s1EnqueueValid: Bool = Mux(alwaysMerge, cacheLineValid, s0Valid && readReady)

  /** data is valid in s1 */
  val s1Valid: Bool = RegEnable(s1Fire, false.B, s1Fire ^ s2Fire)

  /** request enqueue to s1. */
  val s1Wire: MSHRStage1Bundle = Wire(new MSHRStage1Bundle(param))

  /** request inside s1. */
  val s1Reg: MSHRStage1Bundle = RegEnable(s1Wire, 0.U.asTypeOf(s1Wire), s1Fire)

  /** Which location of the cache line is being sent */
  val sendCounter: UInt = RegInit(0.U(param.bustCountBits.W))

  // update sendCounter
  when((s1Fire || tlPortAFire) && alwaysMerge && lsuRequestReg.instructionInformation.isStore) {
    sendCounter := Mux(s1Fire, 0.U, sendCounter + 1.U)
  }

  /** group data by [[param.datapathWidth]] */
  val sendDataGroup: Seq[UInt] = Seq.tabulate(param.bustCount) { bustIndex =>
    s1Reg.dataForCacheLine((bustIndex + 1) * param.datapathWidth - 1, bustIndex * param.datapathWidth)
  }

  /** Select the data to be sent from the cache line */
  val mergedSendData: UInt = Mux1H(UIntToOH(sendCounter), sendDataGroup)

  val sendMaskGroup: Seq[UInt] = Seq.tabulate(param.bustCount) { bustIndex =>
    s1Reg.maskForCacheLine((bustIndex + 1) * param.datapathWidth / 8 - 1, bustIndex * param.datapathWidth / 8)
  }

  /** Select the mask to be sent from the cache line */
  val mergedSendMask: UInt = Mux1H(UIntToOH(sendCounter), sendMaskGroup)

  /** -1: eg: 32 byte / 4 byte = 8 bust -> count <- [0,7]
    * last bust for cache line(if merge)
    * */
  val lastBust: Bool = sendCounter === ((param.cacheLineSize * 8 / param.datapathWidth) - 1).U

  val lastBustForS1: Bool = !mergeStore || lastBust

  /** select [[s2Fire]] */
  s2Fire := tlPortAFire && lastBustForS1

  /** ready to enqueue to s2. */
  val s2EnqueueReady: Bool = tlPort.a.ready && sourceFree && lastBustForS1

  s1EnqueueReady := s2EnqueueReady || !s1Valid
  s1Fire := s1EnqueueValid && s1EnqueueReady

  /** ready signal to enqueue to s0. */
  val s0EnqueueReady: Bool = (s1EnqueueReady && readReady) || !s0Valid
  s0Fire := s0EnqueueReady && s0EnqueueValid

  /** pipeline is flushed. */
  val pipelineClear: Bool = !s0Valid && !s1Valid

  val normalAddress: UInt = lsuRequestReg.rs1Data + s0Reg.addressOffset
  val mergeAddress: UInt = ((lsuRequestReg.rs1Data >> param.cacheLineBits).asUInt + cacheLineIndex) ##
    0.U(param.cacheLineBits.W)
  s1Wire.address :=  Mux(alwaysMerge, mergeAddress, normalAddress)
  s1Wire.indexInMaskGroup := s0Reg.indexInGroup
  s1Wire.segmentIndex := s0Reg.segmentIndex
  s1Wire.dataForCacheLine := cacheLineToNextStage
  s1Wire.maskForCacheLine := maskToNextStage
  s1Wire.cacheIndex := cacheLineIndex

  /** previous cycle sent the VRF read request,
    * this cycle should got response from each lanes
    * TODO: I think the latency is too large here.
    */
  val readVRFResponseValid: Bool = RegNext(s1Fire) && lsuRequestReg.instructionInformation.isStore
  // readResult hold unless readNext
  /** latch from lanes [[vrfReadResults]] */
  val vrfReadResultsReg: UInt = RegEnable(vrfReadResults, 0.U.asTypeOf(vrfReadResults), readVRFResponseValid)

  /** is [[vrfReadResultsReg]] valid or not?
    * TODO: I think this is bad for timing...
    */
  val readDataRegValid: Bool =
    RegEnable(readVRFResponseValid, false.B, (readVRFResponseValid ^ tlPort.a.fire) || lsuRequest.valid)

  /** mux to select from [[vrfReadResultsReg]] or [[vrfReadResults]] */
  val readDataResultSelect: UInt = Mux(readDataRegValid, vrfReadResultsReg, vrfReadResults)

  /** compute the mask for store transaction. */
  val storeMask: UInt = Mux1H(
    dataEEWOH(2, 0),
    Seq(
      UIntToOH(s1Reg.address(1, 0)),
      s1Reg.address(1) ## s1Reg.address(1) ## !s1Reg.address(1) ## !s1Reg.address(1),
      15.U(4.W)
    )
  )

  /** offset caused by element index(byte level) in the datapath. */
  val storeOffsetByIndex: UInt = (s1Reg.indexInMaskGroup(1, 0) << dataEEW).asUInt(1, 0)

  /** align the VRF index in datapath and memory.
    * TODO: use Mux1H to select(only 4 cases).
    */
  val storeData: UInt =
    ((readDataResultSelect << (s1Reg.address(1, 0) ## 0.U(3.W))) >> (storeOffsetByIndex ## 0.U(3.W))).asUInt
  // only PutFull / Get for now
  tlPort.a.bits.opcode := !lsuRequestReg.instructionInformation.isStore ## 0.U(2.W)
  tlPort.a.bits.param := 0.U
  // todo: 1/2 1/4 1/8 cache line
  tlPort.a.bits.size := Mux(alwaysMerge, param.cacheLineBits.U, dataEEW)

  /** source for memory request
    * make volatile field LSB to reduce the source conflict.
    */
  val memoryRequestSource: UInt = Mux(
    isSegmentLoadStore,
    s1Reg.indexInMaskGroup ## s1Reg.segmentIndex,
    s1Reg.indexInMaskGroup
  )
  // offset index + segment index
  // log(32)-> 5    log(8) -> 3
  tlPort.a.bits.source := Mux(alwaysMerge, s1Reg.cacheIndex, memoryRequestSource)
  tlPort.a.bits.address := s1Reg.address
  tlPort.a.bits.mask := Mux(alwaysMerge, mergedSendMask, storeMask)
  tlPort.a.bits.data := Mux(alwaysMerge, mergedSendData, storeData)
  tlPort.a.bits.corrupt := false.B
  tlPort.a.valid := s1Valid && sourceFree
  tlPortAFire := tlPort.a.fire

  // Handle response

  /** extract `indexInMaskGroup` from response. */
  val indexInMaskGroupResponse: UInt = Mux(
    isSegmentLoadStore,
    (tlPort.d.bits.source >> 3).asUInt,
    tlPort.d.bits.source
  )(param.maxOffsetPerLaneAccessBits - 1, 0)

  /** the LSB(maskGroupWidth) for response, MSHR only maintains maskGroupWidth of request. */
  val responseSourceLSBOH: UInt = UIntToOH(tlPort.d.bits.source(log2Ceil(param.maxOffsetPerLaneAccess) - 1, 0))

  /** which byte to access in VRF for load instruction.
    * see [[storeBaseByteOffset]]
    */
  val loadBaseByteOffset: UInt = ((groupIndex ## indexInMaskGroupResponse) << dataEEW).asUInt(9, 0)
  vrfWritePort.valid := tlPort.d.valid
  tlPort.d.ready := vrfWritePort.ready

  // TODO: handle alignment for VRF and memory
  vrfWritePort.bits.data := (tlPort.d.bits.data << (loadBaseByteOffset(1, 0) ## 0.U(3.W))).asUInt
  vrfWritePort.bits.last := last
  vrfWritePort.bits.instructionIndex := lsuRequestReg.instructionIndex
  vrfWritePort.bits.mask := Mux1H(
    dataEEWOH(2, 0),
    Seq(
      UIntToOH(loadBaseByteOffset(1, 0)),
      loadBaseByteOffset(1) ## loadBaseByteOffset(1) ## !loadBaseByteOffset(1) ## !loadBaseByteOffset(1),
      15.U(4.W)
    )
  )
  // calculate vd register offset
  vrfWritePort.bits.vd :=
    // base vd in the instruction
    lsuRequestReg.instructionInformation.vs3 +
      // vd offset for segment instructions
      Mux(
        isSegmentLoadStore,
        tlPort.d.bits.source(2, 0) * segmentInstructionIndexInterval,
        0.U
      ) +
      // vd offset for element index
      loadBaseByteOffset(
        // VLEN=1024 -> 9
        log2Ceil(param.vLen) - 1,
        // VLEN=1024,datapath=32,laneNumber=8 -> 7
        log2Ceil(param.datapathWidth / 8) + log2Ceil(param.laneNumber) + param.vrfOffsetBits
      )
  vrfWritePort.bits.offset := loadBaseByteOffset(
    // VLEN=1024,datapath=32,laneNumber=8 -> 6
    log2Ceil(param.datapathWidth / 8) + log2Ceil(param.laneNumber) + param.vrfOffsetBits - 1,
    // VLEN=1024,datapath=32,laneNumber=8 -> 5
    log2Ceil(param.datapathWidth / 8) + log2Ceil(param.laneNumber)
  )

  // update [[outstandingTLDMessages]]
  when((tlPort.d.fire || tlPort.a.fire) && !lsuRequestReg.instructionInformation.isStore) {
    // 同时进出得让相应被拉高
    outstandingTLDMessages := (outstandingTLDMessages &
      // free outstanding source since got response from memory.
      ~Mux(
        tlPort.d.fire,
        responseSourceLSBOH,
        0.U(param.maxOffsetPerLaneAccess.W)
      ): UInt) |
      // allocate outstanding source since corresponding memory request has been issued.
      Mux(
        tlPort.a.fire,
        memoryRequestSourceOH,
        0.U(param.maxOffsetPerLaneAccess.W)
      )
  }

  // update state
  when(state === sRequest) {
    when((last || maskGroupEnd) && !(mergeUnitValid || s1Valid)) {
      state := wResponse
    }
  }
  // if all responses are cleared
  when(
    state === wResponse && noOutstandingMessages && pipelineClear
    // TODO: cosim bug for multiple response for same request!!!
      && !tlPort.d.valid
  ) {
    // switch state to idle
    when(last) {
      state := idle
    }
      // change mask group
      .otherwise {
        state := sRequest
        updateOffsetGroupEnable := true.B
      }
  }
  // handle corner case for vl=0
  val invalidInstruction:     Bool = csrInterface.vl === 0.U && !requestIsWholeRegisterLoadStore && lsuRequest.valid
  val invalidInstructionNext: Bool = RegNext(invalidInstruction)

  // change state to request
  when(lsuRequest.valid && !invalidInstruction) {
    state := sRequest
    updateOffsetGroupEnable := true.B
  }

  // expose state for this MSHR
  status.instructionIndex := lsuRequestReg.instructionIndex

  /** the current state is idle. */
  val stateIdle = state === idle
  status.idle := stateIdle
  status.last := (!RegNext(stateIdle) && stateIdle) || invalidInstructionNext
  status.changeMaskGroup := updateOffsetGroupEnable
  // which lane to access
  status.targetLane := UIntToOH(
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
  status.waitFirstResponse := waitFirstMemoryResponseForFaultOnlyFirst
  // which mask to request
  maskSelect.bits := nextGroupIndex
}
