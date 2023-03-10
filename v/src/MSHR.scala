package v

import chisel3._
import chisel3.util._
import tilelink.{TLBundle, TLBundleParameter, TLChannelA, TLChannelAParameter, TLChannelDParameter}

case class MSHRParam(chainingSize: Int, ELEN: Int = 32, VLEN: Int = 1024, lane: Int = 8, paWidth: Int = 32) {
  val dataBits:      Int = log2Ceil(ELEN)
  val mshrSize:      Int = 3
  val VLMaxBits:     Int = log2Ceil(VLEN) + 1
  val laneGroupSize: Int = VLEN / lane
  // 一次完全的offset读会最多分成多少个offset
  val lsuGroupLength:   Int = ELEN * lane / 8
  val offsetCountSize:  Int = log2Ceil(lsuGroupLength)
  val lsuGroupSize:     Int = VLEN / ELEN
  val lsuGroupSizeBits: Int = log2Ceil(lsuGroupSize) + 1
  val sourceWidth = 8

  val maskGroupWidth:    Int = lsuGroupLength
  val maskGroupSize:     Int = VLEN / maskGroupWidth
  val maskGroupSizeBits: Int = log2Ceil(maskGroupSize)
  val tlParam: TLBundleParameter = TLBundleParameter(
    a = TLChannelAParameter(paWidth, sourceWidth, ELEN, 2, 4),
    b = None,
    c = None,
    d = TLChannelDParameter(sourceWidth, sourceWidth, ELEN, 2),
    e = None
  )
  val regNumBits:           Int = log2Ceil(32)
  val instructionIndexSize: Int = log2Ceil(chainingSize) + 1
  val singleGroupSize:      Int = VLEN / ELEN / lane
  val offsetBits:           Int = log2Ceil(singleGroupSize)
}

class MSHRStatus(lane: Int) extends Bundle {
  val instIndex:     UInt = UInt(3.W)
  val idle:          Bool = Bool()
  val indexGroupEnd: Bool = Bool()
  val targetLane:    UInt = UInt(lane.W)
  val waitFirstResp: Bool = Bool()
  val last:          Bool = Bool()
}

class MSHRStage0Bundle(param: MSHRParam) extends Bundle {
  // 读的相关
  val readVS: UInt = UInt(param.regNumBits.W)
  // 访问寄存器的 offset, 代表第几个32bit
  val readOffset: UInt = UInt(param.offsetBits.W)

  // 由于 stride 需要乘, 其他的类型也有相应的 offset, 所以我们先把 offset 算出来
  val offset: UInt = UInt(param.paWidth.W)
  val segmentIndex: UInt = UInt(3.W)
  val targetLaneIndex: UInt = UInt(log2Ceil(param.lane).W)

  // 在一个组内的offset
  val indexInGroup: UInt = UInt(param.offsetCountSize.W)
}

class MSHRStage1Bundle(param: MSHRParam) extends Bundle {
  // 在一个组内的offset
  val indexInGroup: UInt = UInt(param.offsetCountSize.W)
  val segmentIndex: UInt = UInt(3.W)

  // 访问l2的地址
  val address: UInt = UInt(param.paWidth.W)
}

/** 由于vrf变成了 SyncReadMem, 重构了mshr
  * 这次的mshr是走流水的方式,分为3级流水:
  * s0: 计算访问vrf的相关信息, 据算offset的信息
  * s1: 读vrf,并计算地址信息
  * s2: tl.a
  * 还有一个独立运行的 tl.d
  * */
