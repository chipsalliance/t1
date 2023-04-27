package v

import chisel3._
import chisel3.util._
import tilelink.{TLBundle, TLBundleParameter, TLChannelA, TLChannelAParameter, TLChannelDParameter}

/**
  * @param datapathWidth ELEN
  * @param chainingSize how many instructions can be chained
  * @param vLen VLEN
  * @param laneNumber how many lanes in the vector processor
  * @param paWidth physical address width
  *
  * @note
  * MSHR group:
  *   The memory access of a single instruction will be grouped into MSHR group.
  *   Because indexed load/store need to access VRF to get the offset of memory address to access,
  *   we use the maximum count of transactions of indexed load/store in each memory access operation for each lanes
  *   to calculate the size of MSHR.
  *   The MSHR group maintains a group of transactions with a set of base address.
  */
case class MSHRParam(
  chainingSize:  Int,
  datapathWidth: Int,
  vLen:          Int,
  laneNumber:    Int,
  paWidth:       Int,
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
}

class MSHRStatus(laneNumber: Int) extends Bundle {

  /** the current instruction in this MSHR. */
  val instructionIndex: UInt = UInt(3.W)

  /** indicate this MSHR is idle. */
  val idle: Bool = Bool()

  /** the MSHR finished the current index group,
    * need to notify Scheduler for next index group.
    */
  val indexGroupEnd: Bool = Bool()

  /** the current lane that this MSHR is accessing. */
  val targetLane: UInt = UInt(laneNumber.W)

  /** wait for the fault for fault-only-first instruction. */
  val waitFirstResponse: Bool = Bool()

  /** indicate this is the last cycle for a MSHR */
  val last: Bool = Bool()
}

class MSHRStage0Bundle(param: MSHRParam) extends Bundle {
  // 读的相关
  val readVS: UInt = UInt(param.regNumBits.W)
  // 访问寄存器的 offset, 代表第几个32bit
  val readOffset: UInt = UInt(param.vrfOffsetBits.W)

  // 由于 stride 需要乘, 其他的类型也有相应的 offset, 所以我们先把 offset 算出来
  val offset:          UInt = UInt(param.paWidth.W)
  val segmentIndex:    UInt = UInt(3.W)
  val targetLaneIndex: UInt = UInt(log2Ceil(param.laneNumber).W)

  // 在一个组内的offset
  val indexInGroup: UInt = UInt(param.maxOffsetPerLaneAccessBits.W)
}

class MSHRStage1Bundle(param: MSHRParam) extends Bundle {
  // 在一个组内的offset
  val indexInGroup: UInt = UInt(param.maxOffsetPerLaneAccessBits.W)
  val segmentIndex: UInt = UInt(3.W)

  // 访问l2的地址
  val address: UInt = UInt(param.paWidth.W)
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

  /** request from LSU. */
  val lsuRequestReg: LSURequest = RegEnable(lsuRequest.bits, 0.U.asTypeOf(lsuRequest.bits), lsuRequest.valid)

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
    (requestNF +& 1.U) * (1.U << requestEEW).asUInt(2, 0),
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

  /** a vector of bit indicate which memory transactions in A channel are enqueued to TileLink A channel s0.
    * if a transaction is enqueued to s0, the corresponding bit will be assert.
    */
  val tlAMessagesSent: UInt = RegInit(0.U(param.maxOffsetPerLaneAccess.W))

  /** a vector of bit indicate which memory transactions in D channel ack from memory bus. */
  val outstandingTLDMessages: UInt = RegInit(0.U(param.maxOffsetPerLaneAccess.W))

  /** There is no outstanding transactions in this MSHR. */
  val noOutstandingMessages: Bool = outstandingTLDMessages === 0.U

