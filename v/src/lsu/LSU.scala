// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v

import chisel3._
import chisel3.util._
import tilelink.{TLBundle, TLBundleParameter, TLChannelA, TLChannelD}

/**
  * @param datapathWidth ELEN
  * @param chainingSize how many instructions can be chained
  * @param vLen VLEN
  * @param laneNumber how many lanes in the vector processor
  * @param paWidth physical address width
  */
case class LSUParam(
  datapathWidth:        Int,
  chainingSize:         Int,
  vLen:                 Int,
  laneNumber:           Int,
  paWidth:              Int,
  sourceWidth:          Int,
  sizeWidth:            Int,
  maskWidth:            Int,
  memoryBankSize:       Int,
  lsuMSHRSize:          Int,
  lsuVRFWriteQueueSize: Int,
  cacheLineSize:        Int,
  tlParam:              TLBundleParameter) {

  /** see [[VParameter.maskGroupWidth]]. */
  val maskGroupWidth: Int = datapathWidth

  /** see [[VParameter.maskGroupSize]]. */
  val maskGroupSize: Int = vLen / datapathWidth

  /** hardware width of [[maskGroupSize]] */
  val maskGroupSizeBits: Int = log2Ceil(maskGroupSize)

  /** hardware width of [[CSRInterface.vl]]
    *
    * `+1` is for vl being 0 to vl(not vlMax - 1).
    * we use less than for comparing, rather than less equal.
    */
  val vLenBits: Int = log2Ceil(vLen) + 1

  val bankPosition: Int = log2Ceil(cacheLineSize)

  def mshrParam: MSHRParam =
    MSHRParam(chainingSize, datapathWidth, vLen, laneNumber, paWidth, cacheLineSize, memoryBankSize, tlParam)

  /** see [[VRFParam.regNumBits]] */
  val regNumBits: Int = log2Ceil(32)

  /** see [[VParameter.instructionIndexBits]] */
  val instructionIndexBits: Int = log2Ceil(chainingSize) + 1

  /** see [[LaneParameter.singleGroupSize]] */
  val singleGroupSize: Int = vLen / datapathWidth / laneNumber

  /** see [[LaneParameter.vrfOffsetBits]] */
  val vrfOffsetBits: Int = log2Ceil(singleGroupSize)
}

/** Load Store Unit
  * it is instantiated in [[V]],
  * it contains
  * - a bunch of [[MSHR]] to record outstanding memory transactions.
  * - a crossbar to connect memory interface and each lanes.
  */
class LSU(param: LSUParam) extends Module {

  /** [[LSURequest]] from Scheduler to LSU
    * [[request.ready]] couples to [[request.bits]] to detect memory conflict.
    * There will be two cases that [[request.ready]] is false:
    *  - LSU slots is full.
    *  - memory conflict is detected.
    */
  val request: DecoupledIO[LSURequest] = IO(Flipped(Decoupled(new LSURequest(param.datapathWidth))))

  /** mask from [[V]]
    * TODO: since mask is one-cycle information for a mask group,
    *       we should latch it in the LSU, and reduce the IO width.
    *       this needs PnR information.
    */
  val maskInput: Vec[UInt] = IO(Input(Vec(param.lsuMSHRSize, UInt(param.maskGroupWidth.W))))

  /** the address of the mask group in the [[V]]. */
  val maskSelect: Vec[UInt] = IO(Output(Vec(param.lsuMSHRSize, UInt(param.maskGroupSizeBits.W))))

  /** TileLink Port to next level memory.
    * TODO: rename to `tlPorts`
    */
  val tlPort: Vec[TLBundle] = IO(Vec(param.memoryBankSize, param.tlParam.bundle()))

