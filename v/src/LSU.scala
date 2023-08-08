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

  /** TODO: configured by cache line size. */
  val bankPosition: Int = 6

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

  val loadUnit: LoadUnit = Module(new LoadUnit(param.mshrParam))
  val storeUnit: MSHR = Module(new MSHR(param.mshrParam))
  val otherUnit: MSHR = Module(new MSHR(param.mshrParam))

  val unitVec: Seq[LSUBase] = Seq(loadUnit, storeUnit, otherUnit)
  val storeVec: Seq[MSHR] = Seq(storeUnit, otherUnit)

  /** Always merge into cache line */
  val alwaysMerge: Bool = (request.bits.instructionInformation.mop ## request.bits.instructionInformation.lumop) === 0.U
  val useLoadUnit: Bool = alwaysMerge && !request.bits.instructionInformation.isStore
  val useStoreUnit: Bool = alwaysMerge && request.bits.instructionInformation.isStore
  val addressCheck: Bool = WireDefault(true.B)
  val useOtherUnit: Bool = !alwaysMerge
  val unitReady: Bool = (useLoadUnit && loadUnit.status.idle) || (useStoreUnit && storeUnit.status.idle) || (useOtherUnit && otherUnit.status.idle)
  request.ready := unitReady && addressCheck

  val requestFire = request.fire
  val reqEnq: Vec[Bool] = VecInit(Seq(useLoadUnit && requestFire, useLoadUnit && requestFire, useLoadUnit && requestFire))

  unitVec.zipWithIndex.foreach { case(mshr, index) =>
    mshr.lsuRequest.valid := reqEnq(index)
    mshr.lsuRequest.bits := request.bits

    maskSelect(index) := Mux(mshr.maskSelect.valid, mshr.maskSelect.bits, 0.U)
    mshr.maskInput := maskInput(index)

    // broadcast CSR
    mshr.csrInterface := csrInterface
  }
  storeUnit.lsuRequest.bits.instructionInformation.isStore := true.B
  storeUnit.lsuRequest.bits.instructionInformation.mop := 0.U
  storeUnit.lsuRequest.bits.instructionInformation.lumop := 0.U

  // read vrf
  /** a matrix to indicate which MSHR is trying to read VRF from which lane. */
  val mshrToLaneTryToReadVRF: Vec[UInt] = Wire(Vec(param.lsuMSHRSize - 1, UInt(param.laneNumber.W)))

  /** a matrix to indicate which MSHR can read VRF from which lane. */
  val mshrToLaneReadVRFWinner: Vec[Vec[Bool]] = Wire(Vec(param.lsuMSHRSize - 1, Vec(param.laneNumber, Bool())))

  /** a matrix to indicate which MSHR is reading VRF from which lane. */
  val mshrToLaneReadVRFFire: Vec[Vec[Bool]] = Wire(Vec(param.lsuMSHRSize - 1, Vec(param.laneNumber, Bool())))

  /** a vector of MSHR that indicates which one is reading VRF from VRF. */
  val mshrReadVRFFire: IndexedSeq[Bool] = mshrToLaneReadVRFFire.map(_.asUInt.orR)

  storeVec.zipWithIndex.foreach { case (mshr, index) =>
    mshrToLaneTryToReadVRF(index) := Mux(mshr.vrfReadDataPorts.valid, mshr.status.targetLane, 0.U)
    mshr.vrfReadDataPorts.ready := mshrReadVRFFire(index)
    // We use SeqMem to implement VRF, so we need to read previous cycle to get the correct result.
    mshr.vrfReadResults := Mux1H(RegNext(mshr.status.targetLane), vrfReadResults)
  }

  Seq.tabulate(param.laneNumber) { laneID =>
    // LSU slots read VRF request arbitration
    // TODO: it may have dead lock issue:
    //       the priority in LSU is low, but VRF has hazard, allocate VRF access to high priority instruction.
    // TODO: give a test case to illustrate the issue
    // TODO: the issue may be solved by adding priority bit here.
    mshrToLaneTryToReadVRF.map(_(laneID)).zipWithIndex.foldLeft(false.B) {
      case (occupied, (tryToUse, i)) =>
        mshrToLaneReadVRFWinner(i)(laneID) := tryToUse && !occupied
        mshrToLaneReadVRFFire(i)(laneID) := mshrToLaneReadVRFWinner(i)(laneID) && vrfReadDataPorts(laneID).ready
        occupied || tryToUse
    }
    vrfReadDataPorts(laneID).valid := VecInit(mshrToLaneReadVRFWinner.map(_(laneID))).asUInt.orR
    vrfReadDataPorts(laneID).bits := Mux1H(mshrToLaneReadVRFWinner.map(_(laneID)), storeVec.map(_.vrfReadDataPorts.bits))
  }

  /** TileLink D Channel write to VRF queue:
   * TL-D -CrossBar-> MSHR -proxy-> write queue -CrossBar-> VRF
   */
  val writeQueueVec: Seq[Queue[LSUWriteQueueBundle]] = Seq.fill(param.laneNumber)(
    Module(new Queue(new LSUWriteQueueBundle(param), param.lsuVRFWriteQueueSize))
  )

  // write vrf
  val otherTryToWrite = Mux(otherUnit.vrfWritePort.valid, otherUnit.status.targetLane, 0.U)
  // other 优先级更高
  otherUnit.vrfWritePort.ready := (otherUnit.status.targetLane & VecInit(vrfWritePort.map(_.ready)).asUInt).orR
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

  // access tile link a
  val accessPortA: Seq[DecoupledIO[TLChannelA]] = Seq(loadUnit.tlPortA, storeUnit.tlPort.a, otherUnit.tlPort.a)
  /** a vector of MSHR that try to send Get by TileLink A channel. */
  val mshrTryToUseTLAChannel: Vec[UInt] = WireInit(VecInit(
    accessPortA.map(p => Mux(p.valid, UIntToOH(p.bits.address(param.bankPosition)), 0.U))
  ))

  /** a vector of MSHR that is sending Get by TileLink A channel. */
  val mshrAccessTLAChannelFire: Vec[Vec[Bool]] = Wire(Vec(param.lsuMSHRSize, Vec(param.memoryBankSize, Bool())))

  Seq.tabulate(param.memoryBankSize) { bankID =>
    mshrTryToUseTLAChannel.map(_(bankID)).zipWithIndex.foldLeft(false.B) {
      case (occupied, (tryToUse, i)) =>
        mshrAccessTLAChannelFire(i)(bankID) := tryToUse && !occupied && tlPort(bankID).a.ready
        occupied || tryToUse
    }
    /** encode MSHR index to source id in A channel. */
    val sourceExtend: UInt = OHToUInt(VecInit(mshrAccessTLAChannelFire.map(_(bankID))).asUInt)
    tlPort(bankID).a.valid := VecInit(mshrAccessTLAChannelFire.map(_(bankID))).asUInt.orR
    tlPort(bankID).a.bits := Mux1H(mshrAccessTLAChannelFire.map(_(bankID)), accessPortA.map(_.bits))
    tlPort(bankID).a.bits.source := Mux1H(
      mshrAccessTLAChannelFire.map(_(bankID)),
      accessPortA.map(_.bits.source)
    ) ## sourceExtend
  }
  accessPortA.zipWithIndex.foreach{ case (p, i) =>
    p.ready := mshrAccessTLAChannelFire(i).asUInt.orR
  }

  // connect tile link D
  val tlDFireForOther: Vec[Bool] = Wire(Vec(param.memoryBankSize, Bool()))
  tlPort.zipWithIndex.foldLeft(false.B) {case (o, (tl, index)) =>
    val port: DecoupledIO[TLChannelD] = tl.d
    val isAccessAck = port.bits.opcode === 0.U
    // 0 -> load unit, 0b10 -> other unit
    val responseForOther: Bool = port.bits.source(1)
    loadUnit.tlPortD(index).valid := port.valid && !responseForOther
    loadUnit.tlPortD(index).bits := port.bits
    loadUnit.tlPortD(index).bits.source := port.bits.source >> 2
    port.ready := Mux(responseForOther, !o && otherUnit.tlPort.d.ready, loadUnit.tlPortD(index).ready)
    tlDFireForOther(index) := !o && responseForOther
    o || responseForOther
  }
  otherUnit.tlPort.d.valid := tlDFireForOther.asUInt.orR
  otherUnit.tlPort.d.bits := Mux1H(tlDFireForOther, tlPort.map(_.d.bits))
  otherUnit.tlPort.d.bits.source := Mux1H(tlDFireForOther, tlPort.map(_.d.bits.source)) >> 2
  storeUnit.tlPort.d <> DontCare
  storeUnit.offsetReadResult <> DontCare
  storeUnit.vrfWritePort.ready <> DontCare

  // index offset connect
  otherUnit.offsetReadResult := offsetReadResult

  // gather last signal from all MSHR to notify LSU
  lastReport := storeVec
    .map(m => Mux(m.status.last, indexToOH(m.status.instructionIndex, param.chainingSize), 0.U))
    .reduce(_ | _) | Mux(loadUnit.status.last, indexToOH(loadUnit.status.instructionIndex, param.chainingSize), 0.U)
  lsuMaskGroupChange := storeVec
    .map(m => Mux(m.status.changeMaskGroup, indexToOH(m.status.instructionIndex, param.chainingSize), 0.U))
    .reduce(_ | _) |
    Mux(loadUnit.status.changeMaskGroup, indexToOH(loadUnit.status.instructionIndex, param.chainingSize), 0.U)
  lsuOffsetRequest := otherUnit.status.offsetGroupEnd
}
