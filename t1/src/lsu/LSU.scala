// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lsu

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.probe.{define, Probe, ProbeValue}
import chisel3.util._
import org.chipsalliance.amba.axi4.bundle.{AXI4BundleParameter, AXI4RWIrrevocable}
import org.chipsalliance.t1.rtl._
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}
import org.chipsalliance.t1.rtl.zvma.{ZVMA, ZVMAParameter}

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
  */
case class LSUParameter(
  datapathWidth:       Int,
  chainingSize:        Int,
  vLen:                Int,
  eLen:                Int,
  laneNumber:          Int,
  paWidth:             Int,
  sourceWidth:         Int,
  sizeWidth:           Int,
  maskWidth:           Int,
  lsuMSHRSize:         Int,
  toVRFWriteQueueSize: Int,
  transferSize:        Int,
  // TODO: refactor to per lane parameter.
  vrfReadLatency:      Int,
  axi4BundleParameter: AXI4BundleParameter,
  lsuReadShifterSize:  Seq[Int],
  name:                String,
  useXsfmm:            Boolean,
  TE:                  Int,
  matrixAluRowSize:    Int,
  matrixAluColSize: Int) {
  val sewMin: Int = 8

  val chaining1HBits: Int = 2 << log2Ceil(chainingSize)

  /** the maximum address offsets number can be accessed from lanes for one time. */
  val maxOffsetPerLaneAccess: Int = datapathWidth * laneNumber / sewMin

  /** see [[MSHRParam.maskGroupWidth]]. */
  val maskGroupWidth: Int = maxOffsetPerLaneAccess

  /** see [[VParameter.maskGroupSize]]. */
  val maskGroupSize: Int = vLen / maskGroupWidth

  /** hardware width of [[maskGroupSize]] */
  val maskGroupSizeBits: Int = log2Ceil(maskGroupSize)

  /** hardware width of [[CSRInterface.vl]]
    *
    * `+1` is for vl being 0 to vl(not vlMax - 1). we use less than for comparing, rather than less equal.
    */
  val vLenBits: Int = log2Ceil(vLen) + 1

  val sourceQueueSize: Int = 32.min(vLen * 8 / (transferSize * 8))

  def mshrParam: MSHRParam =
    MSHRParam(
      chainingSize,
      datapathWidth,
      vLen,
      eLen,
      laneNumber,
      paWidth,
      transferSize,
      lsuReadShifterSize.head,
      vrfReadLatency
    )

  /** see [[VRFParam.regNumBits]] */
  val regNumBits: Int = log2Ceil(32)

  /** see [[VParameter.instructionIndexBits]] */
  val instructionIndexBits: Int = log2Ceil(chainingSize) + 1

  /** see [[LaneParameter.singleGroupSize]] */
  val singleGroupSize: Int = vLen / datapathWidth / laneNumber

  /** see [[LaneParameter.vrfOffsetBits]] */
  val vrfOffsetBits: Int = log2Ceil(singleGroupSize)

  def zvmaExchangeParam = ZVMADataExchangeParam(
    chainingSize = chainingSize,
    datapathWidth = datapathWidth,
    vLen = vLen,
    laneNumber = laneNumber,
    paWidth = paWidth,
    lsuTransposeSize = transferSize,
    vrfReadLatency = vrfReadLatency,
    TE = TE
  )

  def zvmaParam = ZVMAParameter(
    vlen = vLen,
    dlen = datapathWidth * laneNumber,
    elen = eLen,
    TE = TE,
    matrixAluRowSize = matrixAluRowSize,
    matrixAluColSize = matrixAluColSize
  )
}

class LSUSlotProbe(param: LSUParameter) extends Bundle {
  val dataVd:          UInt = UInt(param.regNumBits.W)
  val dataOffset:      UInt = UInt(param.vrfOffsetBits.W)
  val dataMask:        UInt = UInt((param.datapathWidth / 8).W)
  val dataData:        UInt = UInt(param.datapathWidth.W)
  val dataInstruction: UInt = UInt(param.instructionIndexBits.W)
  val writeValid:      Bool = Bool()
  val targetLane:      UInt = UInt(log2Ceil(param.laneNumber).W)
}

