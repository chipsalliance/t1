// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lsu

import chisel3._
import chisel3.experimental.hierarchy.{Instance, Instantiate, instantiable, public}
import chisel3.probe.{Probe, ProbeValue, define}
import chisel3.util._
import chisel3.util.experimental.BitSet
import org.chipsalliance.t1.rtl.{CSRInterface, LSUBankParameter, LSURequest, LSUWriteQueueBundle, VRFReadRequest, VRFWriteRequest, firstlastHelper, indexToOH, instIndexL}
import tilelink.{TLBundle, TLBundleParameter, TLChannelA, TLChannelD}

// TODO: need some idea from BankBinder
object LSUInstantiateParameter {
  implicit def bitSetP:upickle.default.ReadWriter[BitSet] = upickle.default.readwriter[String].bimap[BitSet](
    _.toString,
    BitSet.fromString
  )

  implicit def rwP: upickle.default.ReadWriter[LSUInstantiateParameter] = upickle.default.macroRW
}

/** Public LSU parameter expose to upper level. */
case class LSUInstantiateParameter(name: String, base: BigInt, size: BigInt, banks: Int) {
  // TODO: uarch tuning for different LSUs to reduce segment overhead.
  //       these tweaks should only be applied to some special MMIO LSU, e.g. systolic array, etc
  val supportStride: Boolean = true
  val supportSegment: Set[Int] = Seq.tabulate(8)(_ + 1).toSet
  val supportMask: Boolean = true
  // TODO: support MMU for linux.
  val supportMMU: Boolean = false

  // used for add latency from LSU to corresponding lanes, it should be managed by floorplan
  val latencyToLanes: Seq[Int] = Seq(1)
  // used for add queue for avoid dead lock on memory.
  val maxLatencyToEndpoint: Int = 96
}

/**
  * @param datapathWidth ELEN
  * @param chainingSize how many instructions can be chained
  * @param vLen VLEN
  * @param laneNumber how many lanes in the vector processor
  * @param paWidth physical address width
  */
case class LSUParameter(
                         datapathWidth:        Int,
                         chainingSize:         Int,
                         vLen:                 Int,
                         laneNumber:           Int,
                         paWidth:              Int,
                         sourceWidth:          Int,
                         sizeWidth:            Int,
                         maskWidth:            Int,
                         banks:                Seq[LSUBankParameter],
                         lsuMSHRSize:          Int,
                         toVRFWriteQueueSize:  Int,
                         transferSize:         Int,
                         // TODO: refactor to per lane parameter.
                         vrfReadLatency:       Int,
                         tlParam:              TLBundleParameter,
                         name: String
                       ) {
  val memoryBankSize: Int = banks.size

  banks.zipWithIndex.foreach { case (bs, i) =>
    Seq.tabulate(banks.size) { bankIndex =>
      require(i == bankIndex || !banks(bankIndex).region.overlap(bs.region))
    }
  }

  val sewMin: Int = 8

  /** the maximum address offsets number can be accessed from lanes for one time. */
  val maxOffsetPerLaneAccess: Int = datapathWidth * laneNumber / sewMin

  /** see [[MSHRParam.maskGroupWidth]]. */
  val maskGroupWidth: Int = maxOffsetPerLaneAccess

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

  val sourceQueueSize: Int = vLen * 8 / (transferSize * 8)

  def mshrParam: MSHRParam =
    MSHRParam(chainingSize, datapathWidth, vLen, laneNumber, paWidth, transferSize, memoryBankSize, vrfReadLatency, banks, tlParam)

  /** see [[VRFParam.regNumBits]] */
  val regNumBits: Int = log2Ceil(32)

  /** see [[VParameter.instructionIndexBits]] */
  val instructionIndexBits: Int = log2Ceil(chainingSize) + 1

  /** see [[LaneParameter.singleGroupSize]] */
  val singleGroupSize: Int = vLen / datapathWidth / laneNumber

  /** see [[LaneParameter.vrfOffsetBits]] */
  val vrfOffsetBits: Int = log2Ceil(singleGroupSize)
}

class LSUSlotProbe(param: LSUParameter) extends Bundle {
  val dataVd: UInt = UInt(param.regNumBits.W)
  val dataOffset: UInt = UInt(param.vrfOffsetBits.W)
  val dataMask: UInt = UInt((param.datapathWidth / 8).W)
  val dataData: UInt = UInt(param.datapathWidth.W)
  val dataInstruction: UInt = UInt(param.instructionIndexBits.W)
  val writeValid: Bool = Bool()
  val targetLane: UInt = UInt(param.laneNumber.W)
}