class MSHR(param: MSHRParam) extends Module {
  val req: ValidIO[LSUReq] = IO(Flipped(Valid(new LSUReq(param.ELEN))))
  val readDataPort: DecoupledIO[VRFReadRequest] = IO(
    Decoupled(new VRFReadRequest(param.regNumBits, param.offsetBits, param.instructionIndexSize))
  )
  val readResult:       UInt = IO(Input(UInt(param.ELEN.W)))
  val offsetReadResult: Vec[ValidIO[UInt]] = IO(Vec(param.lane, Flipped(Valid(UInt(param.ELEN.W)))))
  val maskRegInput:     UInt = IO(Input(UInt(param.maskGroupWidth.W)))
  val maskSelect:       ValidIO[UInt] = IO(Valid(UInt(param.maskGroupSizeBits.W)))
  val tlPort:           TLBundle = IO(param.tlParam.bundle())
  val vrfWritePort: DecoupledIO[VRFWriteRequest] = IO(
    Decoupled(new VRFWriteRequest(param.regNumBits, param.offsetBits, param.instructionIndexSize, param.ELEN))
  )
  val csrInterface: LaneCsrInterface = IO(Input(new LaneCsrInterface(param.VLMaxBits)))
  val status:       MSHRStatus = IO(Output(new MSHRStatus(param.lane)))

  // 流水线的控制信号
  val s0Fire: Bool = Wire(Bool())
  val s1Fire: Bool = Wire(Bool())
  val s2Fire: Bool = Wire(Bool())

  // 进请求
  val requestReg:     LSUReq = RegEnable(req.bits, 0.U.asTypeOf(req.bits), req.valid)
  // 指令类型相关
  val segType: Bool = requestReg.instInf.nf.orR
  // unit stride 里面有额外的 mask 类型的ls
  val maskLayoutType: Bool = requestReg.instInf.mop === 0.U && requestReg.instInf.vs2(0)
  // 所有的mask类型
  val maskType: Bool = requestReg.instInf.mask
  val indexType: Bool = requestReg.instInf.mop(0)

  // 计算offset的太长,一个req才变一次的用reg存起来
  val reqExtendMask: Bool = req.bits.instInf.mop === 0.U && req.bits.instInf.vs2(0)
  val reqEEW: UInt = Mux(req.bits.instInf.mop(0), csrInterface.vSew, Mux(reqExtendMask, 0.U, req.bits.instInf.eew))
  val segAddressMul: UInt = RegEnable((req.bits.instInf.nf + 1.U) * (1.U << reqEEW).asUInt(2, 0), 0.U, req.valid)
  val elementByteWidth: UInt = RegEnable((1.U << reqEEW).asUInt(2, 0), 0.U, req.valid)

  // 开始与结束的组内偏移
  // 标志哪些做完了
  val reqDone: UInt = RegInit(0.U(param.lsuGroupLength.W))
  // 还没有d通道回应的请求
  val respDone: UInt = RegInit(0.U(param.lsuGroupLength.W))
  val respFinish: Bool = respDone === 0.U

  // 更新 groupIndex
  val groupIndex:     UInt = RegInit(0.U(param.lsuGroupSizeBits.W))
  val nextGroupIndex: UInt = Mux(req.valid, 0.U, groupIndex + 1.U) //todo: vstart
  val newGroup:     Bool = WireDefault(false.B)
  when(newGroup) {groupIndex := nextGroupIndex}

  // fof的反压寄存器
  val firstReq:       Bool = RegEnable(req.valid, false.B, req.valid || tlPort.a.fire)
  val waitFirstResp:  Bool = RegEnable(req.valid && req.bits.instInf.fof, false.B, req.valid || tlPort.d.fire)

  // offset的寄存器
  val indexOffsetVec: Vec[ValidIO[UInt]] = RegInit(
    VecInit(Seq.fill(param.lane)(0.U.asTypeOf(Valid(UInt(param.ELEN.W)))))
  )
  // 处理offset的寄存器
  val offsetGroupIndexNext: UInt = Wire(UInt(2.W))
  val offsetGroupIndex:     UInt = RegEnable(offsetGroupIndexNext, req.valid || offsetReadResult.head.valid)
  offsetGroupIndexNext := Mux(req.valid, 3.U(2.W), offsetGroupIndex + 1.U)
  val offsetUsed: Vec[Bool] = Wire(Vec(param.lane, Bool()))
  indexOffsetVec.zipWithIndex.foreach {
    case (offset, index) =>
      offset.valid := offsetReadResult(index).valid || (offset.valid && !offsetUsed(index))
      offset.bits := Mux(offsetReadResult(index).valid, offsetReadResult(index).bits, offset.bits)
  }