class MemoryWriteProbe(param: MSHRParam) extends Bundle {
  val valid:   Bool = Bool()
  val data:    UInt = UInt((param.lsuTransposeSize * 8).W)
  val mask:    UInt = UInt(param.lsuTransposeSize.W)
  val index:   UInt = UInt(param.cacheLineIndexBits.W)
  val address: UInt = UInt(param.paWidth.W)
}

class LSUProbe(param: LSUParameter) extends Bundle {
  // lsu write queue enq probe
  val slots          = Vec(param.laneNumber, new LSUSlotProbe(param))
  val storeUnitProbe = new MemoryWriteProbe(param.mshrParam)
  val otherUnitProbe = new MemoryWriteProbe(param.mshrParam)
  val reqEnq:              UInt = UInt(param.lsuMSHRSize.W)
  val lsuInstructionValid: UInt = UInt(param.chaining1HBits.W)
}

/** Load Store Unit it is instantiated in [[V]], it contains
  *   - a bunch of [[MSHR]] to record outstanding memory transactions.
  *   - a crossbar to connect memory interface and each lanes.
  */
@instantiable
class LSU(param: LSUParameter) extends Module {

  /** [[LSURequest]] from Scheduler to LSU [[request.ready]] couples to [[request.bits]] to detect memory conflict.
    * There will be two cases that [[request.ready]] is false:
    *   - LSU slots is full.
    *   - memory conflict is detected.
    */
  @public
  val request: DecoupledIO[LSURequest] = IO(Flipped(Decoupled(new LSURequest(param.datapathWidth, param.chainingSize))))

  @public
  val v0UpdateVec: Vec[ValidIO[V0Update]] = IO(
    Flipped(Vec(param.laneNumber, Valid(new V0Update(param.datapathWidth, param.vrfOffsetBits))))
  )

  @public
  val axi4Port: AXI4RWIrrevocable = IO(new AXI4RWIrrevocable(param.axi4BundleParameter))

  @public
  val simpleAccessPorts: AXI4RWIrrevocable = IO(new AXI4RWIrrevocable(param.axi4BundleParameter.copy(dataWidth = 32)))

  /** read channel to [[V]], which will redirect it to [[Lane.vrf]]. [[vrfReadDataPorts.head.ready]] will be deasserted
    * if there are VRF hazards. [[vrfReadDataPorts.head.valid]] is from MSHR in LSU
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

  /** hard wire form Top. TODO: merge to [[vrfReadDataPorts]]
    */
  @public
  val vrfReadResults: Vec[ValidIO[UInt]] = IO(Vec(param.laneNumber, Flipped(Valid(UInt(param.datapathWidth.W)))))

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
  val writeRelease: Vec[Bool] = IO(Vec(param.laneNumber, Input(Bool())))

  @public
  val dataInWriteQueue: Vec[UInt] = IO(Output(Vec(param.laneNumber, UInt(param.chaining1HBits.W))))

  /** the CSR interface from [[V]], CSR will be latched in MSHR. TODO: merge to [[LSURequest]]
    */
  @public
  val csrInterface: CSRInterface = IO(Input(new CSRInterface(param.vLenBits)))

  /** offset of indexed load/store instructions. */
  @public
  val offsetReadResult: Vec[DecoupledIO[UInt]] = IO(
    Vec(param.laneNumber, Flipped(Decoupled(UInt(param.datapathWidth.W))))
  )

  /** which instruction is requesting the offset. TODO: merge to [[offsetReadResult]]
    */
  @public
  val offsetReadIndex: Vec[UInt] = IO(Input(Vec(param.laneNumber, UInt(param.instructionIndexBits.W))))

  /** interface to [[V]], indicate a MSHR slots is finished, and corresponding instruction can commit. */
  @public
  val lastReport: UInt = IO(Output(UInt(param.chaining1HBits.W)))

  @public
  val writeReadyForLsu: Bool = IO(Input(Bool()))
  @public
  val vrfReadyToStore:  Bool = IO(Input(Bool()))