  /** the storeage of a group of offset for indexed instructions.
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
  when(updateOffsetGroupEnable) { groupIndex := nextGroupIndex }

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

  // START FROM HERE.

  // fof的反压寄存器
  val firstReq: Bool = RegEnable(lsuRequest.valid, false.B, lsuRequest.valid || tlPort.a.fire)
  val waitFirstResp: Bool =
    RegEnable(
      lsuRequest.valid && lsuRequest.bits.instructionInformation.fof,
      false.B,
      lsuRequest.valid || tlPort.d.fire
    )

  // 缓存 mask
  val maskReg: UInt = RegEnable(maskInput, 0.U, maskSelect.fire || lsuRequest.valid)

  // segment 的维护
  val segmentIndex: UInt = RegInit(0.U(3.W))
  val segIndexNext: UInt = segmentIndex + 1.U
  val segEnd:       Bool = segmentIndex === lsuRequestReg.instructionInformation.nf
  // 更新 segMask
  when((isSegmentLoadStore && s0Fire) || lsuRequest.valid) {
    segmentIndex := Mux(segEnd || lsuRequest.valid, 0.U, segIndexNext)
  }
  val lastElementForSeg = !isSegmentLoadStore || segEnd
  val segExhausted: Bool = s0Fire && lastElementForSeg

  /** 状态机
    * idle: mshr 处于闲置状态
    * sRequest：出与试图往s0发数据的状态,前提是所有需要的源数据都准备就绪了
    * wResp: 这一组往s0发的已经结束了,但是还需要维护相关信息等到d通道回应的时候寻址vrf
    *
    * TODO: add performance monitor on the FSM.
    */
  val idle :: sRequest :: wResp :: Nil = Enum(3)
  val state: UInt = RegInit(idle)

