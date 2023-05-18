package v

import chisel3._
import chisel3.util._
import tilelink.{TLBundle, TLBundleParameter, TLChannelAParameter, TLChannelD, TLChannelDParameter}

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

  def mshrParam: MSHRParam = MSHRParam(chainingSize, datapathWidth, vLen, laneNumber, paWidth, tlParam)

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

  /** interface to [[V]], redirect to [[Lane]].
    * this group of offset is finish, request the next group of offset.
    */
  val lsuOffsetRequest: Bool = IO(Output(Bool()))

  /** which MSHR will be allocated to the incoming instruction.
    * TODO: rename to `requestEnqueue`
    */
  val reqEnq: Vec[Bool] = Wire(Vec(param.lsuMSHRSize, Bool()))

  // MSHR To Lane VRF access
  // combinational logic loop:
  // ready -> read -> bits -> ready
  // break it by giving a fine grained `winner` signal:
  // mshrTryToAccessLane -> winner
  // winner & ready -> fire
  // winner -> bits -> ready
  /** a matrix to indicate which MSHR is trying to read VRF from which lane. */
  val mshrToLaneTryToReadVRF: Vec[UInt] = Wire(Vec(param.lsuMSHRSize, UInt(param.laneNumber.W)))

  /** a matrix to indicate which MSHR can read VRF from which lane. */
  val mshrToLaneReadVRFWinner: Vec[Vec[Bool]] = Wire(Vec(param.lsuMSHRSize, Vec(param.laneNumber, Bool())))

  /** a matrix to indicate which MSHR is reading VRF from which lane. */
  val mshrToLaneReadVRFFire: Vec[Vec[Bool]] = Wire(Vec(param.lsuMSHRSize, Vec(param.laneNumber, Bool())))

  /** a vector of MSHR that indicates which one is reading VRF from VRF. */
  val mshrReadVRFFire: IndexedSeq[Bool] = mshrToLaneReadVRFFire.map(_.asUInt.orR)

  /** a matrix to indicate which MSHR is trying to write VRF to which lane. */
  val mshrToLaneTryToWriteVRF: Vec[UInt] = Wire(Vec(param.lsuMSHRSize, UInt(param.laneNumber.W)))

  /** a matrix to indicate which MSHR can write VRF to which lane. */
  val mshrToLaneWriteVRFWinner: Vec[Vec[Bool]] = Wire(Vec(param.lsuMSHRSize, Vec(param.laneNumber, Bool())))

  /** a matrix to indicate which MSHR is writing VRF to which lane. */
  val mshrToLaneWriteVRFFire: Vec[Vec[Bool]] = Wire(Vec(param.lsuMSHRSize, Vec(param.laneNumber, Bool())))

  /** a vector of MSHR that indicates which one is writing VRF to VRF. */
  val mshrWriteVRFFire: IndexedSeq[Bool] = mshrToLaneWriteVRFFire.map(_.asUInt.orR)

  // MSHR to Memory access signals
  /** a vector of MSHR that try to send Get by TileLink A channel. */
  val mshrTryToUseTLAChannel: Vec[UInt] = Wire(Vec(param.lsuMSHRSize, UInt(param.memoryBankSize.W)))

  /** a vector of MSHR that is sending Get by TileLink A channel. */
  val mshrAccessTLAChannelFire: Vec[Vec[Bool]] = Wire(Vec(param.lsuMSHRSize, Vec(param.memoryBankSize, Bool())))

  /** a matrix to indicate which memory channel is trying to access which MSHR */
  val tlDChannelTryToAckMSHR: Vec[UInt] = Wire(Vec(param.memoryBankSize, UInt(param.lsuMSHRSize.W)))

  /** a matrix to indicate which memory channel can access which MSHR */
  val tlDChannelAckMSHRWinner: Vec[Vec[Bool]] = Wire(Vec(param.memoryBankSize, Vec(param.lsuMSHRSize, Bool())))

  /** a matrix to indicate which memory channel is accessing which MSHR */
  val tlDChannelAckMSHRFire: Vec[Vec[Bool]] = Wire(Vec(param.memoryBankSize, Vec(param.lsuMSHRSize, Bool())))

  /** a vector of memory channel that indicate which one is sending ACK ack and data. */
  val tlDChannelAckFire: IndexedSeq[Bool] = tlDChannelAckMSHRFire.map(_.asUInt.orR)

  /** TileLink D Channel write to VRF queue:
    * TL-D -CrossBar-> MSHR -proxy-> write queue -CrossBar-> VRF
    */
  val writeQueueVec: Seq[Queue[LSUWriteQueueBundle]] = Seq.fill(param.lsuMSHRSize)(
    Module(new Queue(new LSUWriteQueueBundle(param), param.lsuVRFWriteQueueSize))
  )

  /** [[MSHR]] Modules */
  val mshrVec: Seq[MSHR] = Seq.tabulate(param.lsuMSHRSize) { index =>
    val mshr: MSHR = Module(new MSHR(param.mshrParam)).suggestName(s"mshr_$index")

    mshr.lsuRequest.valid := reqEnq(index)
    mshr.lsuRequest.bits := request.bits

    mshrToLaneTryToReadVRF(index) := Mux(mshr.vrfReadDataPorts.valid, mshr.status.targetLane, 0.U)
    mshr.vrfReadDataPorts.ready := mshrReadVRFFire(index)
    // We use SeqMem to implement VRF, so we need to read previous cycle to get the correct result.
    mshr.vrfReadResults := Mux1H(RegNext(mshr.status.targetLane), vrfReadResults)

    // lane broadcast [[offsetReadResult]] to [[LSU]], use instructionIndex to identify which mshr is the target.
    Seq.tabulate(param.laneNumber) { laneID =>
      mshr.offsetReadResult(laneID).valid :=
        offsetReadResult(laneID).valid && offsetReadIndex(laneID) === mshr.status.instructionIndex
      mshr.offsetReadResult(laneID).bits :=
        offsetReadResult(laneID).bits
    }

    maskSelect(index) := Mux(mshr.maskSelect.valid, mshr.maskSelect.bits, 0.U)
    mshr.maskInput := maskInput(index)

    // select bank
    mshrTryToUseTLAChannel(index) := Mux(
      mshr.tlPort.a.valid,
      UIntToOH(mshr.tlPort.a.bits.address(param.bankPosition)),
      0.U
    )
    mshr.tlPort.a.ready := mshrAccessTLAChannelFire(index).asUInt.orR

    // TODO: move out from MSHR
    tlDChannelTryToAckMSHR.map(_(index)).zipWithIndex.foldLeft(false.B) {
      case (occupied, (tryToUse, i)) =>
        tlDChannelAckMSHRFire(i)(index) :=
          tryToUse && !occupied && tlPort(i).d.valid && writeQueueVec(index).io.enq.ready
        tlDChannelAckMSHRWinner(i)(index) := !occupied
        occupied || (tryToUse && tlPort(i).d.valid)
    }

    /** select bits from multiple TL-D
      * TODO: use MuxOH to select from multiple tlPorts.
      */
    val selectedTLDChannel: TLChannelD = Mux(tlDChannelAckMSHRFire.head(index), tlPort.head.d.bits, tlPort.last.d.bits)
    // only transaction has data will be sent to MSHR, otherwise, it will be dropped.
    mshr.tlPort.d.valid :=
      VecInit(tlDChannelAckMSHRFire.map(_(index))).asUInt.orR &&
      (
        selectedTLDChannel.opcode =/= 0.U ||
        mshr.status.waitFirstResponse
      )
    mshr.tlPort.d.bits := selectedTLDChannel
    // the last 2 bits is MSHR index
    // TODO: no magic number
    mshr.tlPort.d.bits.source := (selectedTLDChannel.source >> 2).asUInt

    // connect write queue
    writeQueueVec(index).io.enq.valid := mshr.vrfWritePort.valid
    writeQueueVec(index).io.enq.bits.data := mshr.vrfWritePort.bits
    writeQueueVec(index).io.enq.bits.targetLane := mshr.status.targetLane
    mshr.vrfWritePort.ready := writeQueueVec(index).io.enq.ready
    mshrToLaneTryToWriteVRF(index) := Mux(
      writeQueueVec(index).io.deq.valid,
      writeQueueVec(index).io.deq.bits.targetLane,
      0.U
    )
    writeQueueVec(index).io.deq.ready := mshrWriteVRFFire(index)

    // broadcast CSR
    mshr.csrInterface := csrInterface
    mshr
  }

  /** indicate which MSHR is empty. */
  val idleMSHRs: UInt = VecInit(mshrVec.map(_.status.idle)).asUInt

  /** selected the the first idle mshr */
  val selectedIdleMSHR: UInt = ffo(idleMSHRs)(param.lsuMSHRSize - 1, 0)
  reqEnq := VecInit(Mux(request.valid, selectedIdleMSHR, 0.U).asBools)
  // todo: address conflict
  // todo: Execute only a single lsu instruction first
  request.ready := idleMSHRs.andR

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
    vrfReadDataPorts(laneID).bits := Mux1H(mshrToLaneReadVRFWinner.map(_(laneID)), mshrVec.map(_.vrfReadDataPorts.bits))

    mshrToLaneTryToWriteVRF.map(_(laneID)).zipWithIndex.foldLeft(false.B) {
      case (occupied, (tryToUse, i)) =>
        mshrToLaneWriteVRFWinner(i)(laneID) := tryToUse && !occupied
        mshrToLaneWriteVRFFire(i)(laneID) := mshrToLaneWriteVRFWinner(i)(laneID) && vrfWritePort(laneID).ready
        occupied || tryToUse
    }
    vrfWritePort(laneID).valid := VecInit(mshrToLaneWriteVRFWinner.map(_(laneID))).asUInt.orR
    vrfWritePort(laneID).bits := Mux1H(mshrToLaneWriteVRFWinner.map(_(laneID)), writeQueueVec.map(_.io.deq.bits.data))
  }

  /** extract source from each TL-D channel. */
  val mshrIdFromTLDSource: IndexedSeq[UInt] = tlPort.map(_.d.bits.source(1, 0))

  /** which MSHR(encoded in OH) should go to for each port. */
  val tlDSourceOH: IndexedSeq[UInt] = mshrIdFromTLDSource.map(UIntToOH(_))

  Seq.tabulate(param.memoryBankSize) { bankID =>
    mshrTryToUseTLAChannel.map(_(bankID)).zipWithIndex.foldLeft(false.B) {
      case (occupied, (tryToUse, i)) =>
        mshrAccessTLAChannelFire(i)(bankID) := tryToUse && !occupied && tlPort(bankID).a.ready
        occupied || tryToUse
    }

    /** encode MSHR index to source id in A channel. */
    val sourceExtend: UInt = OHToUInt(VecInit(mshrAccessTLAChannelFire.map(_(bankID))).asUInt)
    tlPort(bankID).a.valid := VecInit(mshrAccessTLAChannelFire.map(_(bankID))).asUInt.orR
    tlPort(bankID).a.bits := Mux1H(mshrAccessTLAChannelFire.map(_(bankID)), mshrVec.map(_.tlPort.a.bits))
    tlPort(bankID).a.bits.source := Mux1H(
      mshrAccessTLAChannelFire.map(_(bankID)),
      mshrVec.map(_.tlPort.a.bits.source)
    ) ## sourceExtend
    tlDChannelTryToAckMSHR(bankID) := tlDSourceOH(bankID)
    tlPort(bankID).d.ready := tlDChannelAckFire(bankID) || tlPort(bankID).d.bits.opcode === 0.U
  }

  // gather last signal from all MSHR to notify LSU
  lastReport := mshrVec
    .map(m => Mux(m.status.last, indexToOH(m.status.instructionIndex, param.chainingSize), 0.U))
    .reduce(_ | _)
  lsuOffsetRequest := VecInit(mshrVec.map(_.status.offsetGroupEnd)).asUInt.orR
}