  @public
  val tokenIO: LSUToken = IO(new LSUToken(param))

  @public
  val zvmaInterface: Option[ZVMInterfaceInLSU] = Option.when(param.useXsfmm) {
    IO(Input(new ZVMInterfaceInLSU(param)))
  }

  // TODO: make it D/I
  val loadUnit:  LoadUnit         = Module(new LoadUnit(param.mshrParam))
  val storeUnit: StoreUnit        = Module(new StoreUnit(param.mshrParam))
  val otherUnit: SimpleAccessUnit = Module(new SimpleAccessUnit(param.mshrParam))

  val zvmaExchange: Option[ZVMADataExchange] =
    Option.when(param.useXsfmm)(Module(new ZVMADataExchange(param.zvmaExchangeParam)))

  val zvma = Option.when(param.useXsfmm)(Module(new ZVMA(param.zvmaParam)))

  /** duplicate v0 in lsu */
  val v0: Vec[UInt] = RegInit(
    VecInit(Seq.fill(param.vLen / param.datapathWidth)(0.U(param.datapathWidth.W)))
  )

  // write v0(mask)
  v0.zipWithIndex.foreach { case (data, index) =>
    // 属于哪个lane
    val laneIndex: Int = index % param.laneNumber
    // 取出写的端口
    val v0Write = v0UpdateVec(laneIndex)
    // offset
    val offset: Int = index / param.laneNumber
    val maskExt = FillInterleaved(8, v0Write.bits.mask)
    when(v0Write.valid && v0Write.bits.offset === offset.U) {
      data := (data & (~maskExt).asUInt) | (maskExt & v0Write.bits.data)
    }
  }

  val unitVec = Seq(loadUnit, storeUnit, otherUnit)

  /** Always merge into cache line */
  val alwaysMerge:  Bool = (
    request.bits.instructionInformation.mop ##
      // unit stride & whole register
      request.bits.instructionInformation.lumop(2, 0) ##
      request.bits.instructionInformation.lumop(4)
  ) === 0.U
  val useLoadUnit:  Bool = alwaysMerge && !request.bits.instructionInformation.isStore
  val useStoreUnit: Bool = alwaysMerge && request.bits.instructionInformation.isStore
  val useOtherUnit: Bool = !alwaysMerge
  val addressCheck: Bool = otherUnit.status.idle && (!useOtherUnit || (loadUnit.status.idle && storeUnit.status.idle))
  val unitReady:    Bool =
    (useLoadUnit && loadUnit.status.idle) || (useStoreUnit && storeUnit.status.idle) || (useOtherUnit && otherUnit.status.idle)
  val zvmaReady = zvmaInterface.map(i =>
    loadUnit.status.idle && storeUnit.status.idle &&
      otherUnit.status.idle && zvmaExchange.get.io.idle && i.isZVMA && zvma.get.io.idle
  )
  val zvmaFree  = zvmaExchange.map(z => z.io.idle && zvma.get.io.idle)
  request.ready := unitReady && addressCheck && zvmaFree.getOrElse(true.B) || zvmaReady.getOrElse(false.B)
  val requestFire = request.fire
  val unitFire: Bool      = zvmaInterface.map(z => !z.isZVMA && requestFire).getOrElse(requestFire)
  val reqEnq:   Vec[Bool] = VecInit(
    Seq(useLoadUnit && unitFire, useStoreUnit && unitFire, useOtherUnit && unitFire)
  )
  val zvmaLastReport = zvmaExchange.map { z =>
    val idle     = z.io.idle && zvma.get.io.idle
    val idleNext = RegNext(idle, false.B)
    Mux(!idleNext && idle, indexToOH(z.io.instructionIndex, param.chainingSize), 0.U)
  }.getOrElse(0.U((2 * param.chainingSize).W))