  /** read channel to [[V]], which will redirect it to [[Lane.vrf]].
    * [[vrfReadDataPorts.head.ready]] will be deasserted if there are VRF hazards.
    * [[vrfReadDataPorts.head.valid]] is from MSHR in LSU
    *
    * if fire, the next cycle [[vrfReadResults]] should be valid in the next cycle.
    */
  val vrfReadDataPorts: Vec[DecoupledIO[VRFReadRequest]] = IO(
    Vec(
      param.laneNumber,
      Decoupled(new VRFReadRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits))
    )
  )

  /** hard wire form Top.
    * TODO: merge to [[vrfReadDataPorts]]
    */
  val vrfReadResults: Vec[UInt] = IO(Input(Vec(param.laneNumber, UInt(param.datapathWidth.W))))

  /** write channel to [[V]], which will redirect it to [[Lane.vrf]]. */
  val vrfWritePort: Vec[DecoupledIO[VRFWriteRequest]] = IO(
    Vec(
      param.laneNumber,
      Decoupled(
        new VRFWriteRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits, param.datapathWidth)
      )
    )
  )

  /** the CSR interface from [[V]], CSR will be latched in MSHR.
    * TODO: merge to [[LSURequest]]
    */
  val csrInterface: CSRInterface = IO(Input(new CSRInterface(param.vLenBits)))

  /** offset of indexed load/store instructions. */
  val offsetReadResult: Vec[ValidIO[UInt]] = IO(Vec(param.laneNumber, Flipped(Valid(UInt(param.datapathWidth.W)))))

  /** which instruction is requesting the offset.
    * TODO: merge to [[offsetReadResult]]
    */
  val offsetReadIndex: Vec[UInt] = IO(Input(Vec(param.laneNumber, UInt(param.instructionIndexBits.W))))

  /** interface to [[V]], indicate a MSHR slots is finished, and corresponding instruction can commit. */
  val lastReport: UInt = IO(Output(UInt(param.chainingSize.W)))

  val lsuMaskGroupChange: UInt = IO(Output(UInt(param.chainingSize.W)))

  /** interface to [[V]], redirect to [[Lane]].
    * this group of offset is finish, request the next group of offset.
    */
  val lsuOffsetRequest: Bool = IO(Output(Bool()))
  val writeReadyForLsu: Bool = IO(Input(Bool()))
  val vrfReadyToStore: Bool = IO(Input(Bool()))

  val loadUnit: LoadUnit = Module(new LoadUnit(param.mshrParam))
  val storeUnit: StoreUnit = Module(new StoreUnit(param.mshrParam))
  val otherUnit: SimpleAccessUnit = Module(new SimpleAccessUnit(param.mshrParam))

  val unitVec: Seq[Module with LSUPublic] = Seq(loadUnit, storeUnit, otherUnit)

  /** Always merge into cache line */
  val alwaysMerge: Bool = (request.bits.instructionInformation.mop ## request.bits.instructionInformation.lumop) === 0.U
  val useLoadUnit: Bool = alwaysMerge && !request.bits.instructionInformation.isStore
  val useStoreUnit: Bool = alwaysMerge && request.bits.instructionInformation.isStore
  val useOtherUnit: Bool = !alwaysMerge
  val addressCheck: Bool = otherUnit.status.idle && (!useOtherUnit || (loadUnit.status.idle && storeUnit.status.idle))
  val unitReady: Bool = (useLoadUnit && loadUnit.status.idle) || (useStoreUnit && storeUnit.status.idle) || (useOtherUnit && otherUnit.status.idle)
  request.ready := unitReady && addressCheck

  val requestFire = request.fire
  val reqEnq: Vec[Bool] = VecInit(Seq(useLoadUnit && requestFire, useStoreUnit && requestFire, useOtherUnit && requestFire))

  unitVec.zipWithIndex.foreach { case(mshr, index) =>
    mshr.lsuRequest.valid := reqEnq(index)
    mshr.lsuRequest.bits := request.bits

    maskSelect(index) := Mux(mshr.maskSelect.valid, mshr.maskSelect.bits, 0.U)
    mshr.maskInput := maskInput(index)

    // broadcast CSR
    mshr.csrInterface := csrInterface
  }

  /** TileLink D Channel write to VRF queue:
   * TL-D -CrossBar-> MSHR -proxy-> write queue -CrossBar-> VRF
   */
  val writeQueueVec: Seq[Queue[LSUWriteQueueBundle]] = Seq.fill(param.laneNumber)(
    Module(new Queue(new LSUWriteQueueBundle(param), param.lsuVRFWriteQueueSize))
  )

  // read vrf
  val otherTryReadVrf: UInt = Mux(otherUnit.vrfReadDataPorts.valid, otherUnit.status.targetLane, 0.U)
  vrfReadDataPorts.zipWithIndex.foreach { case (read, index) =>
    read.valid := otherTryReadVrf(index) || storeUnit.vrfReadDataPorts(index).valid
    read.bits := Mux(otherTryReadVrf(index), otherUnit.vrfReadDataPorts.bits, storeUnit.vrfReadDataPorts(index).bits)
    storeUnit.vrfReadDataPorts(index).ready := read.ready && !otherTryReadVrf(index)
    storeUnit.vrfReadResults(index) := vrfReadResults(index)
  }
  otherUnit.vrfReadDataPorts.ready := (otherTryReadVrf & VecInit(vrfReadDataPorts.map(_.ready)).asUInt).orR
  otherUnit.vrfReadResults := Mux1H(RegNext(otherUnit.status.targetLane), vrfReadResults)

  // write vrf
  val otherTryToWrite: UInt = Mux(otherUnit.vrfWritePort.valid, otherUnit.status.targetLane, 0.U)
  // other 优先级更高
  otherUnit.vrfWritePort.ready := (otherUnit.status.targetLane & VecInit(writeQueueVec.map(_.io.enq.ready)).asUInt).orR
  writeQueueVec.zipWithIndex.foreach {case (write, index) =>
    write.io.enq.valid := otherTryToWrite(index) || loadUnit.vrfWritePort(index).valid
    write.io.enq.bits.data := Mux(otherTryToWrite(index), otherUnit.vrfWritePort.bits, loadUnit.vrfWritePort(index).bits)
    write.io.enq.bits.targetLane := (1 << index).U
    loadUnit.vrfWritePort(index).ready := write.io.enq.ready && !otherTryToWrite(index)
  }

  vrfWritePort.zip(writeQueueVec).foreach { case (p, q) =>
    p.valid := q.io.deq.valid
    p.bits := q.io.deq.bits.data
    q.io.deq.ready := p.ready
  }

  val accessPortA: Seq[DecoupledIO[TLChannelA]] = Seq(loadUnit.tlPortA, otherUnit.tlPort.a)
  val mshrTryToUseTLAChannel: Vec[UInt] =
    WireInit(VecInit(accessPortA.map(
      p =>
        Mux(
          p.valid,
          UIntToOH(p.bits.address(param.bankPosition + log2Ceil(param.memoryBankSize) - 1, param.bankPosition)),
          0.U
        )
    )))
  // connect tile link a
  val readyVec: Seq[Bool] = tlPort.zipWithIndex.map { case (tl, index) =>
    val port: DecoupledIO[TLChannelA] = tl.a
    val storeRequest: DecoupledIO[TLChannelA] = storeUnit.tlPortA(index)
    val portFree: Bool = storeUnit.status.releasePort(index)
    val Seq(loadTryToUse, otherTryToUse) = mshrTryToUseTLAChannel.map(_(index))

    /**
     * a 通道的优先级分两种情况:
     *  1. store unit 声明占用时, 无条件给 store unit
     *  1. 不声明占用时, load > store > other
     * */
    val requestSelect = Seq(
      // select load unit
      portFree && loadTryToUse,
      // select store unit
      !portFree || (!loadTryToUse && storeRequest.valid),
      // select otherUnit
      portFree && !loadTryToUse && !storeRequest.valid
    )
    val selectIndex: UInt = OHToUInt(requestSelect)

    // 选出一个请求连到 a 通道上
    val selectBits = Mux1H(requestSelect, Seq(loadUnit.tlPortA.bits, storeRequest.bits, otherUnit.tlPort.a.bits))
    port.valid := storeRequest.valid || ((loadTryToUse || otherTryToUse) && portFree)
    port.bits := selectBits
    port.bits.source := selectBits.source ## selectIndex
    // 反连 ready
    storeRequest.ready := requestSelect(1) && port.ready
    Seq(requestSelect.head && port.ready, requestSelect.last && port.ready)
  }.transpose.map(rv => VecInit(rv).asUInt.orR)
  loadUnit.tlPortA.ready := readyVec.head
  otherUnit.tlPort.a.ready := readyVec.last

  // connect tile link D
  val tlDFireForOther: Vec[Bool] = Wire(Vec(param.memoryBankSize, Bool()))
  tlPort.zipWithIndex.foldLeft(false.B) {case (o, (tl, index)) =>
    val port: DecoupledIO[TLChannelD] = tl.d
    val isAccessAck = port.bits.opcode === 0.U
    // 0 -> load unit, 0b10 -> other unit
    val responseForOther: Bool = port.bits.source(1)
    loadUnit.tlPortD(index).valid := port.valid && !responseForOther && !isAccessAck
    loadUnit.tlPortD(index).bits := port.bits
    loadUnit.tlPortD(index).bits.source := port.bits.source >> 2
    port.ready := isAccessAck || Mux(responseForOther, !o && otherUnit.tlPort.d.ready, loadUnit.tlPortD(index).ready)
    tlDFireForOther(index) := !o && responseForOther
    o || responseForOther
  }
  otherUnit.tlPort.d.valid := tlDFireForOther.asUInt.orR
  otherUnit.tlPort.d.bits := Mux1H(tlDFireForOther, tlPort.map(_.d.bits))
  otherUnit.tlPort.d.bits.source := Mux1H(tlDFireForOther, tlPort.map(_.d.bits.source)) >> 2

  // index offset connect
  otherUnit.offsetReadResult := offsetReadResult

  // gather last signal from all MSHR to notify LSU
  lastReport :=
    unitVec.map(m => Mux(m.status.last, indexToOH(m.status.instructionIndex, param.chainingSize), 0.U)).reduce(_ | _)
  lsuMaskGroupChange := unitVec.map(
    m => Mux(m.status.changeMaskGroup, indexToOH(m.status.instructionIndex, param.chainingSize), 0.U)
  ).reduce(_ | _)
  lsuOffsetRequest := otherUnit.status.offsetGroupEnd
  loadUnit.writeReadyForLsu := writeReadyForLsu
  storeUnit.vrfReadyToStore := vrfReadyToStore
}