  // 缓存 mask
  val maskReg: UInt = RegEnable(maskRegInput, 0.U, maskSelect.fire || req.valid)

  // segment 的维护
  val segmentIndex: UInt = RegInit(0.U(3.W))
  val segIndexNext: UInt = segmentIndex + 1.U
  val segEnd: Bool = segmentIndex === requestReg.instInf.nf
  // 更新 segMask
  when((segType && s0Fire) || req.valid) {
    segmentIndex := Mux(segEnd || req.valid, 0.U, segIndexNext)
  }
  val lastElementForSeg = !segType || segEnd
  val segExhausted: Bool = s0Fire && lastElementForSeg

  /** 状态机
    * idle: mshr 处于闲置状态
    * sRequest：出与试图往s0发数据的状态,前提是所有需要的源数据都准备就绪了
    * wResp: 这一组往s0发的已经结束了,但是还需要维护相关信息等到d通道回应的时候寻址vrf
    * */
  val idle :: sRequest :: wResp :: Nil = Enum(3)
  val state: UInt = RegInit(idle)

  // 选出 reqNext
  val reqFilter: UInt = (~reqDone).asUInt
  val maskFilter: UInt = Wire(UInt(param.maskGroupWidth.W))
  val maskNext: UInt = ffo(maskFilter)
  val reqDoneNext: UInt = (reqDone ## true.B) & reqFilter
  maskFilter := maskReg & reqFilter
  // 下一个请求在组内的 OH
  val reqNext: UInt = Mux(maskType, maskNext, reqDoneNext)
  val reqNextIndex: UInt = OHToUInt(reqNext)(param.offsetCountSize - 1, 0)
  // 更新 reqDone
  when(segExhausted || newGroup) {
    reqDone := Mux(newGroup, 0.U, scanRightOr(reqNext) )
  }

  // 额外添加的mask类型对数据的粒度有限制
  val dataEEW:       UInt = Mux(indexType, csrInterface.vSew, Mux(maskLayoutType, 0.U, requestReg.instInf.eew))
  val dataEEWOH:     UInt = UIntToOH(dataEEW)

  // 用到了最后一个或这一组的全被mask了
  val maskExhausted = maskFilter === 0.U
  val maskNeedUpdate: Bool = (maskExhausted || reqNext(param.maskGroupWidth - 1)) && maskType
  maskSelect.valid := maskNeedUpdate

  /** 处理 offset
    * 每次每一条lane读会读出一组32bit的offset,汇集在这边就会有32 * lane的长度
    * 由与index在eew != 0的时候与mask(reqNext)不是一组对一组, [[indexOffset]] 记录了对应关系
    * 高位代表相mask被分的组数,低位代表组内偏移:
    *   eew = 0 的时候,index的element与mask是一一对应的
    *   eew = 1 的时候,一组mask对应两组index,此时[[indexOffset(reqNextIndex.getWidth)]]需要与[[offsetGroupIndex(0)]]匹配
    *   eew = 2 时,一组mask对应四组index，[[indexOffset]]高两位需要与[[offsetGroupIndex]]匹配
    * [[indexOffsetByteIndex]]表示这一次使用的index位于[[indexOffsetVec]]中的起始byte
    * [[indexOffsetNext]] 是通过 [[indexOffsetVec]] 移位得到我们想要的 index
    */
  val offsetEEW:              UInt = requestReg.instInf.eew
  val indexOffset:            UInt = (reqNextIndex << offsetEEW).asUInt(reqNextIndex.getWidth + 1, 0)
  val indexOffsetTargetGroup: UInt = indexOffset(reqNextIndex.getWidth + 1, reqNextIndex.getWidth)
  val indexOffsetByteIndex:   UInt = indexOffset(reqNextIndex.getWidth - 1, 0)
  val indexOffsetNext: UInt =
    (VecInit(indexOffsetVec.map(_.bits)).asUInt >> (indexOffsetByteIndex ## 0.U(3.W))).asUInt(param.ELEN - 1, 0)
  val offsetValidCheck: Bool =
    (VecInit(indexOffsetVec.map(_.valid)).asUInt >> (indexOffsetByteIndex >> 2).asUInt).asUInt(0)
  val groupMatch:       UInt = indexOffsetTargetGroup ^ offsetGroupIndex
  val offsetGroupCheck: Bool = (!offsetEEW(0) || !groupMatch(0)) && (!offsetEEW(1) || groupMatch === 0.U)
  val unitOffsetNext:   UInt = groupIndex ## reqNextIndex
  val strideOffsetNext: UInt = (groupIndex ## reqNextIndex) * requestReg.rs2Data
  val reqOffset = Mux(
    indexType,
    indexOffsetNext,
    Mux(requestReg.instInf.mop(1), strideOffsetNext, unitOffsetNext * segAddressMul)
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
      * 4. 如果index与mask不匹配,index在mask中的偏移由[[offsetGroupIndex]]记录.
      * 5. unit stride 和 stride 类型的在没有mask的前提下 [[reqNext]] 最高位拉高才换组.
      */
    val nextIndexReq: Bool = indexType && indexLaneMask(7) && indexExhausted
    // index 类型的需要在最后一份index被耗尽的时候通知lane读下一组index过来.
    status.indexGroupEnd := nextIndexReq && tlPort.a.fire && indexOffsetVec.last.valid
    Seq.tabulate(param.lane) { i => offsetUsed(i) := segExhausted && indexLaneMask(i) && indexExhausted }
  }
  val groupEnd: Bool = maskNeedUpdate || (reqNext(param.maskGroupWidth - 1) && tlPort.a.fire)

  // 处理 tile link source id 的冲突
  val reqSource1H: UInt = UIntToOH(tlPort.a.bits.source(4, 0))
  val sourceFree: Bool = !(reqSource1H & respDone).orR

  // s0 stall 判断
  val stateCheck: Bool = state === sRequest
  // 如果状态是wResp,为了让回应能寻址会暂时不更新groupIndex，但是属于groupIndex的请求已经发完了
  val elementID: UInt = Mux(stateCheck, groupIndex, nextGroupIndex) ## reqNextIndex
  val evl: UInt = Mux(maskLayoutType, csrInterface.vl(param.VLMaxBits - 1, 3), csrInterface.vl)
  // todo: evl: unit stride -> whole register load | mask load, EEW=8
  val last: Bool = (elementID >= evl) && segmentIndex === 0.U
  val maskCheck:  Bool = !maskType || !maskExhausted
  val indexCheck: Bool = !indexType || (offsetValidCheck && offsetGroupCheck)
  val fofCheck:   Bool = firstReq || !waitFirstResp
  val stateReady: Bool = stateCheck && maskCheck && indexCheck && fofCheck && sourceFree

  val s0EnqueueValid: Bool = stateReady && !last
  val s0Valid: Bool = RegEnable(s0Fire, false.B, s0Fire ^ s1Fire)
  val s0Wire: MSHRStage0Bundle = Wire(new MSHRStage0Bundle(param))
  val s0Reg: MSHRStage0Bundle = RegEnable(s0Wire, 0.U.asTypeOf(s0Wire), s0Fire)
  val s1SlotReady: Bool = Wire(Bool())

  /** 读我们直接用 [[reqNextIndex]]
    * 然后我们左移 eew,得到修改的起始是第几个byte: [[baseByteOffset]]
    * XXX XXXXXXXX XX
    *     XXX: vd的增量
    *     |      |: 在一个寄存器中位于哪一个32bit
    *              XX：起始位置在32bit中的偏移
    *          XXX： 由于寄存器的实体按32bit为粒度分散在lane中,所以这是目标lane的index
    *        XX： 这里代表[[vrfWritePort.bits.offset]]
    */
  val s0ElementIndex:   UInt = groupIndex ## reqNextIndex
  val baseByteOffset: UInt = (s0ElementIndex << dataEEW).asUInt(9, 0)

  // todo: seg * lMul
  s0Wire.readVS := requestReg.instInf.vs3 + Mux(segType, segmentIndex, baseByteOffset(9, 7))
  s0Wire.readOffset := baseByteOffset(6, 5)
  s0Wire.offset := reqOffset + (elementByteWidth * segmentIndex)
  s0Wire.indexInGroup := reqNextIndex
  s0Wire.segmentIndex := segmentIndex
  s0Wire.targetLaneIndex := baseByteOffset(4, 2)

  // s1
  // s1 read vrf
  readDataPort.valid := s0Valid && requestReg.instInf.st && s1SlotReady
  readDataPort.bits.offset := s0Reg.readOffset
  readDataPort.bits.vs := s0Reg.readVS
  readDataPort.bits.instructionIndex := requestReg.instIndex

  val readReady: Bool = !requestReg.instInf.st || readDataPort.ready
  val s1EnqueueValid: Bool = s0Valid && readReady
  val s1Valid: Bool = RegEnable(s1Fire, false.B, s1Fire ^ s2Fire)
  val s1Wire: MSHRStage1Bundle = Wire(new MSHRStage1Bundle(param))
  val s1Reg: MSHRStage1Bundle = RegEnable(s1Wire, 0.U.asTypeOf(s1Wire), s1Fire)
  val s1DequeueReady: Bool = tlPort.a.ready
  s1SlotReady := s1DequeueReady || !s1Valid
  s1Fire := s1EnqueueValid && s1SlotReady
  // s0DeqReady === s1EnqReady
  val s0EnqueueReady: Bool = (s1SlotReady && readReady) || !s0Valid
  s0Fire := s0EnqueueReady && s0EnqueueValid
  val slotClear: Bool = !(s0Valid || s1Valid)

  s1Wire.address := requestReg.rs1Data + s0Reg.offset
  s1Wire.indexInGroup := s0Reg.indexInGroup
  s1Wire.segmentIndex := s0Reg.segmentIndex

  // 处理数据的缓存
  val readNext: Bool = RegNext(s1Fire) && requestReg.instInf.st
  // readResult hold unless readNext
  val dataReg: UInt = RegEnable(readResult, 0.U.asTypeOf(readResult), readNext)
  val readDataRegValid: Bool = RegEnable(readNext, false.B, (readNext ^ tlPort.a.fire) || req.valid)

  {
    // 计算mask: todo: 比较一下这两mask,然后替换掉indexMask
    val addressMask: UInt = Mux1H(
      dataEEWOH(2, 0),
      Seq(
        UIntToOH(s1Reg.address(1, 0)),
        s1Reg.address(1) ## s1Reg.address(1) ## !s1Reg.address(1) ## !s1Reg.address(1),
        15.U(4.W)
      )
    )
    dontTouch(addressMask)
  }
  // s2: tl.a
  val indexBase: UInt = (s1Reg.indexInGroup(1, 0) << dataEEW).asUInt(1, 0)
  val indexMask: UInt = Mux1H(
    dataEEWOH(2, 0),
    Seq(
      UIntToOH(indexBase),
      indexBase(1) ## indexBase(1) ## !indexBase(1) ## !indexBase(1),
      15.U(4.W)
    )
  )
  tlPort.a.bits.opcode := !requestReg.instInf.st ## 0.U(2.W)
  tlPort.a.bits.param := 0.U
  tlPort.a.bits.size := dataEEW
  /** offset index + segment index
    * log(32)-> 5    log(8) -> 3
    */
  tlPort.a.bits.source := Mux(segType, s1Reg.indexInGroup ## s1Reg.segmentIndex, s1Reg.indexInGroup)
  tlPort.a.bits.address := s1Reg.address
  tlPort.a.bits.mask := indexMask
  tlPort.a.bits.data := Mux(readDataRegValid, dataReg, readResult)
  tlPort.a.bits.corrupt := false.B
  tlPort.a.valid := s1Valid
  s2Fire := tlPort.a.fire


  // 处理回应
  val respIndex: UInt = Mux(
    segType,
    (tlPort.d.bits.source >> 3).asUInt,
    tlPort.d.bits.source
  )(param.offsetCountSize - 1, 0)
  // d 通道回应的信息
  val baseByteOffsetD: UInt = ((groupIndex ## respIndex) << dataEEW).asUInt(9, 0)
  vrfWritePort.valid := tlPort.d.valid
  tlPort.d.ready := vrfWritePort.ready
  vrfWritePort.bits.data := (tlPort.d.bits.data << (baseByteOffsetD(1, 0) ## 0.U(3.W))).asUInt
  vrfWritePort.bits.last := last
  vrfWritePort.bits.instructionIndex := requestReg.instIndex
  vrfWritePort.bits.mask := Mux1H(
    dataEEWOH(2, 0),
    Seq(
      UIntToOH(baseByteOffsetD(1, 0)),
      baseByteOffsetD(1) ## baseByteOffsetD(1) ## !baseByteOffsetD(1) ## !baseByteOffsetD(1),
      15.U(4.W)
    )
  )
  vrfWritePort.bits.vd := requestReg.instInf.vs3 +
    Mux(segType, tlPort.d.bits.source(2, 0), baseByteOffsetD(9, 7))
  vrfWritePort.bits.offset := baseByteOffsetD(6, 5)

  val respSourceOH: UInt = UIntToOH(tlPort.d.bits.source(4, 0))
  val lastResp: Bool = last && tlPort.d.fire && (respDone & (~respSourceOH).asUInt) === 0.U && slotClear
  val sourceUpdate:     UInt = Mux(tlPort.a.fire, reqSource1H, 0.U(param.lsuGroupLength.W))
  val respSourceUpdate: UInt = Mux(tlPort.d.fire, respSourceOH, 0.U(param.lsuGroupLength.W))
  // 更新 respDone
  when((tlPort.d.fire || tlPort.a.fire) && !requestReg.instInf.st) {
    respDone := (respDone | sourceUpdate) & (~respSourceUpdate).asUInt
  }

  // state 更新
  when(state === sRequest) {
    when(last) {
      when(respFinish) {
        state := idle
      }.otherwise {
        state := wResp
      }
    }.elsewhen(groupEnd) {
      when(respFinish) {
        newGroup := true.B
      }.otherwise {
        state := wResp
      }
    }
  }

  when(state === wResp && respFinish) {
    when(last) {
      state := idle
    }.otherwise {
      state := sRequest
      newGroup := true.B
    }
  }
  when(req.valid) {
    state := sRequest
    newGroup := true.B
  }
  // load/store whole register
  val whole: Bool = requestReg.instInf.mop === 0.U && requestReg.instInf.vs2 === 8.U
  val invalidInstruction: Bool = RegNext(csrInterface.vl === 0.U && req.valid && !whole)
  val stateIdle = state === idle
  status.instIndex := requestReg.instIndex
  status.idle := stateIdle
  status.last := (!RegNext(stateIdle) && stateIdle) || invalidInstruction
  status.targetLane := UIntToOH(Mux(requestReg.instInf.st, s0Reg.targetLaneIndex, baseByteOffsetD(4, 2)))
  status.waitFirstResp := waitFirstResp
  // 需要更新的时候是需要下一组的mask
  maskSelect.bits := nextGroupIndex
}