  zvmaExchange.foreach { z =>
    val interface = zvmaInterface.get
    val zModule   = zvma.get

    z.io.clock := clock
    z.io.reset := reset

    zModule.io.clock := clock
    zModule.io.reset := reset

    z.io.instRequest.valid                 := requestFire && interface.isZVMA
    z.io.instRequest.bits.inst             := interface.inst
    z.io.instRequest.bits.instructionIndex := request.bits.instructionIndex
    z.io.instRequest.bits.address          := request.bits.rs1Data
    z.io.csrInterface                      := csrInterface

    zModule.io.request.valid            := requestFire && interface.isZVMA
    zModule.io.request.bits.instruction := interface.inst
    zModule.io.request.bits.scalaSource := Mux(interface.inst(6), request.bits.rs1Data, request.bits.rs2Data)
    zModule.io.dataFromLSU <> z.io.datatoZVMA
    z.io.dataFromZVMA <> zModule.io.dataToLSU

    zModule.io.request.bits.csr.sew := csrInterface.vSew
    zModule.io.request.bits.csr.tew := csrInterface.tew
    zModule.io.request.bits.csr.tk  := csrInterface.tk
    zModule.io.request.bits.csr.tm  := csrInterface.tm
    zModule.io.request.bits.csr.tn  := csrInterface.vl
  }

  unitVec.zipWithIndex.foreach { case (mshr, index) =>
    mshr.lsuRequest.valid := reqEnq(index)
    mshr.lsuRequest.bits  := request.bits

    val maskSelect = Mux(mshr.maskSelect.valid, mshr.maskSelect.bits, 0.U)
    mshr.maskInput := cutUInt(v0.asUInt, param.maskGroupWidth)(maskSelect)

    // broadcast CSR
    mshr.csrInterface := csrInterface
  }

  /** TileLink D Channel write to VRF queue: TL-D -CrossBar-> MSHR -proxy-> write queue -CrossBar-> VRF
    */
  val writeQueueVec: Seq[QueueIO[LSUWriteQueueBundle]] = Seq.fill(param.laneNumber)(
    Queue.io(new LSUWriteQueueBundle(param), param.toVRFWriteQueueSize, flow = true)
  )

  @public
  val lsuProbe = IO(Output(Probe(new LSUProbe(param), layers.Verification)))

  // todo: require all shifter same as head
  val readLatency:           Int                = param.vrfReadLatency + param.lsuReadShifterSize.head * 2
  val otherUnitTargetQueue:  QueueIO[UInt]      = Queue.io(UInt(param.laneNumber.W), 2 * readLatency, pipe = true)
  val otherUnitDataQueueVec: Seq[QueueIO[UInt]] = Seq.fill(param.laneNumber)(
    Queue.io(UInt(param.datapathWidth.W), readLatency, flow = true)
  )
  val dataDeqFire:           UInt               = Wire(UInt(param.laneNumber.W))
  val zvmaWaitVrfReadVec = Seq.tabulate(param.laneNumber)(_ => Option.when(param.useXsfmm)(Wire(Bool())))
  val zvmaWaitVrfRead    = zvmaWaitVrfReadVec.map(_.getOrElse(false.B))
  // read vrf
  val otherTryReadVrf: UInt = Mux(otherUnit.vrfReadDataPorts.valid, otherUnit.status.targetLane, 0.U)
  vrfReadDataPorts.zipWithIndex.foreach { case (read, index) =>
    read.valid                              := otherTryReadVrf(index) || storeUnit.vrfReadDataPorts(index).valid
    read.bits                               := Mux(otherTryReadVrf(index), otherUnit.vrfReadDataPorts.bits, storeUnit.vrfReadDataPorts(index).bits)
    storeUnit.vrfReadDataPorts(index).ready := read.ready && !otherTryReadVrf(index)
    storeUnit.vrfReadResults(index)         := vrfReadResults(index)
    storeUnit.vrfReadResults(index).valid   := vrfReadResults(
      index
    ).valid && otherUnitTargetQueue.empty && !zvmaWaitVrfRead(index)

    val otherUnitQueue: QueueIO[UInt] = otherUnitDataQueueVec(index)
    otherUnitQueue.enq.valid := vrfReadResults(index).valid && !otherUnitTargetQueue.empty
    otherUnitQueue.enq.bits  := vrfReadResults(index).bits
    otherUnitQueue.deq.ready := dataDeqFire(index)
  }
  otherUnit.vrfReadDataPorts.ready := (otherTryReadVrf & VecInit(vrfReadDataPorts.map(_.ready)).asUInt).orR &&
    otherUnitTargetQueue.enq.ready
  otherUnitTargetQueue.enq.bits  := otherUnit.status.targetLane
  otherUnitTargetQueue.enq.valid := otherUnit.vrfReadDataPorts.fire