class MemoryWriteProbe(param: MSHRParam) extends Bundle {
  val valid: Bool = Bool()
  val data: UInt = UInt((param.lsuTransposeSize * 8).W)
  val mask: UInt = UInt(param.lsuTransposeSize.W)
  val index: UInt = UInt(param.cacheLineIndexBits.W)
  val address: UInt = UInt(param.paWidth.W)
}

class LSUProbe(param: LSUParameter) extends Bundle {
  val slots = Vec(param.laneNumber, new LSUSlotProbe(param))
  val storeUnitProbe = new MemoryWriteProbe(param.mshrParam)
  val otherUnitProbe = new MemoryWriteProbe(param.mshrParam)
  val reqEnq: UInt = UInt(param.lsuMSHRSize.W)
}

/** Load Store Unit
  * it is instantiated in [[V]],
  * it contains
  * - a bunch of [[MSHR]] to record outstanding memory transactions.
  * - a crossbar to connect memory interface and each lanes.
  */
@instantiable
class LSU(param: LSUParameter) extends Module {

  /** [[LSURequest]] from Scheduler to LSU
    * [[request.ready]] couples to [[request.bits]] to detect memory conflict.
    * There will be two cases that [[request.ready]] is false:
    *  - LSU slots is full.
    *  - memory conflict is detected.
    */
  @public
  val request: DecoupledIO[LSURequest] = IO(Flipped(Decoupled(new LSURequest(param.datapathWidth))))

  /** mask from [[V]]
    * TODO: since mask is one-cycle information for a mask group,
    *       we should latch it in the LSU, and reduce the IO width.
    *       this needs PnR information.
    */
  @public
  val maskInput: Vec[UInt] = IO(Input(Vec(param.lsuMSHRSize, UInt(param.maskGroupWidth.W))))

  /** the address of the mask group in the [[V]]. */
  @public
  val maskSelect: Vec[UInt] = IO(Output(Vec(param.lsuMSHRSize, UInt(param.maskGroupSizeBits.W))))

  /** TileLink Port to next level memory.
    * TODO: rename to `tlPorts`
    */
  @public
  val tlPort: Vec[TLBundle] = IO(Vec(param.memoryBankSize, param.tlParam.bundle()))