  // 选出 reqNext
  val reqFilter:   UInt = (~tlAMessagesSent).asUInt
  val maskFilter:  UInt = Wire(UInt(param.maskGroupWidth.W))
  val maskNext:    UInt = ffo(maskFilter)
  val reqDoneNext: UInt = (tlAMessagesSent ## true.B) & reqFilter
  maskFilter := maskReg & reqFilter
  // 下一个请求在组内的 OH
  val reqNext:      UInt = Mux(isMaskedLoadStore, maskNext, reqDoneNext)(param.maskGroupWidth - 1, 0)
  val reqNextIndex: UInt = OHToUInt(reqNext)(param.maxOffsetPerLaneAccessBits - 1, 0)
  // 更新 reqDone
  when(segExhausted || updateOffsetGroupEnable) {
    tlAMessagesSent := Mux(updateOffsetGroupEnable, 0.U, scanRightOr(reqNext))
  }

  // 额外添加的mask类型对数据的粒度有限制
  val dataEEW: UInt =
    Mux(
      isIndexedLoadStore,
      csrInterfaceReg.vSew,
      Mux(isMaskLoadStore, 0.U, Mux(isWholeRegisterLoadStore, 2.U, lsuRequestReg.instructionInformation.eew))
    )
  val dataEEWOH: UInt = UIntToOH(dataEEW)

  // 用到了最后一个或这一组的全被mask了
  val maskExhausted = maskFilter === 0.U
  // 耗尽的不用等seg,最后一个用完了的要等
  val maskNeedUpdate: Bool = (maskExhausted || (reqNext(param.maskGroupWidth - 1) && segExhausted)) && isMaskedLoadStore
  maskSelect.valid := maskNeedUpdate

  /** 处理 offset
    * 每次每一条lane读会读出一组32bit的offset,汇集在这边就会有32 * lane的长度
    * 由与index在eew != 0的时候与mask(reqNext)不是一组对一组, [[indexOffset]] 记录了对应关系
    * 高位代表相mask被分的组数,低位代表组内偏移:
    *   eew = 0 的时候,index的element与mask是一一对应的
    *   eew = 1 的时候,一组mask对应两组index,此时[[indexOffset(reqNextIndex.getWidth)]]需要与[[indexOfIndexedInstructionOffsets(0)]]匹配
    *   eew = 2 时,一组mask对应四组index，[[indexOffset]]高两位需要与[[indexOfIndexedInstructionOffsets]]匹配
    * [[indexOffsetByteIndex]]表示这一次使用的index位于[[indexedInstructionOffsets]]中的起始byte
    * [[indexOffsetNext]] 是通过 [[indexedInstructionOffsets]] 移位得到我们想要的 index
    */
  val offsetEEW:              UInt = lsuRequestReg.instructionInformation.eew
  val indexOffset:            UInt = (reqNextIndex << offsetEEW).asUInt(reqNextIndex.getWidth + 1, 0)
  val indexOffsetTargetGroup: UInt = indexOffset(reqNextIndex.getWidth + 1, reqNextIndex.getWidth)
  val indexOffsetByteIndex:   UInt = indexOffset(reqNextIndex.getWidth - 1, 0)
  val indexOffsetNext: UInt =
    (VecInit(indexedInstructionOffsets.map(_.bits)).asUInt >> (indexOffsetByteIndex ## 0.U(3.W)))
      .asUInt(param.datapathWidth - 1, 0) &
      FillInterleaved(8, sew1HReg(2) ## sew1HReg(2) ## !sew1HReg(0) ## true.B)
  val offsetValidCheck: Bool =
    (VecInit(indexedInstructionOffsets.map(_.valid)).asUInt >> (indexOffsetByteIndex >> 2).asUInt).asUInt(0)
  val groupMatch:       UInt = indexOffsetTargetGroup ^ indexOfIndexedInstructionOffsets
  val offsetGroupCheck: Bool = (!offsetEEW(0) || !groupMatch(0)) && (!offsetEEW(1) || groupMatch === 0.U)
  val unitOffsetNext:   UInt = groupIndex ## reqNextIndex
  val strideOffsetNext: UInt = (groupIndex ## reqNextIndex) * lsuRequestReg.rs2Data
  val reqOffset = Mux(
    isIndexedLoadStore,
    indexOffsetNext,
    Mux(lsuRequestReg.instructionInformation.mop(1), strideOffsetNext, unitOffsetNext * dataWidthForSegmentLoadStore)
  )

  { // 拉回 indexOffset valid
    val indexLaneMask: UInt = UIntToOH(indexOffsetByteIndex(4, 2))
    val indexExhausted: Bool =
      Mux1H(UIntToOH(offsetEEW)(2, 0), Seq(indexOffsetByteIndex(1, 0).andR, indexOffsetByteIndex(1), true.B))

    /**
      * 各个类型的换组的标志:
      * 1. 如果是seg的类型,那么需要执行完完整的一组才算是结算的时间点:[[segEnd]]
      * 2. 如果是mask类型的,在被mask筛选之后没有剩余请求了,将直接换组: [[maskNeedUpdate]].
      * 3. 如果是index类型的,那么在index耗尽的时候需要更换index,只有在index的粒度8的时候index和mask才同时耗尽.
      * 4. 如果index与mask不匹配,index在mask中的偏移由[[indexOfIndexedInstructionOffsets]]记录.
      * 5. unit stride 和 stride 类型的在没有mask的前提下 [[reqNext]] 最高位拉高才换组.
      */
    val nextIndexReq: Bool = isIndexedLoadStore && indexLaneMask(7) && indexExhausted
    // 既然试图请求新的index,那说明前一组全用完了
    Seq.tabulate(param.laneNumber) { i =>
      usedIndexedInstructionOffsets(i) := (segExhausted && indexLaneMask(
        i
      ) && indexExhausted) || status.indexGroupEnd || maskNeedUpdate
    }
  }
  val groupEnd: Bool = maskNeedUpdate || (reqNext(param.maskGroupWidth - 1) && s0Fire && lastElementForSeg)

  // 处理 tile link source id 的冲突
  val reqSource1H: UInt = UIntToOH(tlPort.a.bits.source(4, 0))
  val sourceFree:  Bool = !(reqSource1H & outstandingTLDMessages).orR

  // s0 stall 判断
  val stateCheck: Bool = state === sRequest
  // 如果状态是wResp,为了让回应能寻址会暂时不更新groupIndex，但是属于groupIndex的请求已经发完了
  val elementID: UInt = Mux(stateCheck, groupIndex, nextGroupIndex) ## reqNextIndex
  // whole 类型的我们以最大粒度地执行,所以一个寄存器有(vlen/32 -> maskGroupSize)
  val wholeEvl: UInt = (lsuRequestReg.instructionInformation.nf +& 1.U) ## 0.U(param.maskGroupSizeBits.W)
  // TODO: bug: ceil + csrInterfaceReg.vl(2,0).orR
  val evl: UInt =
    Mux(
      isWholeRegisterLoadStore,
      wholeEvl,
      Mux(isMaskLoadStore, csrInterfaceReg.vl(param.vlMaxBits - 1, 3), csrInterfaceReg.vl)
    )
  val allIndexReady: Bool = VecInit(indexedInstructionOffsets.map(_.valid)).asUInt.andR
  val indexAlign:    Bool = RegInit(false.B)
  // 这里处理在第一组index用完了但是第二组还没被lane发过来,然后mshr认为index应该换组的问题
  when(!indexAlign && allIndexReady) {
    indexAlign := true.B
  }.elsewhen(status.indexGroupEnd) {
    indexAlign := false.B
  }
  // todo: evl: unit stride -> whole register load | mask load, EEW=8
  val last:       Bool = elementID >= evl
  val maskCheck:  Bool = !isMaskedLoadStore || !maskExhausted
  val indexCheck: Bool = !isIndexedLoadStore || (offsetValidCheck && offsetGroupCheck && indexAlign)
  val fofCheck:   Bool = firstReq || !waitFirstResp
  val stateReady: Bool = stateCheck && maskCheck && indexCheck && fofCheck
  // 刚来请求的时候不需要请求 index
  val needReqIndex: Bool =
    RegEnable(offsetReadResult.head.valid, false.B, offsetReadResult.head.valid || lsuRequest.valid)
  //  只能给脉冲
  val indexReqValid:     Bool = stateCheck && maskCheck && !indexCheck && fofCheck
  val indexReqValidNext: Bool = RegNext(indexReqValid)
  status.indexGroupEnd := indexReqValid && needReqIndex && !indexReqValidNext

  val s0EnqueueValid: Bool = stateReady && !last
  val s0Valid:        Bool = RegEnable(s0Fire, false.B, s0Fire ^ s1Fire)
  val s0Wire:         MSHRStage0Bundle = Wire(new MSHRStage0Bundle(param))
  val s0Reg:          MSHRStage0Bundle = RegEnable(s0Wire, 0.U.asTypeOf(s0Wire), s0Fire)
  val s1SlotReady:    Bool = Wire(Bool())

  /** 读我们直接用 [[reqNextIndex]]
    * 然后我们左移 eew,得到修改的起始是第几个byte: [[baseByteOffset]]
    * XXX XXXXXXXX XX
    *     XXX: vd的增量
    *     |      |: 在一个寄存器中位于哪一个32bit
    *              XX：起始位置在32bit中的偏移
    *          XXX： 由于寄存器的实体按32bit为粒度分散在lane中,所以这是目标lane的index
    *        XX： 这里代表[[vrfWritePort.bits.offset]]
    */
  val s0ElementIndex: UInt = groupIndex ## reqNextIndex
  val baseByteOffset: UInt = (s0ElementIndex << dataEEW).asUInt(9, 0)

  // todo: seg * lMul
  s0Wire.readVS := lsuRequestReg.instructionInformation.vs3 + Mux(
    isSegmentLoadStore,
    segmentIndex * segmentInstructionIndexInterval,
    0.U
  ) + baseByteOffset(9, 7)
  s0Wire.readOffset := baseByteOffset(6, 5)
  s0Wire.offset := reqOffset + (elementByteWidth * segmentIndex)
  s0Wire.indexInGroup := reqNextIndex
  s0Wire.segmentIndex := segmentIndex
  s0Wire.targetLaneIndex := baseByteOffset(4, 2)

  // s1
  // s1 read vrf
  vrfReadDataPorts.valid := s0Valid && lsuRequestReg.instructionInformation.isStore && s1SlotReady
  vrfReadDataPorts.bits.offset := s0Reg.readOffset
  vrfReadDataPorts.bits.vs := s0Reg.readVS
  vrfReadDataPorts.bits.instructionIndex := lsuRequestReg.instructionIndex

  val readReady:      Bool = !lsuRequestReg.instructionInformation.isStore || vrfReadDataPorts.ready
  val s1EnqueueValid: Bool = s0Valid && readReady
  val s1Valid:        Bool = RegEnable(s1Fire, false.B, s1Fire ^ s2Fire)
  val s1Wire:         MSHRStage1Bundle = Wire(new MSHRStage1Bundle(param))
  val s1Reg:          MSHRStage1Bundle = RegEnable(s1Wire, 0.U.asTypeOf(s1Wire), s1Fire)
  val s1DequeueReady: Bool = tlPort.a.ready && sourceFree
  s1SlotReady := s1DequeueReady || !s1Valid
  s1Fire := s1EnqueueValid && s1SlotReady
  // s0DeqReady === s1EnqReady
  val s0EnqueueReady: Bool = (s1SlotReady && readReady) || !s0Valid
  s0Fire := s0EnqueueReady && s0EnqueueValid
  val slotClear: Bool = !(s0Valid || s1Valid)

  s1Wire.address := lsuRequestReg.rs1Data + s0Reg.offset
  s1Wire.indexInGroup := s0Reg.indexInGroup
  s1Wire.segmentIndex := s0Reg.segmentIndex

  // 处理数据的缓存
  val readNext: Bool = RegNext(s1Fire) && lsuRequestReg.instructionInformation.isStore
  // readResult hold unless readNext
  val dataReg:          UInt = RegEnable(vrfReadResults, 0.U.asTypeOf(vrfReadResults), readNext)
  val readDataRegValid: Bool = RegEnable(readNext, false.B, (readNext ^ tlPort.a.fire) || lsuRequest.valid)

  // 计算mask: todo: 比较一下这两mask,然后替换掉indexMask
  val addressMask: UInt = Mux1H(
    dataEEWOH(2, 0),
    Seq(
      UIntToOH(s1Reg.address(1, 0)),
      s1Reg.address(1) ## s1Reg.address(1) ## !s1Reg.address(1) ## !s1Reg.address(1),
      15.U(4.W)
    )
  )
  // s2: tl.a
  val indexBase: UInt = (s1Reg.indexInGroup(1, 0) << dataEEW).asUInt(1, 0)
  // 从vrf里面读出来的数据,会带有index导致的偏移
  val readDataResultSelect: UInt = Mux(readDataRegValid, dataReg, vrfReadResults)
  // 由于segment的存在,数据在vrf里的偏移和在mem里的偏移是不一样的 todo: 变成mux1H
  val putData: UInt =
    ((readDataResultSelect << (s1Reg.address(1, 0) ## 0.U(3.W))) >> (indexBase(1, 0) ## 0.U(3.W))).asUInt
  tlPort.a.bits.opcode := !lsuRequestReg.instructionInformation.isStore ## 0.U(2.W)
  tlPort.a.bits.param := 0.U
  tlPort.a.bits.size := dataEEW

  /** offset index + segment index
    * log(32)-> 5    log(8) -> 3
    */
  tlPort.a.bits.source := Mux(isSegmentLoadStore, s1Reg.indexInGroup ## s1Reg.segmentIndex, s1Reg.indexInGroup)
  tlPort.a.bits.address := s1Reg.address
  tlPort.a.bits.mask := addressMask
  tlPort.a.bits.data := putData
  tlPort.a.bits.corrupt := false.B
  tlPort.a.valid := s1Valid && sourceFree
  s2Fire := tlPort.a.fire

  // 处理回应
  val respIndex: UInt = Mux(
    isSegmentLoadStore,
    (tlPort.d.bits.source >> 3).asUInt,
    tlPort.d.bits.source
  )(param.maxOffsetPerLaneAccessBits - 1, 0)
  // d 通道回应的信息
  val baseByteOffsetD: UInt = ((groupIndex ## respIndex) << dataEEW).asUInt(9, 0)
  vrfWritePort.valid := tlPort.d.valid
  tlPort.d.ready := vrfWritePort.ready
  vrfWritePort.bits.data := (tlPort.d.bits.data << (baseByteOffsetD(1, 0) ## 0.U(3.W))).asUInt
  vrfWritePort.bits.last := last
  vrfWritePort.bits.instructionIndex := lsuRequestReg.instructionIndex
  vrfWritePort.bits.mask := Mux1H(
    dataEEWOH(2, 0),
    Seq(
      UIntToOH(baseByteOffsetD(1, 0)),
      baseByteOffsetD(1) ## baseByteOffsetD(1) ## !baseByteOffsetD(1) ## !baseByteOffsetD(1),
      15.U(4.W)
    )
  )
  vrfWritePort.bits.vd := lsuRequestReg.instructionInformation.vs3 +
    Mux(isSegmentLoadStore, tlPort.d.bits.source(2, 0) * segmentInstructionIndexInterval, 0.U) + baseByteOffsetD(9, 7)
  vrfWritePort.bits.offset := baseByteOffsetD(6, 5)

  val respSourceOH:     UInt = UIntToOH(tlPort.d.bits.source(4, 0))
  val sourceUpdate:     UInt = Mux(tlPort.a.fire, reqSource1H, 0.U(param.maxOffsetPerLaneAccess.W))
  val respSourceUpdate: UInt = Mux(tlPort.d.fire, respSourceOH, 0.U(param.maxOffsetPerLaneAccess.W))
  // 更新 respDone
  when((tlPort.d.fire || tlPort.a.fire) && !lsuRequestReg.instructionInformation.isStore) {
    // 同时进出得让相应被拉高
    outstandingTLDMessages := (outstandingTLDMessages & (~respSourceUpdate).asUInt) | sourceUpdate
  }

  // state 更新
  when(state === sRequest) {
    when(last || groupEnd) {
      state := wResp
    }
  }
  // todo: cosim 有时会在resp done的时候继续发曾经回过的回应,但是newGroup会导致vrf寻址错误,先 && !tlPort.d.valid 绕一下
  when(state === wResp && noOutstandingMessages && slotClear && !tlPort.d.valid) {
    when(last) {
      state := idle
    }.otherwise {
      state := sRequest
      updateOffsetGroupEnable := true.B
    }
  }
  val invalidInstruction: Bool = csrInterface.vl === 0.U && lsuRequest.valid && !requestIsWholeRegisterLoadStore
  when(lsuRequest.valid && !invalidInstruction) {
    state := sRequest
    updateOffsetGroupEnable := true.B
  }
  val invalidInstructionNext: Bool = RegNext(invalidInstruction)
  val stateIdle = state === idle
  status.instructionIndex := lsuRequestReg.instructionIndex
  status.idle := stateIdle
  status.last := (!RegNext(stateIdle) && stateIdle) || invalidInstructionNext
  status.targetLane := UIntToOH(
    Mux(lsuRequestReg.instructionInformation.isStore, s0Reg.targetLaneIndex, baseByteOffsetD(4, 2))
  )
  status.waitFirstResponse := waitFirstResp
  // 需要更新的时候是需要下一组的mask
  maskSelect.bits := nextGroupIndex
}