  // read data reorder
  otherUnit.vrfReadResults.bits  := Mux1H(otherUnitTargetQueue.deq.bits, otherUnitDataQueueVec.map(_.deq.bits))
  otherUnit.vrfReadResults.valid := otherUnitTargetQueue.deq.valid && !VecInit(zvmaWaitVrfRead).asUInt.orR &&
    (otherUnitTargetQueue.deq.bits & VecInit(otherUnitDataQueueVec.map(_.deq.valid)).asUInt).orR
  dataDeqFire                    := maskAnd(otherUnit.vrfReadResults.valid, otherUnitTargetQueue.deq.bits)
  otherUnitTargetQueue.deq.ready := otherUnit.vrfReadResults.valid

  // write vrf
  val otherTryToWrite: UInt = Mux(otherUnit.vrfWritePort.valid, otherUnit.status.targetLane, 0.U)
  // other 优先级更高
  otherUnit.vrfWritePort.ready := (otherUnit.status.targetLane & VecInit(writeQueueVec.map(_.enq.ready)).asUInt).orR
  writeQueueVec.zipWithIndex.foreach { case (write, index) =>
    write.enq.valid                    := otherTryToWrite(index) || loadUnit.vrfWritePort(index).valid
    write.enq.bits.data                := Mux(
      otherTryToWrite(index),
      otherUnit.vrfWritePort.bits,
      loadUnit.vrfWritePort(index).bits
    )
    write.enq.bits.targetLane          := (BigInt(1) << index).U
    loadUnit.vrfWritePort(index).ready := write.enq.ready && !otherTryToWrite(index)
  }

  zvmaExchange.foreach { z =>
    // zvma <> vrf read
    z.io.vrfReadDataPorts.zipWithIndex.foreach { case (zrp, ri) =>
      val vrfReadPort    = vrfReadDataPorts(ri)
      val vrfReadResult  = vrfReadResults(ri)
      val vrfReadCount   = RegInit(0.U(8.W))
      val waitReadResult = vrfReadCount.orR
      when(zrp.fire ^ vrfReadResult.fire && (zrp.fire || waitReadResult)) {
        vrfReadCount := vrfReadCount + Mux(zrp.fire, 1.U, -1.S(8.W).asUInt)
      }
      when(zrp.valid) {
        vrfReadPort.valid := true.B
        vrfReadPort.bits  := zrp.bits
      }
      zrp.ready := vrfReadPort.ready
      zvmaWaitVrfReadVec(ri).get    := waitReadResult
      z.io.vrfReadResults(ri).valid := vrfReadResult.valid && waitReadResult
      z.io.vrfReadResults(ri).bits  := vrfReadResult.bits
    }
    // zvma <> vrf write
    z.io.vrfWritePort.zipWithIndex.foreach { case (zwp, wi) =>
      val writeQueue = writeQueueVec(wi)
      when(zwp.valid) {
        writeQueue.enq.valid           := true.B
        writeQueue.enq.bits.data       := zwp.bits
        writeQueue.enq.bits.targetLane := (BigInt(1) << wi).U
      }
      zwp.ready := writeQueue.enq.ready
    }
  }