  /** read channel to [[V]], which will redirect it to [[Lane.vrf]].
    * [[vrfReadDataPorts.head.ready]] will be deasserted if there are VRF hazards.
    * [[vrfReadDataPorts.head.valid]] is from MSHR in LSU
    *
    * if fire, the next cycle [[vrfReadResults]] should be valid in the next cycle.
    */
  @public
  val vrfReadDataPorts: Vec[DecoupledIO[VRFReadRequest]] = IO(
    Vec(
      param.laneNumber,
      Decoupled(new VRFReadRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits))
    )
  )

  /** hard wire form Top.
    * TODO: merge to [[vrfReadDataPorts]]
    */
  @public
  val vrfReadResults: Vec[UInt] = IO(Input(Vec(param.laneNumber, UInt(param.datapathWidth.W))))

  /** write channel to [[V]], which will redirect it to [[Lane.vrf]]. */
  @public
  val vrfWritePort: Vec[DecoupledIO[VRFWriteRequest]] = IO(
    Vec(
      param.laneNumber,
      Decoupled(
        new VRFWriteRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits, param.datapathWidth)
      )
    )
  )

  @public
  val dataInWriteQueue: Vec[UInt] = IO(Output(Vec(param.laneNumber, UInt(param.chainingSize.W))))

  /** the CSR interface from [[V]], CSR will be latched in MSHR.
    * TODO: merge to [[LSURequest]]
    */
  @public
  val csrInterface: CSRInterface = IO(Input(new CSRInterface(param.vLenBits)))

  /** offset of indexed load/store instructions. */
  @public
  val offsetReadResult: Vec[ValidIO[UInt]] = IO(Vec(param.laneNumber, Flipped(Valid(UInt(param.datapathWidth.W)))))

  /** which instruction is requesting the offset.
    * TODO: merge to [[offsetReadResult]]
    */
  @public
  val offsetReadIndex: Vec[UInt] = IO(Input(Vec(param.laneNumber, UInt(param.instructionIndexBits.W))))

  /** interface to [[V]], indicate a MSHR slots is finished, and corresponding instruction can commit. */
  @public
  val lastReport: UInt = IO(Output(UInt(param.chainingSize.W)))

  @public
  val lsuMaskGroupChange: UInt = IO(Output(UInt(param.chainingSize.W)))

  /** interface to [[V]], redirect to [[org.chipsalliance.t1.rtl.Lane]].
    * this group of offset is finish, request the next group of offset.
    */
  @public
  val lsuOffsetRequest: Bool = IO(Output(Bool()))
  @public
  val writeReadyForLsu: Bool = IO(Input(Bool()))
  @public
  val vrfReadyToStore: Bool = IO(Input(Bool()))

  // TODO: make it D/I
  val loadUnit: LoadUnit = Module(new LoadUnit(param.mshrParam))
  val storeUnit: StoreUnit = Module(new StoreUnit(param.mshrParam))
  val otherUnit: SimpleAccessUnit = Module(new SimpleAccessUnit(param.mshrParam))

  val unitVec = Seq(loadUnit, storeUnit, otherUnit)

  /** Always merge into cache line */
  val alwaysMerge: Bool = (
    request.bits.instructionInformation.mop ##
      // unit stride & whole register
      request.bits.instructionInformation.lumop(2, 0) ##
      request.bits.instructionInformation.lumop(4)
    ) === 0.U
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
  @public
  val writeQueueVec: Seq[Queue[LSUWriteQueueBundle]] = Seq.fill(param.laneNumber)(
    Module(new Queue(new LSUWriteQueueBundle(param), param.toVRFWriteQueueSize, flow = true))
  )

  @public
  val _probe = IO(Output(Probe(new LSUProbe(param))))
  val probeWire = Wire(new LSUProbe(param))
  define(_probe, ProbeValue(probeWire))

  // read vrf
  val otherTryReadVrf: UInt = Mux(otherUnit.vrfReadDataPorts.valid, otherUnit.status.targetLane, 0.U)
  vrfReadDataPorts.zipWithIndex.foreach { case (read, index) =>
    read.valid := otherTryReadVrf(index) || storeUnit.vrfReadDataPorts(index).valid
    read.bits := Mux(otherTryReadVrf(index), otherUnit.vrfReadDataPorts.bits, storeUnit.vrfReadDataPorts(index).bits)
    storeUnit.vrfReadDataPorts(index).ready := read.ready && !otherTryReadVrf(index)
    storeUnit.vrfReadResults(index) := vrfReadResults(index)
  }
  otherUnit.vrfReadDataPorts.ready := (otherTryReadVrf & VecInit(vrfReadDataPorts.map(_.ready)).asUInt).orR
  val pipeOtherRead: ValidIO[UInt] =
    Pipe(otherUnit.vrfReadDataPorts.fire, otherUnit.status.targetLane, param.vrfReadLatency)
  otherUnit.vrfReadResults.bits := Mux1H(pipeOtherRead.bits, vrfReadResults)
  otherUnit.vrfReadResults.valid := pipeOtherRead.valid

  // write vrf
  val otherTryToWrite: UInt = Mux(otherUnit.vrfWritePort.valid, otherUnit.status.targetLane, 0.U)
  // Permission to enter the queue TODO: Investigate why this happens
  val canEnterQueue: Vec[Bool] = Wire(Vec(param.laneNumber, Bool()))
  // other 优先级更高
  otherUnit.vrfWritePort.ready := (otherUnit.status.targetLane & VecInit(writeQueueVec.map(_.io.enq.ready)).asUInt).orR
  writeQueueVec.zipWithIndex.foreach {case (write, index) =>
    write.io.enq.valid := otherTryToWrite(index) || loadUnit.vrfWritePort(index).valid
    write.io.enq.bits.data := Mux(otherTryToWrite(index), otherUnit.vrfWritePort.bits, loadUnit.vrfWritePort(index).bits)
    write.io.enq.bits.targetLane := (BigInt(1) << index).U
    loadUnit.vrfWritePort(index).ready := write.io.enq.ready && !otherTryToWrite(index)

    // probes
    probeWire.slots(index).dataVd := write.io.enq.bits.data.vd
    probeWire.slots(index).dataOffset := write.io.enq.bits.data.offset
    probeWire.slots(index).dataMask := write.io.enq.bits.data.mask
    probeWire.slots(index).dataData := write.io.enq.bits.data.data
    probeWire.slots(index).dataInstruction := write.io.enq.bits.data.instructionIndex
    probeWire.slots(index).writeValid := write.io.enq.valid
    probeWire.slots(index).targetLane := write.io.enq.bits.targetLane
  }
  probeWire.reqEnq := reqEnq.asUInt

  probeWire.storeUnitProbe := probe.read(storeUnit.probe)
  probeWire.otherUnitProbe := probe.read(otherUnit.probe)

  vrfWritePort.zip(writeQueueVec).foreach { case (p, q) =>
    p.valid := q.io.deq.valid
    p.bits := q.io.deq.bits.data
    q.io.deq.ready := p.ready
  }

  val dataInMSHR: UInt =
    Mux(loadUnit.status.idle, 0.U(param.chainingSize.W), indexToOH(loadUnit.status.instructionIndex, param.chainingSize)) |
      Mux(
        otherUnit.status.idle || otherUnit.status.isStore,
        0.U(param.chainingSize.W),
        indexToOH(otherUnit.status.instructionIndex, param.chainingSize)
      )

  // Record whether there is data for the corresponding instruction in the queue
  writeQueueVec.zip(dataInWriteQueue).zipWithIndex.foreach {case ((q, p), queueIndex) =>
    val queueCount: Seq[UInt] = Seq.tabulate(param.chainingSize) { _ =>
      RegInit(0.U(log2Ceil(param.toVRFWriteQueueSize).W))
    }
    val enqOH: UInt = indexToOH(q.io.enq.bits.data.instructionIndex, param.chainingSize)
    val queueEnq: UInt = Mux(q.io.enq.fire, enqOH, 0.U)
    val queueDeq = Mux(q.io.deq.fire, indexToOH(q.io.deq.bits.data.instructionIndex, param.chainingSize), 0.U)
    queueCount.zipWithIndex.foreach {case (count, index) =>
      val counterUpdate: UInt = Mux(queueEnq(index), 1.U, -1.S(log2Ceil(param.toVRFWriteQueueSize).W).asUInt)
      when(queueEnq(index) ^ queueDeq(index)) {
        count := count + counterUpdate
      }
    }
    p := VecInit(queueCount.map(_ =/= 0.U)).asUInt | dataInMSHR
    val dataTag = VecInit(Seq.tabulate(param.chainingSize) { _ =>
      RegInit(false.B)
    })
    val nextTag = q.io.enq.bits.data.instructionIndex.asBools.last
    val currentTag = (dataTag.asUInt & enqOH).orR
    // same tage or nothing in queue
    canEnterQueue(queueIndex) := (nextTag === currentTag) || !p
    dataTag.zipWithIndex.foreach {case (d, i) =>
      when(q.io.deq.fire && enqOH(i)) {
        d := nextTag
      }
    }
  }

  val accessPortA: Seq[DecoupledIO[TLChannelA]] = Seq(loadUnit.tlPortA, otherUnit.tlPort.a)
  val mshrTryToUseTLAChannel: Vec[UInt] =
    WireInit(VecInit(accessPortA.map(
      p =>
        Mux(
          p.valid,
          VecInit(param.banks.map(bs => bs.region.matches(p.bits.address))).asUInt,
          0.U
        )
    )))
  mshrTryToUseTLAChannel.foreach(select => assert(PopCount(select) <= 1.U, "address overlap"))
  val sourceQueueVec: Seq[Queue[UInt]] =
    tlPort.map(_ => Module(new Queue(UInt(param.mshrParam.sourceWidth.W), param.sourceQueueSize)))
  // connect tile link a
  val readyVec: Seq[Bool] = tlPort.zipWithIndex.map { case (tl, index) =>
    val port: DecoupledIO[TLChannelA] = tl.a
    val storeRequest: DecoupledIO[TLChannelA] = storeUnit.tlPortA(index)
    val sourceQueue: Queue[UInt] = sourceQueueVec(index)
    val portFree: Bool = storeUnit.status.releasePort(index)
    val Seq(loadTryToUse, otherTryToUse) = mshrTryToUseTLAChannel.map(_(index))
    val portReady = port.ready && sourceQueue.io.enq.ready
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
    val (_, _, done, _) = param.mshrParam.fistLast(
      // todo: use param
      param.transferSize.U,
      // other no burst
      !port.bits.opcode(2) && !requestSelect(2),
      port.fire
    )

    // 选出一个请求连到 a 通道上
    val selectBits = Mux1H(requestSelect, Seq(loadUnit.tlPortA.bits, storeRequest.bits, otherUnit.tlPort.a.bits))
    port.valid := (storeRequest.valid || ((loadTryToUse || otherTryToUse) && portFree)) && sourceQueue.io.enq.ready
    port.bits := selectBits
    port.bits.source := selectIndex

    // record source id by queue
    sourceQueue.io.enq.valid := done
    sourceQueue.io.enq.bits := selectBits.source

    // 反连 ready
    storeRequest.ready := requestSelect(1) && portReady
    Seq(requestSelect.head && portReady, requestSelect.last && portReady)
  }.transpose.map(rv => VecInit(rv).asUInt.orR)
  loadUnit.tlPortA.ready := readyVec.head
  otherUnit.tlPort.a.ready := readyVec.last

  // connect tile link D
  val tlDFireForOther: Vec[Bool] = Wire(Vec(param.memoryBankSize, Bool()))
  tlPort.zipWithIndex.foldLeft(false.B) {case (o, (tl, index)) =>
    val port: DecoupledIO[TLChannelD] = tl.d
    val isAccessAck = port.bits.opcode === 0.U
    val (_, _, done, _) = param.mshrParam.fistLast(
      // todo: use param
      param.transferSize.U,
      // other no burst
      port.bits.opcode(0) && !port.bits.source(1),
      port.fire
    )
    val sourceQueue: Queue[UInt] = sourceQueueVec(index)
    sourceQueue.io.deq.ready := done
    // 0 -> load unit, 0b10 -> other unit
    val responseForOther: Bool = port.bits.source(1)
    loadUnit.tlPortD(index).valid := port.valid && !responseForOther && !isAccessAck
    loadUnit.tlPortD(index).bits := port.bits
    loadUnit.tlPortD(index).bits.source := sourceQueue.io.deq.bits
    port.ready := isAccessAck || Mux(responseForOther, !o && otherUnit.tlPort.d.ready, loadUnit.tlPortD(index).ready)
    tlDFireForOther(index) := !o && responseForOther && port.valid
    o || responseForOther
  }
  otherUnit.tlPort.d.valid := tlDFireForOther.asUInt.orR
  otherUnit.tlPort.d.bits := Mux1H(tlDFireForOther, tlPort.map(_.d.bits))
  otherUnit.tlPort.d.bits.source := Mux1H(tlDFireForOther, sourceQueueVec.map(_.io.deq.bits))

  // index offset connect
  otherUnit.offsetReadResult := offsetReadResult

  // gather last signal from all MSHR to notify LSU
  lastReport :=
    unitVec.map(m => Mux(m.status.last, indexToOH(m.status.instructionIndex, param.chainingSize), 0.U)).reduce(_ | _)
  lsuMaskGroupChange := unitVec.map(
    m => Mux(m.status.changeMaskGroup, indexToOH(m.status.instructionIndex, param.chainingSize), 0.U)
  ).reduce(_ | _)
  lsuOffsetRequest := (otherUnit.status.offsetGroupEnd | otherUnit.status.last |
    (otherUnit.status.idle && offsetReadResult.map(_.valid).reduce(_ | _))) && otherUnit.status.isIndexLS
  loadUnit.writeReadyForLsu := writeReadyForLsu
  storeUnit.vrfReadyToStore := vrfReadyToStore

  val unitOrder: Bool = instIndexL(loadUnit.status.instructionIndex, storeUnit.status.instructionIndex)
  val storeStartLargerThanLoadStart: Bool = loadUnit.status.startAddress <= storeUnit.status.startAddress
  val storeStartLessThanLoadEnd: Bool = storeUnit.status.startAddress <= loadUnit.status.endAddress
  val storeEndLargerThanLoadStart: Bool = loadUnit.status.startAddress <= storeUnit.status.endAddress
  val storeEndLessThanLoadEnd: Bool = storeUnit.status.endAddress <= loadUnit.status.endAddress

  val addressOverlap: Bool = ((storeStartLargerThanLoadStart && storeStartLessThanLoadEnd) ||
    (storeEndLargerThanLoadStart && storeEndLessThanLoadEnd))
  val stallLoad: Bool = !unitOrder && addressOverlap && !storeUnit.status.idle
  val stallStore: Bool = unitOrder && addressOverlap && !loadUnit.status.idle

  loadUnit.addressConflict := stallLoad
  storeUnit.addressConflict := stallStore
}