  layer.block(layers.Verification) {
    val probeWire = Wire(new LSUProbe(param))
    define(lsuProbe, ProbeValue(probeWire))
    writeQueueVec.zipWithIndex.foreach { case (write, index) =>
      probeWire.slots(index).dataVd          := write.enq.bits.data.vd
      probeWire.slots(index).dataOffset      := write.enq.bits.data.offset
      probeWire.slots(index).dataMask        := write.enq.bits.data.mask
      probeWire.slots(index).dataData        := write.enq.bits.data.data
      probeWire.slots(index).dataInstruction := write.enq.bits.data.instructionIndex
      probeWire.slots(index).writeValid      := write.enq.fire
      probeWire.slots(index).targetLane      := OHToUInt(write.enq.bits.targetLane)
    }
    probeWire.reqEnq := reqEnq.asUInt

    probeWire.storeUnitProbe      := probe.read(storeUnit.probe)
    probeWire.otherUnitProbe      := probe.read(otherUnit.probe)
    probeWire.lsuInstructionValid :=
      // The load unit becomes idle when it writes vrf for the last time.
      maskAnd(
        !loadUnit.status.idle || VecInit(loadUnit.vrfWritePort.map(_.valid)).asUInt.orR,
        indexToOH(loadUnit.status.instructionIndex, param.chainingSize)
      ).asUInt |
        maskAnd(!storeUnit.status.idle, indexToOH(storeUnit.status.instructionIndex, param.chainingSize)).asUInt |
        maskAnd(!otherUnit.status.idle, indexToOH(otherUnit.status.instructionIndex, param.chainingSize)).asUInt
  }

  vrfWritePort.zip(writeQueueVec).foreach { case (p, q) =>
    p.valid     := q.deq.valid
    p.bits      := q.deq.bits.data
    q.deq.ready := p.ready
  }

  val dataInMSHR: UInt =
    Mux(
      loadUnit.status.idle,
      0.U(param.chaining1HBits.W),
      indexToOH(loadUnit.status.instructionIndex, param.chainingSize)
    ) |
      Mux(
        otherUnit.status.idle || otherUnit.status.isStore,
        0.U(param.chaining1HBits.W),
        indexToOH(otherUnit.status.instructionIndex, param.chainingSize)
      )

  // Record whether there is data for the corresponding instruction in the queue
  writeQueueVec.zip(dataInWriteQueue).zipWithIndex.foreach { case ((q, p), i) =>
    val queueCount: Seq[UInt] = Seq.tabulate(param.chaining1HBits) { _ =>
      RegInit(0.U(log2Ceil(param.toVRFWriteQueueSize).W))
    }
    val enqOH:      UInt      = indexToOH(q.enq.bits.data.instructionIndex, param.chainingSize)
    val queueEnq:   UInt      = Mux(q.enq.fire, enqOH, 0.U)

    val writeTokenSize  = 8
    val writeIndexQueue = Queue.io(UInt(param.instructionIndexBits.W), writeTokenSize)
    writeIndexQueue.enq.valid := q.deq.fire
    writeIndexQueue.enq.bits  := q.deq.bits.data.instructionIndex
    writeIndexQueue.deq.ready := writeRelease(i)

    val queueDeq = Mux(writeIndexQueue.deq.fire, indexToOH(writeIndexQueue.deq.bits, param.chainingSize), 0.U)
    queueCount.zipWithIndex.foreach { case (count, index) =>
      val counterUpdate: UInt = Mux(queueEnq(index), 1.U, -1.S(log2Ceil(param.toVRFWriteQueueSize).W).asUInt)
      when(queueEnq(index) ^ queueDeq(index)) {
        count := count + counterUpdate
      }
    }
    p := VecInit(queueCount.map(_ =/= 0.U)).asUInt | dataInMSHR
  }

  val sourceQueue = Queue.io(UInt(param.mshrParam.cacheLineIndexBits.W), param.sourceQueueSize)
  val zvmUseAr    = Option.when(param.useXsfmm)(Wire(Bool()))
  val zvmWaitR    = Option.when(param.useXsfmm)(Wire(Bool()))
  // load unit connect
  axi4Port.ar.valid         := loadUnit.memRequest.valid && sourceQueue.enq.ready || zvmUseAr.getOrElse(false.B)
  axi4Port.ar.bits <> DontCare
  axi4Port.ar.bits.addr     := loadUnit.memRequest.bits.address
  axi4Port.ar.bits.len      := 0.U
  axi4Port.ar.bits.size     := param.mshrParam.cacheLineBits.U
  axi4Port.ar.bits.burst    := 1.U // INCR
  loadUnit.memRequest.ready := sourceQueue.enq.ready && axi4Port.ar.ready

  loadUnit.memResponse.valid      := axi4Port.r.valid && !zvmWaitR.getOrElse(false.B)
  loadUnit.memResponse.bits.data  := axi4Port.r.bits.data
  loadUnit.memResponse.bits.index := sourceQueue.deq.bits
  axi4Port.r.ready                := loadUnit.memResponse.ready

  sourceQueue.enq.valid := loadUnit.memRequest.valid && axi4Port.ar.ready
  sourceQueue.enq.bits  := loadUnit.memRequest.bits.src
  sourceQueue.deq.ready := axi4Port.r.fire

  // store unit <> axi
  val dataQueue: QueueIO[MemWrite] = Queue.io(chiselTypeOf(storeUnit.memRequest.bits), 2)
  axi4Port.aw.valid          := storeUnit.memRequest.valid && dataQueue.enq.ready
  axi4Port.aw.bits <> DontCare
  axi4Port.aw.bits.len       := 0.U
  axi4Port.aw.bits.burst     := 1.U // INCR
  axi4Port.aw.bits.size      := param.mshrParam.cacheLineBits.U
  axi4Port.aw.bits.addr      := storeUnit.memRequest.bits.address
  axi4Port.aw.bits.id        := storeUnit.memRequest.bits.index
  storeUnit.memRequest.ready := axi4Port.aw.ready && dataQueue.enq.ready

  dataQueue.enq.valid := storeUnit.memRequest.valid && axi4Port.aw.ready
  dataQueue.enq.bits  := storeUnit.memRequest.bits

  axi4Port.w.valid     := dataQueue.deq.valid
  axi4Port.w.bits <> DontCare
  axi4Port.w.bits.data := dataQueue.deq.bits.data
  axi4Port.w.bits.strb := dataQueue.deq.bits.mask
  axi4Port.w.bits.last := true.B
  dataQueue.deq.ready  := axi4Port.w.ready

  // todo: add write token ?
  axi4Port.b.ready          := true.B
  storeUnit.storeResponse   := axi4Port.b.valid
  simpleAccessPorts.b.ready := true.B

  zvmaExchange.foreach { z =>
    // zvma <> axi read
    val tryToRead  = z.io.memRequest.valid && !z.io.memRequest.bits.write
    val tryToWrite = z.io.memRequest.valid && !z.io.memRequest.bits.write
    val read       = z.io.memRequest.fire && !z.io.memRequest.bits.write
    // todo: There is no coexistence with load unit yet.
    zvmUseAr.get := tryToRead
    // todo
    val outReadCount   = RegInit(0.U(8.W))
    val waitReadResult = outReadCount.orR
    when(read ^ axi4Port.r.fire && (read || waitReadResult)) {
      outReadCount := outReadCount + Mux(read, 1.U, -1.S(8.W).asUInt)
    }
    z.io.memRequest.ready := Mux(tryToRead, axi4Port.ar.ready, axi4Port.w.ready && axi4Port.aw.ready)
    when(tryToRead) {
      axi4Port.ar.bits.addr     := z.io.memRequest.bits.address
      loadUnit.memRequest.ready := sourceQueue.enq.ready && axi4Port.ar.ready
    }
    z.io.memResponse.valid := axi4Port.r.valid && waitReadResult
    z.io.memResponse.bits := axi4Port.r.bits.data
    zvmWaitR.get          := waitReadResult
    when(tryToWrite) {
      axi4Port.aw.bits.addr := z.io.memRequest.bits.address
      axi4Port.w.bits.data  := z.io.memRequest.bits.data
      axi4Port.w.bits.strb  := z.io.memRequest.bits.mask
    }

  }

  // other unit <> axi
  val simpleSourceQueue: QueueIO[UInt] =
    Queue.io(UInt(param.mshrParam.sourceWidth.W), param.sourceQueueSize)
  simpleAccessPorts.ar.valid      := otherUnit.memReadRequest.valid && simpleSourceQueue.enq.ready
  simpleAccessPorts.ar.bits <> DontCare
  simpleAccessPorts.ar.bits.addr  := otherUnit.memReadRequest.bits.address
  simpleAccessPorts.ar.bits.len   := 0.U
  simpleAccessPorts.ar.bits.size  := otherUnit.memReadRequest.bits.size
  simpleAccessPorts.ar.bits.burst := 1.U // INCR
  otherUnit.memReadRequest.ready  := simpleSourceQueue.enq.ready && simpleAccessPorts.ar.ready

  otherUnit.memReadResponse.valid       := simpleAccessPorts.r.valid
  otherUnit.memReadResponse.bits.data   := simpleAccessPorts.r.bits.data
  otherUnit.memReadResponse.bits.source := simpleSourceQueue.deq.bits
  simpleAccessPorts.r.ready             := otherUnit.memReadResponse.ready

  simpleSourceQueue.enq.valid := otherUnit.memReadRequest.valid && simpleAccessPorts.ar.ready
  simpleSourceQueue.enq.bits  := otherUnit.memReadRequest.bits.source
  simpleSourceQueue.deq.ready := simpleAccessPorts.r.fire

  val simpleDataQueue: QueueIO[SimpleMemWrite] =
    Queue.io(chiselTypeOf(otherUnit.memWriteRequest.bits), 2)
  simpleAccessPorts.aw.valid      := otherUnit.memWriteRequest.valid && dataQueue.enq.ready
  simpleAccessPorts.aw.bits <> DontCare
  simpleAccessPorts.aw.bits.len   := 0.U
  simpleAccessPorts.aw.bits.burst := 1.U // INCR
  simpleAccessPorts.aw.bits.size  := otherUnit.memWriteRequest.bits.size
  simpleAccessPorts.aw.bits.addr  := otherUnit.memWriteRequest.bits.address
  simpleAccessPorts.aw.bits.id    := otherUnit.memWriteRequest.bits.source
  otherUnit.memWriteRequest.ready := simpleAccessPorts.aw.ready && simpleDataQueue.enq.ready

  simpleDataQueue.enq.valid := otherUnit.memWriteRequest.valid && simpleAccessPorts.aw.ready
  simpleDataQueue.enq.bits  := otherUnit.memWriteRequest.bits

  simpleAccessPorts.w.valid     := simpleDataQueue.deq.valid
  simpleAccessPorts.w.bits <> DontCare
  simpleAccessPorts.w.bits.data := simpleDataQueue.deq.bits.data
  simpleAccessPorts.w.bits.strb := simpleDataQueue.deq.bits.mask
  simpleAccessPorts.w.bits.last := true.B
  simpleDataQueue.deq.ready     := simpleAccessPorts.w.ready

  otherUnit.offsetReadResult <> offsetReadResult

  // gather last signal from all MSHR to notify LSU
  lastReport                 :=
    unitVec.map(m => Mux(m.status.last, indexToOH(m.status.instructionIndex, param.chainingSize), 0.U)).reduce(_ | _) |
      zvmaLastReport
  tokenIO.offsetGroupRelease := otherUnit.offsetRelease.asUInt

  val unitOrder:            Bool = instIndexLE(loadUnit.status.instructionIndex, storeUnit.status.instructionIndex)
  val loadAddressConflict:  Bool = (loadUnit.status.startAddress >= storeUnit.status.startAddress) &&
    (loadUnit.status.startAddress <= storeUnit.status.endAddress)
  val storeAddressConflict: Bool = (storeUnit.status.startAddress >= loadUnit.status.startAddress) &&
    (storeUnit.status.startAddress <= loadUnit.status.endAddress)

  val stallLoad:  Bool = !unitOrder && loadAddressConflict && !storeUnit.status.idle
  val stallStore: Bool = unitOrder && storeAddressConflict && !loadUnit.status.idle

  loadUnit.addressConflict  := stallLoad
  storeUnit.addressConflict := stallStore
}
