// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter, SourceInfo}
import chisel3.properties.{AnyClassType, ClassType, Property}
import chisel3.util.experimental.{BitSet, InlineInstance}
import chisel3.util.{
  isPow2,
  log2Ceil,
  Arbiter,
  BitPat,
  Cat,
  Enum,
  Fill,
  FillInterleaved,
  Mux1H,
  MuxLookup,
  OHToUInt,
  PriorityEncoder,
  PriorityEncoderOH,
  PriorityMux,
  RegEnable,
  SRAM,
  SRAMInterface,
  UIntToOH
}
import org.chipsalliance.amba.axi4.bundle.{
  AR,
  AW,
  AXI4BundleParameter,
  AXI4ChiselBundle,
  AXI4ROIrrevocable,
  AXI4RWIrrevocable,
  R,
  W
}
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}
import org.chipsalliance.stdlib.GeneralOM

object HellaCacheParameter {
  implicit def bitSetP: upickle.default.ReadWriter[BitSet] = upickle.default
    .readwriter[String]
    .bimap[BitSet](
      bs => bs.terms.map("b" + _.rawString).mkString("\n"),
      str => if (str.isEmpty) BitSet.empty else BitSet.fromString(str)
    )

  implicit def rwP: upickle.default.ReadWriter[HellaCacheParameter] = upickle.default.macroRW[HellaCacheParameter]
}

case class HellaCacheParameter(
  useAsyncReset:        Boolean,
  clockGate:            Boolean,
  xLen:                 Int,
  fLen:                 Int,
  usingVM:              Boolean,
  paddrBits:            Int,
  cacheBlockBytes:      Int,
  nWays:                Int,
  nSets:                Int,
  rowBits:              Int,
  nTLBSets:             Int,
  nTLBWays:             Int,
  tagECC:               Option[String],
  dataECC:              Option[String],
  maxUncachedInFlight:  Int,
  separateUncachedResp: Boolean,
  legal:                BitSet,
  cacheable:            BitSet,
  read:                 BitSet,
  write:                BitSet,
  putPartial:           BitSet,
  logic:                BitSet,
  arithmetic:           BitSet,
  exec:                 BitSet,
  sideEffects:          BitSet)
    extends SerializableModuleParameter {

  def vpnBitsExtended: Int = vpnBits + (if (vaddrBits < xLen) (if (usingHypervisor) 1 else 0) + 1 else 0)

  def vaddrBitsExtended: Int = vpnBitsExtended + pgIdxBits

  def maxSVAddrBits: Int = pgIdxBits + pgLevels * pgLevelBits

  def maxHypervisorExtraAddrBits: Int = 2

  def hypervisorExtraAddrBits: Int = if (usingHypervisor) maxHypervisorExtraAddrBits else 0

  def maxHVAddrBits: Int = maxSVAddrBits + hypervisorExtraAddrBits

  def vaddrBits:        Int = if (usingVM) {
    val v = maxHVAddrBits
    require(v == xLen || xLen > v && v > paddrBits)
    v
  } else {
    // since virtual addresses sign-extend but physical addresses
    // zero-extend, make room for a zero sign bit for physical addresses
    (paddrBits + 1).min(xLen)
  }
  // static for now
  def dcacheReqTagBits: Int = 6

  def usingHypervisor = false

  def scratch: Option[BigInt] = None

  def acquireBeforeRelease: Boolean = false

  def replacementPolicy: String = "random" // lfsr

  def usingAtomics: Boolean = true

  def useAtomicsOnlyForIO: Boolean = false

  def flushOnFenceI: Boolean = true

  def useVector: Boolean = false

  def haveCFlush: Boolean = false

  def subWordBits: Option[Int] = None

  // calculated
  def pgIdxBits: Int = 12

  def lgCacheBlockBytes: Int = log2Ceil(cacheBlockBytes)

  def blockOffBits: Int = lgCacheBlockBytes

  def idxBits: Int = log2Ceil(nSets)

  def untagBits: Int = blockOffBits + idxBits

  def coreMaxAddrBits: Int = paddrBits.max(vaddrBitsExtended)

  def usingDataScratchpad: Boolean = scratch.isDefined

  def dcacheArbPorts: Int = 1 + (if (usingVM) 1 else 0) + (if (usingDataScratchpad) 1 else 0)

  def tagCode: Code = Code.fromString(tagECC)

  def dataCode: Code = Code.fromString(dataECC)

  def pgLevelBits: Int = 10 - log2Ceil(xLen / 32)

  def pipelineWayMux: Boolean = false

  def nPMPs: Int = 8

  def vpnBits: Int = vaddrBits - pgIdxBits

  def hasCorrectable: Boolean = tagCode.canCorrect || dataCode.canCorrect

  def hasUncorrectable: Boolean = tagCode.canDetect || dataCode.canDetect

  def pgLevels: Int = xLen match {
    case 32 => 2
    case 64 => 3
  }

  /* Sv32 */
  val maxPAddrBits: Int = xLen match {
    case 32 => 34
    case 64 => 56
  }

  def coreDataBits: Int = xLen.max(fLen)

  def coreDataBytes: Int = coreDataBits / 8

  def silentDrop: Boolean = !acquireBeforeRelease

  def idxMSB: Int = untagBits - 1

  def idxLSB: Int = blockOffBits

  def wordBits: Int = coreDataBits

  def rowWords: Int = rowBits / wordBits

  def wordBytes: Int = coreDataBytes

  def wordOffBits: Int = log2Ceil(wordBytes)

  def cacheDataBits: Int = rowBits

  def cacheDataBeats: Int = (cacheBlockBytes * 8) / cacheDataBits

  def beatBytes: Int = cacheBlockBytes / cacheDataBeats

  def beatWords: Int = beatBytes / wordBytes

  def dataECCBytes: Int = 1

  def eccBits: Int = dataECCBytes * 8

  def eccBytes: Int = dataECCBytes

  def encBits: Int = dataCode.width(eccBits)

  def rowBytes: Int = rowBits / 8

  def subWordBytes: Int = subWordBits.getOrElse(wordBits) / 8

  def rowOffBits: Int = log2Ceil(rowBytes)

  def beatOffBits: Int = log2Ceil(beatBytes)

  def usingAtomicsInCache: Boolean = usingAtomics && !useAtomicsOnlyForIO

  def pgUntagBits: Int = if (usingVM) untagBits.min(pgIdxBits) else untagBits

  def tagBits: Int = paddrBits - pgUntagBits

  // todo: max axi id
  def firstMMIO: Int = 4

  def lrscBackoff: Int = 3

  def lrscCycles: Int = 80 // worst case is 14 mispredicted branches + slop

  def pmaCheckerParameter: PMACheckerParameter =
    PMACheckerParameter(paddrBits, legal, cacheable, read, write, putPartial, logic, arithmetic, exec, sideEffects)

  def tlbParameter: TLBParameter = TLBParameter(
    useAsyncReset,
    xLen,
    nTLBSets,
    nTLBWays,
    nSectors = 4,
    nSuperpageEntries = 4,
    asidBits = 0,
    pgLevels,
    usingHypervisor = false,
    usingAtomics,
    usingDataScratchpad,
    useAtomicsOnlyForIO,
    usingVM,
    usingAtomicsInCache,
    nPMPs,
    pmaCheckerParameter,
    paddrBits,
    isITLB = false
  )

  def amoaluParameter: Option[AMOALUParameter] = Option.when(eccBytes > 1 || usingAtomicsInCache)(AMOALUParameter(xLen))

  def dtimParameter: Option[AXI4BundleParameter] = scratch.map { _ =>
    AXI4BundleParameter(
      idWidth = 1,
      dataWidth = rowBits,
      addrWidth = paddrBits,
      userReqWidth = 0,
      userDataWidth = 0,
      userRespWidth = 0,
      hasAW = true,
      hasW = true,
      hasB = true,
      hasAR = true,
      hasR = true,
      supportId = true,
      supportRegion = false,
      supportLen = true,
      supportSize = true,
      supportBurst = true,
      supportLock = false,
      supportCache = false,
      supportQos = false,
      supportStrb = false,
      supportResp = false,
      supportProt = false
    )
  }

  def loadStoreParameter: AXI4BundleParameter = AXI4BundleParameter(
    idWidth = log2Ceil(firstMMIO + maxUncachedInFlight),
    dataWidth = rowBits,
    addrWidth = paddrBits,
    userReqWidth = 0,
    userDataWidth = 0,
    userRespWidth = 0,
    hasAW = true,
    hasW = true,
    hasB = true,
    hasAR = true,
    hasR = true,
    supportId = true,
    supportRegion = false,
    supportLen = true,
    supportSize = true,
    supportBurst = true,
    supportLock = false,
    supportCache = false,
    supportQos = false,
    supportStrb = false,
    supportResp = false,
    supportProt = false
  )
}

class HellaCacheInterface(parameter: HellaCacheParameter) extends Bundle {
  val clock  = Input(Clock())
  val reset  = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val cpu    = Flipped(
    new HellaCacheIO(
      parameter.coreMaxAddrBits,
      parameter.usingVM,
      parameter.untagBits,
      parameter.pgIdxBits,
      parameter.dcacheReqTagBits,
      parameter.dcacheArbPorts,
      parameter.coreDataBytes,
      parameter.paddrBits,
      parameter.vaddrBitsExtended,
      parameter.separateUncachedResp
    )
  )
  val ptw    = new TLBPTWIO(
    parameter.nPMPs,
    parameter.vpnBits,
    parameter.paddrBits,
    parameter.vaddrBits,
    parameter.pgLevels,
    parameter.xLen,
    parameter.maxPAddrBits,
    parameter.pgIdxBits
  )
  val errors = new DCacheErrors(parameter.hasCorrectable, parameter.hasUncorrectable, parameter.paddrBits)
  val loadStoreAXI: AXI4RWIrrevocable         =
    org.chipsalliance.amba.axi4.bundle.AXI4RWIrrevocable(parameter.loadStoreParameter)
  val dtimAXI:      Option[AXI4RWIrrevocable] =
    parameter.dtimParameter.map(p => Flipped(org.chipsalliance.amba.axi4.bundle.AXI4RWIrrevocable(p)))
  val om:           Property[ClassType]       = Output(Property[AnyClassType]())
}

@instantiable
class HellaCacheOM(parameter: HellaCacheParameter) extends GeneralOM[HellaCacheParameter, HellaCache](parameter) {
  override def hasSram: Boolean = true
}

@instantiable
class HellaCache(val parameter: HellaCacheParameter)
    extends FixedIORawModule(new HellaCacheInterface(parameter))
    with SerializableModule[HellaCacheParameter]
    with Public
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock                         = io.clock
  override protected def implicitReset: Reset                         = io.reset
  // instantiate sub hierarchies
  val tlb:                              Instance[TLB]                 = Instantiate(new TLB(parameter.tlbParameter))
  val pmaChecker:                       Instance[PMAChecker]          = Instantiate(new PMAChecker(parameter.pmaCheckerParameter))
  val amoalus:                          Option[Seq[Instance[AMOALU]]] = parameter.amoaluParameter.map(amoaluParameter =>
    Seq.tabulate(parameter.coreDataBits / parameter.xLen)(i => Instantiate(new AMOALU(amoaluParameter)))
  )
  val omInstance:                       Instance[HellaCacheOM]        = Instantiate(new HellaCacheOM(parameter))
  io.om := omInstance.getPropertyReference.asAnyClassType

  tlb.io.clock := io.clock
  tlb.io.reset := io.reset

  // compatibility layers
  object cacheParams {
    def tagCode:              Code    = parameter.tagCode
    def dataCode:             Code    = parameter.dataCode
    def silentDrop:           Boolean = parameter.silentDrop
    def acquireBeforeRelease: Boolean = parameter.acquireBeforeRelease
    def clockGate:            Boolean = parameter.clockGate
    def replacementPolicy:    String  = parameter.replacementPolicy
    def separateUncachedResp: Boolean = parameter.separateUncachedResp
    def pipelineWayMux:       Boolean = parameter.pipelineWayMux
  }
  def rowWords: Int = parameter.rowWords
  def wordOffBits:                Int     = parameter.wordOffBits
  def beatWords:                  Int     = parameter.beatWords
  def beatBytes:                  Int     = parameter.beatBytes
  def idxMSB:                     Int     = parameter.idxMSB
  def idxLSB:                     Int     = parameter.idxLSB
  def subWordBits:                Int     = parameter.subWordBits.getOrElse(parameter.wordBits)
  def eccBits:                    Int     = parameter.eccBits
  def eccBytes:                   Int     = parameter.eccBytes
  def coreMaxAddrBits:            Int     = parameter.coreMaxAddrBits
  def usingVM:                    Boolean = parameter.usingVM
  def pgIdxBits:                  Int     = parameter.pgIdxBits
  def pgLevelBits:                Int     = parameter.pgLevelBits
  def dcacheReqTagBits:           Int     = parameter.dcacheReqTagBits
  def dcacheArbPorts:             Int     = parameter.dcacheArbPorts
  def coreDataBytes:              Int     = parameter.coreDataBytes
  def encBits:                    Int     = parameter.encBits
  def untagBits:                  Int     = parameter.untagBits
  def rowBytes:                   Int     = parameter.rowBytes
  def subWordBytes:               Int     = parameter.subWordBytes
  def rowOffBits:                 Int     = parameter.rowOffBits
  def beatOffBits:                Int     = parameter.beatOffBits
  def wordBytes:                  Int     = parameter.wordBytes
  def usingAtomicsInCache:        Boolean = parameter.usingAtomicsInCache
  def nWays:                      Int     = parameter.nWays
  def nSets:                      Int     = parameter.nSets
  def cacheBlockBytes:            Int     = parameter.cacheBlockBytes
  def vaddrBitsExtended:          Int     = parameter.vaddrBitsExtended
  def paddrBits:                  Int     = parameter.paddrBits
  def maxUncachedInFlight:        Int     = parameter.maxUncachedInFlight
  def tagBits:                    Int     = parameter.tagBits
  def idxBits:                    Int     = parameter.idxBits
  def blockOffBits:               Int     = parameter.blockOffBits
  def usingDataScratchpad:        Boolean = parameter.usingDataScratchpad
  def usingAtomics:               Boolean = parameter.usingAtomics
  def lrscBackoff:                Int     = parameter.lrscBackoff
  def lrscCycles:                 Int     = parameter.lrscCycles
  def rowBits:                    Int     = parameter.rowBits
  def cacheDataBits:              Int     = parameter.rowBits
  def cacheDataBytes:             Int     = cacheDataBits / 8
  def cacheDataBeats:             Int     = (cacheBlockBytes * 8) / cacheDataBits
  def refillCycles:               Int     = cacheDataBeats
  def blockProbeAfterGrantCycles: Int     = 8
  def wordBits:                   Int     = parameter.coreDataBits
  object outer          {
    def firstMMIO     = parameter.firstMMIO
    def flushOnFenceI = parameter.flushOnFenceI
  }
  object coreParams     {
    def useVector  = parameter.useVector
    def haveCFlush = parameter.haveCFlush
  }
  object ClientMetadata {
    def isValid(cm: ClientMetadata): Bool = cm.state > 0.U

    def apply(perm: UInt): ClientMetadata = {
      val meta = Wire(new ClientMetadata)
      meta.state := perm
      meta
    }
  }
  object L1Metadata     {
    def apply(tag: Bits, coh: ClientMetadata) = {
      val meta = Wire(new L1Metadata(parameter.tagBits))
      meta.tag := tag
      meta.coh := coh
      meta
    }
  }
  def M_SFENCE = "b10100".U // SFENCE.VMA
  def M_HFENCEV   = "b10101".U  // HFENCE.VVMA
  def M_HFENCEG   = "b10110".U  // HFENCE.GVMA
  def M_FLUSH_ALL = "b00101".U  // flush all lines
  def M_WOK       = "b10111".U  // check write permissions but don't perform a write
  def M_PWR       = "b10001".U  // partial (masked) store
  def M_XLR       = "b00110".U
  def M_XSC       = "b00111".U
  def M_XWR       = "b00001".U; // int store
  def M_XRD       = "b00000".U; // int load
  def M_PFW       = "b00011".U; // prefetch with intent to write

  // todo
  def grouped(x: UInt, width: Int):              Seq[UInt]   =
    (0 until x.getWidth by width).map(base => x(base + width - 1, base))
  def grouped[T <: Data](x: Vec[T], width: Int): Seq[Vec[T]] =
    (0 until x.size by width).map(base => VecInit(Seq.tabulate(width) { i => x(base + i) }))

  val clock       = io.clock
  val reset       = io.reset
  val pma_checker = pmaChecker

  val tECC       = cacheParams.tagCode
  val dECC       = cacheParams.dataCode
  require(subWordBits % eccBits == 0, "subWordBits must be a multiple of eccBits")
  require(eccBytes == 1 || !dECC.isInstanceOf[IdentityCode])
  require(cacheParams.silentDrop || cacheParams.acquireBeforeRelease, "!silentDrop requires acquireBeforeRelease")
  val usingRMW   = eccBytes > 1 || usingAtomicsInCache
  val mmioOffset = outer.firstMMIO
  // edge.manager.requireFifo(TLFIFOFixer.allVolatile) // TileLink pipelining MMIO requests

  val clock_en_reg = Reg(Bool())
  io.cpu.clock_enabled := clock_en_reg

  val gated_clock =
    if (!cacheParams.clockGate) clock
    else chisel3.util.circt.ClockGate(clock, clock_en_reg)
  class DCacheModuleImpl { // entering gated-clock domain
    // tags
    val replacer = ReplacementPolicy.fromString(cacheParams.replacementPolicy, nWays)

    /** Metadata Arbiter: 0: Tag update on reset 1: Tag update on ECC error 2: Tag update on hit 3: Tag update on refill
      * 4: Tag update on release 5: Tag update on flush 6: Tag update on probe 7: Tag update on CPU request
      */

    val metaArb = Module(
      new Arbiter(
        new DCacheMetadataReq(
          vaddrBitsExtended,
          idxBits,
          nWays,
          cacheParams.tagCode.width(new L1Metadata(tagBits).getWidth)
        ),
        8
      ) with InlineInstance
    )
    // todo: delete
    metaArb.io.in(1).valid := false.B
    metaArb.io.in(1).bits  := DontCare

    val dcacheTagSRAM: SRAMInterface[Vec[UInt]] = SRAM.masked(
      size = nSets,
      tpe = Vec(nWays, chiselTypeOf(metaArb.io.out.bits.data)),
      numReadPorts = 0,
      numWritePorts = 0,
      numReadwritePorts = 1
    )

    // data
    //    val data = Module(new DCacheDataArray)
    // no more DCacheDataArray module for better PD experience
    // Vec(nWays, req.bits.wdata)
    val dcacheDataSRAM = Seq.tabulate(rowBits / subWordBits) { i =>
      SRAM.masked(
        size = nSets * cacheBlockBytes / rowBytes,
        tpe = Vec(nWays * (subWordBits / eccBits), UInt(encBits.W)),
        numReadPorts = 0,
        numWritePorts = 0,
        numReadwritePorts = 1
      )
    }
    omInstance.sramsIn.foreach(
      _ := Property((dcacheDataSRAM ++ Some(dcacheTagSRAM)).map(_.description.get.asAnyClassType))
    )

    /** Data Arbiter 0: data from pending store buffer 1: data from TL-D refill 2: release to TL-A 3: hit path to CPU
      */
    val dataArb = Module(
      new Arbiter(new DCacheDataReq(untagBits, encBits, rowBytes, eccBytes, subWordBytes, wordBytes, nWays), 4)
        with InlineInstance
    )

    val awQueue: QueueIO[AW] = Queue.io(chiselTypeOf(io.loadStoreAXI.aw.bits), 1, flow = true)

    val arQueue: QueueIO[AR] = Queue.io(chiselTypeOf(io.loadStoreAXI.ar.bits), 1, flow = true)

    dataArb.io.in.tail.foreach(_.bits.wdata := dataArb.io.in.head.bits.wdata) // tie off write ports by default
    dataArb.io.out.ready := true.B
    metaArb.io.out.ready := clock_en_reg

    val readData: Seq[Seq[UInt]] = dcacheDataSRAM.zipWithIndex.map { case (array, i) =>
      val valid       = dataArb.io.out.valid && ((dcacheDataSRAM.size == 1).B || dataArb.io.out.bits.wordMask(i))
      val dataEccMask = if (eccBits == subWordBits) Seq(true.B) else dataArb.io.out.bits.eccMask.asBools
      val wMask       =
        if (nWays == 1) dataEccMask
        else (0 until nWays).flatMap(i => dataEccMask.map(_ && dataArb.io.out.bits.way_en(i)))
      val wWords      = grouped(dataArb.io.out.bits.wdata, encBits * (subWordBits / eccBits))
      val addr        = (dataArb.io.out.bits.addr >> rowOffBits).asUInt
      val wData       = VecInit(grouped(wWords(i), encBits))
      val wMaskSlice: Seq[Bool] = (0 until wMask.size)
        .filter(j => i % (wordBytes * 8 / subWordBits) == (j % (wordBytes / eccBytes)) / (subWordBytes / eccBytes))
        .map(wMask(_))
      array.readwritePorts.foreach { arrayPort =>
        arrayPort.enable    := valid
        arrayPort.isWrite   := dataArb.io.out.bits.write
        arrayPort.address   := addr
        arrayPort.writeData := VecInit((0 until nWays).flatMap(i => wData))
        arrayPort.mask.foreach(_ := VecInit(wMaskSlice))
      }
      val data:       Vec[UInt] = array.readwritePorts.head.readData
      // data.grouped(subWordBits / eccBits).map(_.asUInt).toSeq
      grouped(data, subWordBits / eccBits).map(_.asUInt)
    }
    // (io.resp.zip(rdata.transpose)).foreach { case (resp, data) => resp := data.asUInt }
    val rdata = readData.transpose.map(ds => VecInit(ds).asUInt)

    val release_queue_empty = Wire(Bool())

    val s1_valid            = RegNext(io.cpu.req.fire, false.B)
    val releaseAddress      = RegInit(0.U(parameter.paddrBits.W))
    val s1_nack             = WireDefault(false.B)
    val s1_valid_masked     = s1_valid && !io.cpu.s1_kill
    val s1_valid_not_nacked = s1_valid && !s1_nack
    val s0_clk_en           = metaArb.io.out.valid && !metaArb.io.out.bits.write

    val s0_req = WireInit(io.cpu.req.bits)
    s0_req.addr := Cat(metaArb.io.out.bits.addr >> blockOffBits, io.cpu.req.bits.addr(blockOffBits - 1, 0))
    s0_req.idx.foreach(_ := Cat(metaArb.io.out.bits.idx, s0_req.addr(blockOffBits - 1, 0)))
    when(!metaArb.io.in(7).ready) { s0_req.phys := true.B }
    val s1_req = RegEnable(s0_req, s0_clk_en)
    val s1_vaddr = Cat(s1_req.idx.getOrElse(s1_req.addr) >> tagLSB, s1_req.addr(tagLSB - 1, 0))

    val s0_tlb_req: TLBReq = Wire(new TLBReq(paddrBits, vaddrBitsExtended))
    s0_tlb_req.passthrough := s0_req.phys
    s0_tlb_req.vaddr       := s0_req.addr
    s0_tlb_req.size        := s0_req.size
    s0_tlb_req.cmd         := s0_req.cmd
    s0_tlb_req.prv         := s0_req.dprv
    s0_tlb_req.v           := s0_req.dv
    val s1_tlb_req = RegEnable(s0_tlb_req, s0_clk_en)

    val s1_read        = isRead(s1_req.cmd)
    val s1_write       = isWrite(s1_req.cmd)
    val s1_readwrite   = s1_read || s1_write
    val s1_sfence      = s1_req.cmd === M_SFENCE || s1_req.cmd === M_HFENCEV || s1_req.cmd === M_HFENCEG
    val s1_flush_line  = s1_req.cmd === M_FLUSH_ALL && s1_req.size(0)
    val s1_flush_valid = Reg(Bool())
    val s1_waw_hazard  = Wire(Bool())

    val s_ready :: s_voluntary_writeback :: s_voluntary_write_meta :: s_voluntary_aw :: Nil = Enum(4)
    val supports_flush = outer.flushOnFenceI || coreParams.haveCFlush
    val flushed = RegInit(true.B)
    val flushing = RegInit(false.B)
    val flushing_req = Reg(chiselTypeOf(s1_req))
    val cached_grant_wait = RegInit(false.B)
    val resetting = RegInit(false.B)
    val flushCounter = RegInit((nSets * (nWays - 1)).U(log2Ceil(nSets * nWays).W))
    val release_ack_wait = RegInit(false.B)
    val release_ack_addr = Reg(UInt(paddrBits.W))
    val release_state = RegInit(s_ready)
    val refill_way = Reg(UInt())
    val any_pstore_valid = Wire(Bool())
    def isOneOf(x: UInt, s: Seq[UInt]): Bool = VecInit(s.map(x === _)).asUInt.orR

    val inWriteback = release_state === s_voluntary_writeback
    val awState     = release_state === s_voluntary_aw
    val releaseWay  = Wire(UInt())
    io.cpu.req.ready    := (release_state === s_ready) && !cached_grant_wait && !s1_nack
    release_queue_empty := release_state =/= s_voluntary_writeback

    // I/O MSHRs
    val uncachedInFlight = RegInit(VecInit(Seq.fill(maxUncachedInFlight)(false.B)))
    val uncachedReqs     = Reg(
      Vec(
        maxUncachedInFlight,
        new HellaCacheReq(
          coreMaxAddrBits,
          usingVM,
          untagBits,
          pgIdxBits,
          dcacheReqTagBits,
          dcacheArbPorts,
          coreDataBytes
        )
      )
    )
    val uncachedResp     = WireInit(
      new HellaCacheReq(
        coreMaxAddrBits,
        usingVM,
        untagBits,
        pgIdxBits,
        dcacheReqTagBits,
        dcacheArbPorts,
        coreDataBytes
      ),
      DontCare
    )

    // hit initiation path
    val s0_read = isRead(io.cpu.req.bits.cmd)
    dataArb.io.in(3).valid         := io.cpu.req.valid && likelyNeedsRead(io.cpu.req.bits)
    dataArb.io.in(3).bits          := dataArb.io.in(1).bits
    dataArb.io.in(3).bits.write    := false.B
    dataArb.io.in(3).bits.addr     := Cat(
      io.cpu.req.bits.idx.getOrElse(io.cpu.req.bits.addr) >> tagLSB,
      io.cpu.req.bits.addr(tagLSB - 1, 0)
    )
    dataArb.io.in(3).bits.wordMask := {
      val mask = (log2Ceil(subWordBytes) until rowOffBits).foldLeft(1.U) { case (in, i) =>
        val upper_mask = Mux(
          (i >= log2Ceil(wordBytes)).B || io.cpu.req.bits.size <= i.U,
          0.U,
          ((BigInt(1) << (1 << (i - log2Ceil(subWordBytes)))) - 1).U
        )
        val upper      = Mux(io.cpu.req.bits.addr(i), in, 0.U) | upper_mask
        val lower      = Mux(io.cpu.req.bits.addr(i), 0.U, in)
        upper ## lower
      }
      Fill(subWordBytes / eccBytes, mask)
    }
    dataArb.io.in(3).bits.eccMask  := ~0.U((wordBytes / eccBytes).W)
    dataArb.io.in(3).bits.way_en   := ~0.U(nWays.W)
    when(!dataArb.io.in(3).ready && s0_read) { io.cpu.req.ready := false.B }
    val s1_did_read = RegEnable(dataArb.io.in(3).ready && (io.cpu.req.valid && needsRead(io.cpu.req.bits)), s0_clk_en)
    val s1_read_mask = RegEnable(dataArb.io.in(3).bits.wordMask, s0_clk_en)
    metaArb.io.in(7).valid       := io.cpu.req.valid
    metaArb.io.in(7).bits.write  := false.B
    metaArb.io.in(7).bits.idx    := dataArb.io.in(3).bits.addr(idxMSB, idxLSB)
    metaArb.io.in(7).bits.addr   := io.cpu.req.bits.addr
    metaArb.io.in(7).bits.way_en := metaArb.io.in(4).bits.way_en
    metaArb.io.in(7).bits.data   := metaArb.io.in(4).bits.data
    when(!metaArb.io.in(7).ready) { io.cpu.req.ready := false.B }

    // address translation
    val s1_cmd_uses_tlb = s1_readwrite || s1_flush_line || s1_req.cmd === M_WOK
    io.ptw <> tlb.io.ptw
    tlb.io.kill      := io.cpu.s2_kill
    tlb.io.req.valid := s1_valid && !io.cpu.s1_kill && s1_cmd_uses_tlb
    tlb.io.req.bits  := s1_tlb_req
    when(!tlb.io.req.ready && !tlb.io.ptw.resp.valid && !io.cpu.req.bits.phys) { io.cpu.req.ready := false.B }
    when(s1_valid && s1_cmd_uses_tlb && tlb.io.resp.miss) { s1_nack := true.B }

    tlb.io.sfence.valid     := s1_valid && !io.cpu.s1_kill && s1_sfence
    tlb.io.sfence.bits.rs1  := s1_req.size(0)
    tlb.io.sfence.bits.rs2  := s1_req.size(1)
    tlb.io.sfence.bits.asid := io.cpu.s1_data.data
    tlb.io.sfence.bits.addr := s1_req.addr
    tlb.io.sfence.bits.hv   := s1_req.cmd === M_HFENCEV
    tlb.io.sfence.bits.hg   := s1_req.cmd === M_HFENCEG

    val s1_paddr = Cat(tlb.io.resp.paddr >> pgIdxBits, s1_req.addr(pgIdxBits - 1, 0))

    //    pma_checker.io.req.bits.passthrough := true.B
    //    pma_checker.io.req.bits.vaddr := s1_req.addr
    //    pma_checker.io.req.bits.size := s1_req.size
    //    pma_checker.io.req.bits.cmd := s1_req.cmd
    //    pma_checker.io.req.bits.prv := s1_req.dprv
    //    pma_checker.io.req.bits.v := s1_req.dv
    // todo: uncertain
    pma_checker.io.paddr := s1_paddr
    val s1_victim_way                       = Wire(UInt())
    val (s1_hit_way, s1_hit_state, s1_meta) =
      if (usingDataScratchpad) {
        val baseAddr: UInt = parameter.scratch.getOrElse(BigInt(0)).U
        val inScratchpad = s1_paddr >= baseAddr && s1_paddr < baseAddr + (nSets * cacheBlockBytes).U
        val hitState     = Mux(inScratchpad, ClientMetadata(3.U), ClientMetadata(0.U))
        val dummyMeta    = L1Metadata(0.U, ClientMetadata(0.U))
        (inScratchpad, hitState, Seq(tECC.encode(dummyMeta.asUInt)))
      } else {
        val metaReq = metaArb.io.out
        val metaIdx = metaReq.bits.idx
        val wmask   = if (nWays == 1) Seq(true.B) else metaReq.bits.way_en.asBools
        dcacheTagSRAM.readwritePorts.foreach { tagPort =>
          tagPort.enable    := metaReq.valid
          tagPort.isWrite   := metaReq.bits.write
          tagPort.address   := metaIdx
          tagPort.writeData := VecInit(Seq.fill(nWays)(metaReq.bits.data))
          tagPort.mask.foreach(_ := VecInit(wmask))
        }
        val s1_meta: Seq[UInt] = dcacheTagSRAM.readwritePorts.head.readData
        val s1_meta_uncorrected: Seq[L1Metadata] =
          s1_meta.map(tECC.decode(_).uncorrected.asTypeOf(new L1Metadata(tagBits)))
        val s1_tag:              UInt            = s1_paddr >> tagLSB
        val s1_meta_hit_way   = VecInit(
          s1_meta_uncorrected.map(r => ClientMetadata.isValid(r.coh) && r.tag === s1_tag)
        ).asUInt
        val s1_meta_hit_state = (s1_meta_uncorrected
          .map(r => Mux(r.tag === s1_tag && !s1_flush_valid, r.coh.asUInt, 0.U))
          .reduce(_ | _))
          .asTypeOf(chiselTypeOf(ClientMetadata(0.U)))
        (s1_meta_hit_way, s1_meta_hit_state, s1_meta)
      }
    val s1_data_way                         = WireDefault(if (nWays == 1) 1.U else Mux(inWriteback, releaseWay, s1_hit_way))
//    val tl_d_data_encoded = Wire(chiselTypeOf(encodeData(tl_out.d.bits.data, false.B)))
    val tl_d_data_encoded                   = Wire(chiselTypeOf(encodeData(io.loadStoreAXI.r.bits.data, false.B)))
//    val s1_all_data_ways = VecInit(data.io.resp ++ (!cacheParams.separateUncachedResp).option(tl_d_data_encoded))
    val s1_all_data_ways: Vec[UInt] = VecInit(
      rdata ++ Option.when(!cacheParams.separateUncachedResp)(tl_d_data_encoded)
    )
    val s1_mask_xwr = new StoreGen(s1_req.size, s1_req.addr, 0.U, wordBytes).mask
    val s1_mask     = Mux(s1_req.cmd === M_PWR, io.cpu.s1_data.mask, s1_mask_xwr)
    // for partial writes, s1_data.mask must be a subset of s1_mask_xwr
    assert(!(s1_valid_masked && s1_req.cmd === M_PWR) || (s1_mask_xwr | ~io.cpu.s1_data.mask).andR)

    val s2_valid                     = RegNext(s1_valid_masked && !s1_sfence, init = false.B)
    val s2_valid_no_xcpt             = s2_valid && !io.cpu.s2_xcpt.asUInt.orR
    val releaseInFlight              = release_state =/= s_ready
    val s2_not_nacked_in_s1          = RegNext(!s1_nack)
    val s2_valid_not_nacked_in_s1    = s2_valid && s2_not_nacked_in_s1
    val s2_valid_masked              = s2_valid_no_xcpt && s2_not_nacked_in_s1
    val s2_valid_not_killed          = s2_valid_masked && !io.cpu.s2_kill
    val s2_req                       = Reg(chiselTypeOf(io.cpu.req.bits))
    val s2_cmd_flush_all             = s2_req.cmd === M_FLUSH_ALL && !s2_req.size(0)
    val s2_cmd_flush_line            = s2_req.cmd === M_FLUSH_ALL && s2_req.size(0)
    val s2_tlb_xcpt                  = Reg(chiselTypeOf(tlb.io.resp))
    val s2_pma                       = Reg(chiselTypeOf(tlb.io.resp))
    val s2_uncached_resp_addr        = Reg(chiselTypeOf(s2_req.addr)) // should be DCE'd in synthesis
    when(s1_valid_not_nacked || s1_flush_valid) {
      s2_req      := s1_req
      s2_req.addr := s1_paddr
      s2_tlb_xcpt := tlb.io.resp
      s2_pma      := tlb.io.resp
    }
    val s2_vaddr                     = Cat(RegEnable(s1_vaddr, s1_valid_not_nacked || s1_flush_valid) >> tagLSB, s2_req.addr(tagLSB - 1, 0))
    val s2_read                      = isRead(s2_req.cmd)
    val s2_write                     = isWrite(s2_req.cmd)
    val s2_readwrite                 = s2_read || s2_write
    val s2_flush_valid_pre_tag_ecc   = RegNext(s1_flush_valid)
    val s1_meta_decoded              = s1_meta.map(tECC.decode(_))
    val s1_meta_clk_en               = s1_valid_not_nacked || s1_flush_valid
    val s2_meta_correctable_errors   = VecInit(s1_meta_decoded.map(m => RegEnable(m.correctable, s1_meta_clk_en))).asUInt
    val s2_meta_uncorrectable_errors = VecInit(
      s1_meta_decoded.map(m => RegEnable(m.uncorrectable, s1_meta_clk_en))
    ).asUInt
    val s2_meta_error_uncorrectable  = s2_meta_uncorrectable_errors.orR
    val s2_meta_corrected            =
      s1_meta_decoded.map(m => RegEnable(m.corrected, s1_meta_clk_en).asTypeOf(new L1Metadata(tagBits)))
    val s2_meta_error                = (s2_meta_uncorrectable_errors | s2_meta_correctable_errors).orR
    val s2_flush_valid               = s2_flush_valid_pre_tag_ecc && !s2_meta_error
    val s2_data                      = {
      val wordsPerRow  = rowBits / subWordBits
      val en           = s1_valid || inWriteback || io.cpu.replay_next
      val word_en      = Mux(inWriteback, Fill(wordsPerRow, 1.U), Mux(s1_did_read, s1_read_mask, 0.U))
      val s1_way_words = s1_all_data_ways.map(grouped(_, dECC.width(eccBits) * (subWordBits / eccBits)))
      if (cacheParams.pipelineWayMux) {
        val s1_word_en = Mux(io.cpu.replay_next, 0.U, word_en)
        VecInit(for (i <- 0 until wordsPerRow) yield {
          val s2_way_en    = RegEnable(Mux(s1_word_en(i), s1_data_way, 0.U), en)
          val s2_way_words = (0 until nWays).map(j => RegEnable(s1_way_words(j)(i), en && word_en(i)))
          (0 until nWays).map(j => Mux(s2_way_en(j), s2_way_words(j), 0.U)).reduce(_ | _)
        }).asUInt
      } else {
        val s1_word_en = Mux(
          !io.cpu.replay_next,
          word_en,
          UIntToOH(
            if (log2Ceil(rowBits / 8) == log2Ceil(wordBytes)) 0.U
            else uncachedResp.addr(log2Ceil(rowBits / 8) - 1, log2Ceil(wordBytes)),
            wordsPerRow
          )
        )
        VecInit(for (i <- 0 until wordsPerRow) yield {
          RegEnable(Mux1H(Mux(s1_word_en(i), s1_data_way, 0.U), s1_way_words.map(_(i))), en)
        }).asUInt
      }
    }
    val s2_hit_way                   = RegEnable(s1_hit_way, s1_valid_not_nacked)
    val s2_hit_state: ClientMetadata = RegEnable(s1_hit_state, s1_valid_not_nacked || s1_flush_valid)
    val s2_waw_hazard  = RegEnable(s1_waw_hazard, s1_valid_not_nacked)
    val s2_store_merge = Wire(Bool())
//    val s2_hit_valid = s2_hit_state.isValid()
    val s2_hit_valid   = s2_hit_state.state > 0.U
    // No prob, so only D T N
    // val (s2_hit, s2_grow_param, s2_new_hit_state) = s2_hit_state.onAccess(s2_req.cmd)
    val s2_hit         = s2_hit_valid
    val nexState       = Mux(s2_hit_state.state === 3.U || isWrite(s2_req.cmd), 3.U, 2.U)
    val s2_new_hit_state: ClientMetadata = ClientMetadata(nexState)
    val s2_data_decoded                               = decodeData(s2_data)
    val s2_data_error                                 = VecInit(s2_data_decoded.map(_.error)).asUInt.orR
    val s2_data_error_uncorrectable                   = VecInit(s2_data_decoded.map(_.uncorrectable)).asUInt.orR
    val s2_data_corrected                             = VecInit(s2_data_decoded.map(_.corrected): Seq[UInt]).asUInt
    val s2_data_uncorrected                           = VecInit(s2_data_decoded.map(_.uncorrected): Seq[UInt]).asUInt
    val s2_valid_hit_maybe_flush_pre_data_ecc_and_waw = s2_valid_masked && !s2_meta_error && s2_hit
    val s2_no_alloc_hazard                            =
      if (!usingVM || pgIdxBits >= untagBits) false.B
      else {
        // make sure that any in-flight non-allocating accesses are ordered before
        // any allocating accesses.  this can only happen if aliasing is possible.
        val any_no_alloc_in_flight = Reg(Bool())
        when(!uncachedInFlight.asUInt.orR) { any_no_alloc_in_flight := false.B }
        when(s2_valid && s2_req.no_alloc) { any_no_alloc_in_flight := true.B }
        val s1_need_check          = any_no_alloc_in_flight || s2_valid && s2_req.no_alloc

        val concerns         = (uncachedInFlight.zip(uncachedReqs)) :+ (s2_valid && s2_req.no_alloc, s2_req)
        val s1_uncached_hits = VecInit(concerns.map { c =>
          val concern_wmask = new StoreGen(c._2.size, c._2.addr, 0.U, wordBytes).mask
          val addr_match    = (c._2.addr ^ s1_paddr)(pgIdxBits + pgLevelBits - 1, log2Ceil(wordBytes)) === 0.U
          val mask_match    = (concern_wmask & s1_mask_xwr).orR || c._2.cmd === M_PWR || s1_req.cmd === M_PWR
          val cmd_match     = isWrite(c._2.cmd) || isWrite(s1_req.cmd)
          c._1 && s1_need_check && cmd_match && addr_match && mask_match
        })

        val s2_uncached_hits = RegEnable(s1_uncached_hits.asUInt, s1_valid_not_nacked)
        s2_uncached_hits.orR
      }
    val s2_valid_hit_pre_data_ecc_and_waw             =
      s2_valid_hit_maybe_flush_pre_data_ecc_and_waw && s2_readwrite && !s2_no_alloc_hazard
    val s2_valid_flush_line                           = s2_valid_hit_maybe_flush_pre_data_ecc_and_waw && s2_cmd_flush_line
    val s2_valid_hit_pre_data_ecc                     = s2_valid_hit_pre_data_ecc_and_waw && (!s2_waw_hazard || s2_store_merge)
    val s2_valid_data_error                           = s2_valid_hit_pre_data_ecc_and_waw && s2_data_error
    val s2_valid_hit                                  = s2_valid_hit_pre_data_ecc && !s2_data_error
    val s2_valid_miss                                 = s2_valid_masked && s2_readwrite && !s2_meta_error && !s2_hit
    val s2_uncached                                   = !s2_pma.cacheable || s2_req.no_alloc && !s2_pma.must_alloc && !s2_hit_valid
    val s2_valid_cached_miss                          = s2_valid_miss && !s2_uncached && !uncachedInFlight.asUInt.orR
    dontTouch(s2_valid_cached_miss)
    val s2_want_victimize                             =
      (!usingDataScratchpad).B && (s2_valid_cached_miss || s2_valid_flush_line || s2_valid_data_error || s2_flush_valid)
    val s2_cannot_victimize                           = !s2_flush_valid && io.cpu.s2_kill
    val s2_victimize                                  = s2_want_victimize && !s2_cannot_victimize
    val s2_valid_uncached_pending                     = s2_valid_miss && s2_uncached && !uncachedInFlight.asUInt.andR
    val s2_victim_way                                 = UIntToOH(RegEnable(s1_victim_way, s1_valid_not_nacked || s1_flush_valid))
    val s2_victim_or_hit_way                          = Mux(s2_hit_valid, s2_hit_way, s2_victim_way)
    val s2_victim_tag                                 = Mux(
      s2_valid_data_error || s2_valid_flush_line,
      s2_req.addr(paddrBits - 1, tagLSB),
      Mux1H(s2_victim_way, s2_meta_corrected).tag
    )
    val s2_victim_state: ClientMetadata = Mux(s2_hit_valid, s2_hit_state, Mux1H(s2_victim_way, s2_meta_corrected).coh)

    val s2_victim_dirty       = s2_victim_state.state === 3.U
    dontTouch(s2_victim_dirty)
    val s2_update_meta        = s2_hit_state.state =/= s2_new_hit_state.state
    val s2_dont_nack_uncached = s2_valid_uncached_pending && awQueue.enq.ready
    val s2_dont_nack_misc     = s2_valid_masked && !s2_meta_error &&
      (supports_flush.B && s2_cmd_flush_all && flushed && !flushing ||
        supports_flush.B && s2_cmd_flush_line && !s2_hit ||
        s2_req.cmd === M_WOK)
    io.cpu.s2_nack := s2_valid_no_xcpt && !s2_dont_nack_uncached && !s2_dont_nack_misc && !s2_valid_hit
    when(io.cpu.s2_nack || (s2_valid_hit_pre_data_ecc_and_waw && s2_update_meta)) { s1_nack := true.B }

    // tag updates on ECC errors
    val s2_first_meta_corrected = PriorityMux(s2_meta_correctable_errors, s2_meta_corrected)
    metaArb.io.in(1).valid       := s2_meta_error && (s2_valid_masked || s2_flush_valid_pre_tag_ecc)
    metaArb.io.in(1).bits.write  := true.B
    metaArb.io.in(1).bits.way_en := s2_meta_uncorrectable_errors | Mux(
      s2_meta_error_uncorrectable,
      0.U,
      PriorityEncoderOH(s2_meta_correctable_errors)
    )

    // tag updates on hit
    metaArb.io.in(2).valid       := s2_valid_hit_pre_data_ecc_and_waw && s2_update_meta
    metaArb.io.in(2).bits.write  := !io.cpu.s2_kill
    metaArb.io.in(2).bits.way_en := s2_victim_or_hit_way
    metaArb.io.in(2).bits.idx    := s2_vaddr(idxMSB, idxLSB)
    metaArb.io.in(2).bits.addr   := Cat(io.cpu.req.bits.addr >> untagBits, s2_vaddr(idxMSB, 0))
    metaArb.io.in(2).bits.data   := tECC.encode(L1Metadata(s2_req.addr >> tagLSB, s2_new_hit_state).asUInt)

    // load reservations and TL error reporting
    val s2_lr          = (usingAtomics && !usingDataScratchpad).B && s2_req.cmd === M_XLR
    val s2_sc          = (usingAtomics && !usingDataScratchpad).B && s2_req.cmd === M_XSC
    val lrscCount      = RegInit(0.U)
    val lrscValid      = lrscCount > lrscBackoff.U
    val lrscBackingOff = lrscCount > 0.U && !lrscValid
    val lrscAddr       = Reg(UInt())
    val lrscAddrMatch  = lrscAddr === (s2_req.addr >> blockOffBits)
    val s2_sc_fail     = s2_sc && !(lrscValid && lrscAddrMatch)
    when((s2_valid_hit && s2_lr && !cached_grant_wait || s2_valid_cached_miss) && !io.cpu.s2_kill) {
      lrscCount := Mux(s2_hit, (lrscCycles - 1).U, 0.U)
      lrscAddr  := s2_req.addr >> blockOffBits
    }
    when(lrscCount > 0.U) { lrscCount := lrscCount - 1.U }
    when(s2_valid_not_killed && lrscValid) { lrscCount := lrscBackoff.U }

    // don't perform data correction if it might clobber a recent store
    val s2_correct                           =
      s2_data_error && !any_pstore_valid && !RegNext(any_pstore_valid || s2_valid) && usingDataScratchpad.B
    // pending store buffer
    val s2_valid_correct                     = s2_valid_hit_pre_data_ecc_and_waw && s2_correct && !io.cpu.s2_kill
    def s2_store_valid_pre_kill              = s2_valid_hit && s2_write && !s2_sc_fail
    def s2_store_valid                       = s2_store_valid_pre_kill && !io.cpu.s2_kill
    val pstore1_cmd                          = RegEnable(s1_req.cmd, s1_valid_not_nacked && s1_write)
    val pstore1_addr                         = RegEnable(s1_vaddr, s1_valid_not_nacked && s1_write)
    val pstore1_data                         = RegEnable(io.cpu.s1_data.data, s1_valid_not_nacked && s1_write)
    val pstore1_way                          = RegEnable(s1_hit_way, s1_valid_not_nacked && s1_write)
    val pstore1_mask                         = RegEnable(s1_mask, s1_valid_not_nacked && s1_write)
    val pstore1_storegen_data                = WireDefault(pstore1_data)
    val pstore1_rmw                          = usingRMW.B && RegEnable(needsRead(s1_req), s1_valid_not_nacked && s1_write)
    val pstore1_merge_likely                 = s2_valid_not_nacked_in_s1 && s2_write && s2_store_merge
    val pstore1_merge                        = s2_store_valid && s2_store_merge
    val pstore2_valid                        = RegInit(false.B)
    val pstore_drain_opportunistic           =
      !(io.cpu.req.valid && likelyNeedsRead(io.cpu.req.bits)) && !(s1_valid && s1_waw_hazard)
    val pstore_drain_on_miss                 = releaseInFlight || RegNext(io.cpu.s2_nack)
    val pstore1_held                         = RegInit(false.B)
    val pstore1_valid_likely                 = s2_valid && s2_write || pstore1_held
    def pstore1_valid_not_rmw(s2_kill: Bool) = s2_valid_hit_pre_data_ecc && s2_write && !s2_kill || pstore1_held
    val pstore1_valid                        = s2_store_valid || pstore1_held
    any_pstore_valid := pstore1_held || pstore2_valid
    val pstore_drain_structural              = pstore1_valid_likely && pstore2_valid && ((s1_valid && s1_write) || pstore1_rmw)
    assert(pstore1_rmw || pstore1_valid_not_rmw(io.cpu.s2_kill) === pstore1_valid)
    ccover(pstore_drain_structural, "STORE_STRUCTURAL_HAZARD", "D$ read-modify-write structural hazard")
    ccover(pstore1_valid && pstore_drain_on_miss, "STORE_DRAIN_ON_MISS", "D$ store buffer drain on miss")
    ccover(s1_valid_not_nacked && s1_waw_hazard, "WAW_HAZARD", "D$ write-after-write hazard")
    def should_pstore_drain(truly: Bool)     = {
      val s2_kill = truly && io.cpu.s2_kill
      !pstore1_merge_likely &&
      (usingRMW.B && pstore_drain_structural ||
        (((pstore1_valid_not_rmw(
          s2_kill
        ) && !pstore1_rmw) || pstore2_valid) && (pstore_drain_opportunistic || pstore_drain_on_miss)))
    }
    val pstore_drain                         = should_pstore_drain(true.B)
    pstore1_held := (s2_store_valid && !s2_store_merge || pstore1_held) && pstore2_valid && !pstore_drain
    val advance_pstore1                      = (pstore1_valid || s2_valid_correct) && (pstore2_valid === pstore_drain)
    pstore2_valid := pstore2_valid && !pstore_drain || advance_pstore1
    val pstore2_addr                         = RegEnable(Mux(s2_correct, s2_vaddr, pstore1_addr), advance_pstore1)
    val pstore2_way                          = RegEnable(Mux(s2_correct, s2_hit_way, pstore1_way), advance_pstore1)
    val pstore2_storegen_data                = VecInit({
      for (i <- 0 until wordBytes)
        yield RegEnable(
          pstore1_storegen_data(8 * (i + 1) - 1, 8 * i),
          advance_pstore1 || pstore1_merge && pstore1_mask(i)
        )
    }).asUInt
    val pstore2_storegen_mask                = {
      val mask = Reg(UInt(wordBytes.W))
      when(advance_pstore1 || pstore1_merge) {
        val mergedMask = pstore1_mask | Mux(pstore1_merge, mask, 0.U)
        mask := ~Mux(s2_correct, 0.U, ~mergedMask)
      }
      mask
    }
    s2_store_merge := (if (eccBytes == 1) false.B
                       else {
                         ccover(pstore1_merge, "STORE_MERGED", "D$ store merged")
                         // only merge stores to ECC granules that are already stored-to, to avoid
                         // WAW hazards
                         val wordMatch = (eccMask(pstore2_storegen_mask) | ~eccMask(pstore1_mask)).andR
                         val idxMatch  = s2_vaddr(untagBits - 1, log2Ceil(wordBytes)) === pstore2_addr(
                           untagBits - 1,
                           log2Ceil(wordBytes)
                         )
                         val tagMatch  = (s2_hit_way & pstore2_way).orR
                         pstore2_valid && wordMatch && idxMatch && tagMatch
                       })
    dataArb.io.in(0).valid := should_pstore_drain(false.B)
    dataArb.io.in(0).bits.write := pstore_drain
    dataArb.io.in(0).bits.addr := Mux(pstore2_valid, pstore2_addr, pstore1_addr)
    dataArb.io.in(0).bits.way_en := Mux(pstore2_valid, pstore2_way, pstore1_way)
    dataArb.io.in(0).bits.wdata := encodeData(
      Fill(rowWords, Mux(pstore2_valid, pstore2_storegen_data, pstore1_data)),
      false.B
    )
    dataArb.io.in(0).bits.wordMask := {
      // val eccMask = dataArb.io.in(0).bits.eccMask.asBools.grouped(subWordBytes / eccBytes).map(_.orR).toSeq.asUInt
      val eccMask  = VecInit(
        grouped(VecInit(dataArb.io.in(0).bits.eccMask.asBools), subWordBytes / eccBytes).map(_.asUInt.orR)
      ).asUInt
      val wordMask = UIntToOH(
        if (rowOffBits == log2Ceil(wordBytes)) 0.U
        else Mux(pstore2_valid, pstore2_addr, pstore1_addr)(rowOffBits - 1, log2Ceil(wordBytes))
      )
      FillInterleaved(wordBytes / subWordBytes, wordMask) & Fill(rowBytes / wordBytes, eccMask)
    }
    dataArb.io.in(0).bits.eccMask := eccMask(Mux(pstore2_valid, pstore2_storegen_mask, pstore1_mask))

    // store->load RAW hazard detection
    def s1Depends(addr: UInt, mask: UInt) =
      addr(idxMSB, wordOffBits) === s1_vaddr(idxMSB, wordOffBits) &&
        Mux(s1_write, (eccByteMask(mask) & eccByteMask(s1_mask_xwr)).orR, (mask & s1_mask_xwr).orR)
    val s1_hazard                         =
      (pstore1_valid_likely && s1Depends(pstore1_addr, pstore1_mask)) ||
        (pstore2_valid && s1Depends(pstore2_addr, pstore2_storegen_mask))
    val s1_raw_hazard                     = s1_read && s1_hazard
    s1_waw_hazard := (if (eccBytes == 1) false.B
                      else {
                        ccover(s1_valid_not_nacked && s1_waw_hazard, "WAW_HAZARD", "D$ write-after-write hazard")
                        s1_write && (s1_hazard || needsRead(s1_req) && !s1_did_read)
                      })
    when(s1_valid && s1_raw_hazard) { s1_nack := true.B }

    // performance hints to processor
    io.cpu.s2_nack_cause_raw := RegNext(s1_raw_hazard) || !(!s2_waw_hazard || s2_store_merge)

    // Prepare a TileLink request message that initiates a transaction
    val a_source        = PriorityEncoder(~uncachedInFlight.asUInt << mmioOffset) // skip the MSHR
    val acquire_address = (s2_req.addr >> idxLSB) << idxLSB
    val access_address  = s2_req.addr
    val a_size          = s2_req.size
    val a_data          = Fill(beatWords, pstore1_data)
    val a_mask          = pstore1_mask << ((if (log2Ceil(beatBytes) == log2Ceil(wordBytes)) 0.U
                                   else access_address(log2Ceil(beatBytes) - 1, log2Ceil(wordBytes))) << 3)
    val memAccessValid  = !io.cpu.s2_kill &&
      (s2_valid_uncached_pending ||
        (s2_valid_cached_miss &&
          !(release_ack_wait && (s2_req.addr ^ release_ack_addr)(
            ((pgIdxBits + pgLevelBits).min(paddrBits)) - 1,
            idxLSB
          ) === 0.U) &&
          (cacheParams.acquireBeforeRelease.B && !release_ack_wait && release_queue_empty || !s2_victim_dirty)))
    // !s2_uncached -> read cache line
    val accessWillRead: Bool = !s2_uncached || !s2_write
    // If no managers support atomics, assert fail if processor asks for them
    assert(!(memAccessValid && s2_read && s2_write && s2_uncached))
    arQueue.enq.valid      := memAccessValid && accessWillRead
    arQueue.enq.bits       := DontCare
    arQueue.enq.bits.burst := 1.U
    arQueue.enq.bits.addr  := Mux(
      s2_uncached,
      access_address,
      access_address >> parameter.lgCacheBlockBytes << parameter.lgCacheBlockBytes
    )
    arQueue.enq.bits.len   := Mux(
      s2_uncached,
      0.U,
      (parameter.cacheBlockBytes * 8 / parameter.loadStoreParameter.dataWidth - 1).U
    )
    arQueue.enq.bits.size  := Mux(s2_uncached, a_size, log2Ceil(parameter.loadStoreParameter.dataWidth / 8).U)
    arQueue.enq.bits.id    := Mux(s2_uncached, a_source, 0.U)
    io.loadStoreAXI.ar <> arQueue.deq

    awQueue.enq.valid      := memAccessValid && !accessWillRead
    awQueue.enq.bits       := DontCare
    awQueue.enq.bits.id    := Mux(s2_uncached, a_source, 0.U)
    awQueue.enq.bits.burst := 1.U
    awQueue.enq.bits.addr  := access_address
    awQueue.enq.bits.len   := 0.U
    awQueue.enq.bits.size  := a_size
    io.loadStoreAXI.aw <> awQueue.deq

    val dataQueue: QueueIO[W] = Queue.io(chiselTypeOf(io.loadStoreAXI.w.bits), cacheDataBeats)
    dataQueue.enq.valid     := memAccessValid && !accessWillRead
    dataQueue.enq.bits      := DontCare
    dataQueue.enq.bits.data := a_data
    dataQueue.enq.bits.strb := a_mask
    dataQueue.enq.bits.last := true.B
    io.loadStoreAXI.w <> dataQueue.deq

//    // Drive APROT Bits
//    tl_out_a.bits.user.lift(AMBAProt).foreach { x =>
//      val user_bit_cacheable = s2_pma.cacheable
//
//      x.privileged := s2_req.dprv === PRV.M.U || user_bit_cacheable
//      // if the address is cacheable, enable outer caches
//      x.bufferable := user_bit_cacheable
//      x.modifiable := user_bit_cacheable
//      x.readalloc := user_bit_cacheable
//      x.writealloc := user_bit_cacheable
//
//      // Following are always tied off
//      x.fetch := false.B
//      x.secure := true.B
//    }

    // Set pending bits for outstanding TileLink transaction
    val a_sel = UIntToOH(a_source, maxUncachedInFlight + mmioOffset) >> mmioOffset
    when(arQueue.enq.fire || awQueue.enq.fire) {
      when(s2_uncached) {
        (a_sel.asBools.zip(uncachedInFlight.zip(uncachedReqs))).foreach { case (s, (f, r)) =>
          when(s) {
            f     := true.B
            r     := s2_req
            r.cmd := Mux(s2_write, Mux(s2_req.cmd === M_PWR, M_PWR, M_XWR), M_XRD)
          }
        }
      }.otherwise {
        cached_grant_wait := true.B
        refill_way        := s2_victim_or_hit_way
      }
    }

    def axiHelper(x: AXI4ChiselBundle, fire: Bool): (Bool, Bool, Bool, UInt) = {
      // same as len
      val count = RegInit(0.U(8.W))
      val first = count === 0.U
      val last: Bool = x match {
        case r: R => r.last
        case w: W => w.last
        case _ => true.B
      }
      val done = last && fire
      when(fire) {
        count := Mux(last, 0.U, count + 1.U)
      }
      (first, last, done, count)
    }

    // grant
    val (d_first, d_last, d_done, d_refill_count) = axiHelper(io.loadStoreAXI.r.bits, io.loadStoreAXI.r.fire)
//    val (d_opc, grantIsUncached, grantIsUncachedData) = {
//      val uncachedGrantOpcodesSansData = Seq(AccessAck, HintAck)
//      val uncachedGrantOpcodesWithData = Seq(AccessAckData)
//      val uncachedGrantOpcodes = uncachedGrantOpcodesWithData ++ uncachedGrantOpcodesSansData
//      val whole_opc = tl_out.d.bits.opcode
//      if (usingDataScratchpad) {
//        assert(!tl_out.d.valid || whole_opc.isOneOf(uncachedGrantOpcodes))
//        // the only valid TL-D messages are uncached, so we can do some pruning
//        val opc = whole_opc(uncachedGrantOpcodes.map(_.getWidth).max - 1, 0)
//        val data = DecodeLogic(opc, uncachedGrantOpcodesWithData, uncachedGrantOpcodesSansData)
//        (opc, true.B, data)
//      } else {
//        (whole_opc, whole_opc.isOneOf(uncachedGrantOpcodes), whole_opc.isOneOf(uncachedGrantOpcodesWithData))
//      }
//    }
    tl_d_data_encoded := encodeData(
      io.loadStoreAXI.r.bits.data,
      // tl_out.d.bits.corrupt && !io.ptw.customCSRs.suppressCorruptOnGrantData && !grantIsUncached
      false.B
    )
    val uncachedRespIdxOH         = (UIntToOH(io.loadStoreAXI.r.bits.id, maxUncachedInFlight + mmioOffset) >> mmioOffset).asUInt
    val grantIsUncachedData       = uncachedRespIdxOH.orR
    val grantIsCached             = !grantIsUncachedData
    val grantIsRefill             = grantIsCached // Writes the data array
    val grantInProgress           = RegInit(false.B)
    val blockProbeAfterGrantCount = RegInit(0.U)
    when(blockProbeAfterGrantCount > 0.U) { blockProbeAfterGrantCount := blockProbeAfterGrantCount - 1.U }
    // !release_state.isOneOf(s_voluntary_writeback, s_voluntary_write_meta, s_voluntary_aw)
    val canAcceptCachedGrant      =
      !Seq(s_voluntary_writeback, s_voluntary_write_meta, s_voluntary_aw).map(_ === release_state).reduce(_ || _)
    io.loadStoreAXI.r.ready := Mux(grantIsCached, canAcceptCachedGrant, true.B)
    val uncachedReadAckIndex = Mux(
      io.loadStoreAXI.r.fire,
      uncachedRespIdxOH,
      0.U
    )
    uncachedResp := Mux1H(uncachedRespIdxOH, uncachedReqs)
    when(io.loadStoreAXI.r.fire) {
      when(grantIsCached) {
        grantInProgress := true.B
        assert(cached_grant_wait, "A GrantData was unexpected by the dcache.")
        when(d_last) {
          cached_grant_wait         := false.B
          grantInProgress           := false.B
          blockProbeAfterGrantCount := (blockProbeAfterGrantCycles - 1).U
          replacer.miss
        }
      }.otherwise {
        // r always has data
        if (!cacheParams.separateUncachedResp) {
          if (!cacheParams.pipelineWayMux)
            s1_data_way         := 1.U << nWays
          s2_req.cmd            := M_XRD
          s2_req.size           := uncachedResp.size
          s2_req.signed         := uncachedResp.signed
          s2_req.tag            := uncachedResp.tag
          s2_req.addr           := {
            require(rowOffBits >= beatOffBits)
            val dontCareBits = s1_paddr >> rowOffBits << rowOffBits
            dontCareBits | uncachedResp.addr(beatOffBits - 1, 0)
          }
          s2_uncached_resp_addr := uncachedResp.addr
        }
      }
    }

    io.loadStoreAXI.b.ready := true.B
    val uncachedWriteIdxOH =
      (UIntToOH(io.loadStoreAXI.b.bits.id, maxUncachedInFlight + mmioOffset) >> mmioOffset).asUInt
    val writeAckUnCached: Bool = uncachedWriteIdxOH.orR
    val uncachedwriteAckIndex = Mux(
      io.loadStoreAXI.b.fire,
      uncachedWriteIdxOH,
      0.U
    )
    val uncachedAckIndex      = uncachedwriteAckIndex | uncachedReadAckIndex
    uncachedAckIndex.asBools.zip(uncachedInFlight).foreach { case (s, f) =>
      when(s) {
        assert(
          f,
          "An uncached AccessAck was unexpected by the dcache."
        ) // TODO must handle Ack coming back on same cycle!
        f := false.B
      }
    }
    when(io.loadStoreAXI.b.fire && !writeAckUnCached) {
      assert(release_ack_wait, "An release ack was unexpected by the dcache.")
      release_ack_wait := false.B
    }

    // Finish TileLink transaction by issuing a GrantAck
    // tl_out.e.valid := tl_out.d.valid && d_first && grantIsCached && canAcceptCachedGrant
    // tl_out.e.bits := edge.GrantAck(tl_out.d.bits)
    // assert(tl_out.e.fire === (tl_out.d.fire && d_first && grantIsCached))

    // data refill
    // note this ready-valid signaling ignores E-channel backpressure, which
    // benignly means the data RAM might occasionally be redundantly written
    dataArb.io.in(1).valid := io.loadStoreAXI.r.valid && grantIsRefill && canAcceptCachedGrant
    when(grantIsRefill && !dataArb.io.in(1).ready) {
      // tl_out.e.valid := false.B
      // tl_out.d.ready := false.B
      io.loadStoreAXI.r.ready := false.B
    }
    if (!usingDataScratchpad) {
      dataArb.io.in(1).bits.write    := true.B
      dataArb.io.in(1).bits.addr     :=
        (s2_vaddr >> idxLSB) << idxLSB |
          (d_refill_count << log2Ceil(parameter.loadStoreParameter.dataWidth / 8))
      dataArb.io.in(1).bits.way_en   := refill_way
      dataArb.io.in(1).bits.wdata    := tl_d_data_encoded
      dataArb.io.in(1).bits.wordMask := ~0.U((rowBytes / subWordBytes).W)
      dataArb.io.in(1).bits.eccMask  := ~0.U((wordBytes / eccBytes).W)
    } else {
      dataArb.io.in(1).bits := dataArb.io.in(0).bits
    }

    // tag updates on refill
    // ignore backpressure from metaArb, which can only be caused by tag ECC
    // errors on hit-under-miss.  failing to write the new tag will leave the
    // line invalid, so we'll simply request the line again later.
//    metaArb.io.in(3).valid := grantIsCached && d_done && !tl_out.d.bits.denied
    metaArb.io.in(3).valid       := grantIsCached && d_done
    metaArb.io.in(3).bits.write  := true.B
    metaArb.io.in(3).bits.way_en := refill_way
    metaArb.io.in(3).bits.idx    := s2_vaddr(idxMSB, idxLSB)
    metaArb.io.in(3).bits.addr   := Cat(io.cpu.req.bits.addr >> untagBits, s2_vaddr(idxMSB, 0))
    metaArb.io.in(3).bits.data   := tECC.encode(
      L1Metadata(s2_req.addr >> tagLSB, s2_new_hit_state).asUInt
    )

    if (!cacheParams.separateUncachedResp) {
      // don't accept uncached grants if there's a structural hazard on s2_data...
      val blockUncachedGrant = Reg(Bool())
      blockUncachedGrant := dataArb.io.out.valid
      when(grantIsUncachedData && (blockUncachedGrant || s1_valid)) {
        io.loadStoreAXI.r.ready := false.B
        // ...but insert bubble to guarantee grant's eventual forward progress
        when(io.loadStoreAXI.r.valid) {
          io.cpu.req.ready            := false.B
          dataArb.io.in(1).valid      := true.B
          dataArb.io.in(1).bits.write := false.B
          blockUncachedGrant          := !dataArb.io.in(1).ready
        }
      }
    }
    ccover(io.loadStoreAXI.r.valid && !io.loadStoreAXI.r.ready, "BLOCK_D", "D$ D-channel blocked")

    // no probe
    metaArb.io.in(6).valid := false.B
    metaArb.io.in(6).bits  := DontCare

    // replacement policy
    s1_victim_way := (if (replacer.perSet && nWays > 1) {
                        val repl_array        = Mem(nSets, UInt(replacer.nBits.W))
                        val s1_repl_idx       = s1_req.addr(idxBits + blockOffBits - 1, blockOffBits)
                        val s2_repl_idx       = s2_vaddr(idxBits + blockOffBits - 1, blockOffBits)
                        val s2_repl_state     = Reg(UInt(replacer.nBits.W))
                        val s2_new_repl_state = replacer.get_next_state(s2_repl_state, OHToUInt(s2_hit_way))
                        val s2_repl_wen       = s2_valid_masked && s2_hit_way.orR && s2_repl_state =/= s2_new_repl_state
                        val s1_repl_state     =
                          Mux(s2_repl_wen && s2_repl_idx === s1_repl_idx, s2_new_repl_state, repl_array(s1_repl_idx))
                        when(s1_valid_not_nacked) { s2_repl_state := s1_repl_state }

                        val waddr = Mux(resetting, flushCounter(idxBits - 1, 0), s2_repl_idx)
                        val wdata = Mux(resetting, 0.U, s2_new_repl_state)
                        val wen   = resetting || s2_repl_wen
                        when(wen) { repl_array(waddr) := wdata }

                        replacer.get_replace_way(s1_repl_state)
                      } else {
                        replacer.way
                      })

    // release
    val (_, _, releaseDone, c_count) = axiHelper(io.loadStoreAXI.w.bits, io.loadStoreAXI.w.fire)
    val releaseRejected              = Wire(Bool())
    val s1_release_data_valid        = RegNext(dataArb.io.in(2).fire)
    val s2_release_data_valid        = RegNext(s1_release_data_valid && !releaseRejected)
    releaseRejected := s2_release_data_valid && !io.loadStoreAXI.w.fire
    val releaseDataBeat =
      Cat(0.U, c_count) + Mux(releaseRejected, 0.U, s1_release_data_valid + Cat(0.U, s2_release_data_valid))
    val s1_release_last: Bool = RegEnable(releaseDataBeat === (refillCycles - 1).U, dataArb.io.in(2).fire)
    val s2_release_last: Bool = RegEnable(s1_release_last, s1_release_data_valid && !releaseRejected)

    when(awState) {
      awQueue.enq.valid     := true.B
      awQueue.enq.bits.addr := releaseAddress >> parameter.lgCacheBlockBytes << parameter.lgCacheBlockBytes
      awQueue.enq.bits.len  := (parameter.cacheBlockBytes * 8 / parameter.loadStoreParameter.dataWidth - 1).U
      awQueue.enq.bits.size := log2Ceil(parameter.loadStoreParameter.dataWidth / 8).U
      awQueue.enq.bits.id   := (mmioOffset - 1).U
    }

    when(s2_release_data_valid) {
      io.loadStoreAXI.w.valid     := true.B
      io.loadStoreAXI.w.bits      := DontCare
      io.loadStoreAXI.w.bits.data := s2_data_corrected
      io.loadStoreAXI.w.bits.strb := (-1.S(io.loadStoreAXI.w.bits.strb.getWidth.W)).asUInt
      io.loadStoreAXI.w.bits.last := s2_release_last
      // tl_out_c.bits.corrupt := inWriteback && s2_data_error_uncorrectable
    }

    val newCoh = ClientMetadata(0.U(2.W))
    releaseWay := s2_victim_or_hit_way

    if (!usingDataScratchpad) {
      when(s2_victimize) {
        assert(s2_valid_flush_line || s2_flush_valid || io.cpu.s2_nack)
        val discard_line = s2_valid_flush_line && s2_req.size(1) || s2_flush_valid && flushing_req.size(1)
        release_state  := Mux(
          s2_victim_dirty && !discard_line,
          s_voluntary_aw,
          s_voluntary_write_meta
        )
        releaseAddress := Cat(s2_victim_tag, s2_req.addr(tagLSB - 1, idxLSB) << idxLSB)
      }

      when(awState) {
        when(awQueue.enq.ready) {
          release_state    := s_voluntary_writeback
          release_ack_wait := true.B
          release_ack_addr := releaseAddress
        }
      }

      when(release_state === s_voluntary_writeback) {
        when(releaseDone) { release_state := s_voluntary_write_meta }
      }
    }

    dataArb.io.in(2).valid         := inWriteback && releaseDataBeat < refillCycles.U
    dataArb.io.in(2).bits          := dataArb.io.in(1).bits
    dataArb.io.in(2).bits.write    := false.B
    dataArb.io.in(2).bits.addr     := (probeIdx(releaseAddress) << blockOffBits).asUInt | (releaseDataBeat(
      log2Ceil(refillCycles) - 1,
      0
    ) << rowOffBits)
    dataArb.io.in(2).bits.wordMask := ~0.U((rowBytes / subWordBytes).W)
    dataArb.io.in(2).bits.eccMask  := ~0.U((wordBytes / eccBytes).W)
    dataArb.io.in(2).bits.way_en   := ~0.U(nWays.W)

    metaArb.io.in(4).valid       := release_state === s_voluntary_write_meta
    metaArb.io.in(4).bits.write  := true.B
    metaArb.io.in(4).bits.way_en := releaseWay
    metaArb.io.in(4).bits.idx    := probeIdx(releaseAddress)
    metaArb.io.in(4).bits.addr   := Cat(io.cpu.req.bits.addr >> untagBits, releaseAddress(idxMSB, 0))
    metaArb.io.in(4).bits.data   := tECC.encode(L1Metadata(releaseAddress >> tagLSB, newCoh).asUInt)
    when(metaArb.io.in(4).fire) { release_state := s_ready }

    // cached response
    (io.cpu.resp.bits: Data).waiveAll :<>= (s2_req: Data).waiveAll
    io.cpu.resp.bits.has_data := s2_read
    io.cpu.resp.bits.replay   := false.B
    io.cpu.s2_uncached        := s2_uncached && !s2_hit
    io.cpu.s2_paddr           := s2_req.addr
    io.cpu.s2_gpa             := s2_tlb_xcpt.gpa
    io.cpu.s2_gpa_is_pte      := s2_tlb_xcpt.gpa_is_pte

    // report whether there are any outstanding accesses.  disregard any
    // slave-port accesses, since they don't affect local memory ordering.
    val s1_isSlavePortAccess = s1_req.no_xcpt
    val s2_isSlavePortAccess = s2_req.no_xcpt
    io.cpu.ordered := !(s1_valid && !s1_isSlavePortAccess || s2_valid && !s2_isSlavePortAccess || cached_grant_wait || uncachedInFlight.asUInt.orR)

    val s1_xcpt_valid = tlb.io.req.valid && !s1_isSlavePortAccess && !s1_nack
    io.cpu.s2_xcpt := Mux(RegNext(s1_xcpt_valid), s2_tlb_xcpt, 0.U.asTypeOf(s2_tlb_xcpt))

    if (usingDataScratchpad) {
      assert(!(s2_valid_masked && (s2_req.cmd === M_XLR || s2_req.cmd === M_XSC)))
    } else {
      // ccover(tl_out.b.valid && !tl_out.b.ready, "BLOCK_B", "D$ B-channel blocked")
    }

    // uncached response
    val s1_uncached_data_word = {
      val word_idx =
        if (log2Ceil(rowBits / 8) == log2Ceil(wordBytes)) 0.U
        else uncachedResp.addr(log2Ceil(rowBits / 8) - 1, log2Ceil(wordBytes))
      val words: Seq[UInt] = grouped(io.loadStoreAXI.r.bits.data, wordBits)
      Mux1H(UIntToOH(word_idx), words)
    }
    val s2_uncached_data_word = RegEnable(s1_uncached_data_word, io.cpu.replay_next)
    val doUncachedResp        = RegNext(io.cpu.replay_next)
    io.cpu.resp.valid  := (s2_valid_hit_pre_data_ecc || doUncachedResp) && !s2_data_error
    io.cpu.replay_next := io.loadStoreAXI.r.fire && grantIsUncachedData && !cacheParams.separateUncachedResp.B
    when(doUncachedResp) {
      assert(!s2_valid_hit)
      io.cpu.resp.bits.replay := true.B
      io.cpu.resp.bits.addr   := s2_uncached_resp_addr
    }

    io.cpu.uncached_resp.map { resp =>
      resp.valid         := io.loadStoreAXI.r.valid && grantIsUncachedData
      resp.bits.tag      := uncachedResp.tag
      resp.bits.size     := uncachedResp.size
      resp.bits.signed   := uncachedResp.signed
      resp.bits.data     := new LoadGen(
        uncachedResp.size,
        uncachedResp.signed,
        uncachedResp.addr,
        s1_uncached_data_word,
        false.B,
        wordBytes
      ).data
      resp.bits.data_raw := s1_uncached_data_word
      when(grantIsUncachedData && !resp.ready) {
        io.loadStoreAXI.r.ready := false.B
      }
    }

    // load data subword mux/sign extension
    val s2_data_word                   = (0 until rowBits by wordBits).map(i => s2_data_uncorrected(wordBits + i - 1, i)).reduce(_ | _)
    val s2_data_word_corrected         =
      (0 until rowBits by wordBits).map(i => s2_data_corrected(wordBits + i - 1, i)).reduce(_ | _)
    val s2_data_word_possibly_uncached =
      Mux(cacheParams.pipelineWayMux.B && doUncachedResp, s2_uncached_data_word, 0.U) | s2_data_word
    val loadgen                        = new LoadGen(s2_req.size, s2_req.signed, s2_req.addr, s2_data_word_possibly_uncached, s2_sc, wordBytes)
    io.cpu.resp.bits.data             := loadgen.data | s2_sc_fail
    io.cpu.resp.bits.data_word_bypass := loadgen.wordData
    io.cpu.resp.bits.data_raw         := s2_data_word
    io.cpu.resp.bits.store_data       := pstore1_data

    // AMOs
    amoalus.map { amoalus =>
      amoalus.zipWithIndex.map { case (amoalu, i) =>
        amoalu.io.mask := pstore1_mask >> (i * (parameter.xLen / 8))
        amoalu.io.cmd  := (if (usingAtomicsInCache) pstore1_cmd else M_XWR)
        amoalu.io.lhs  := s2_data_word >> (i * parameter.xLen)
        amoalu.io.rhs  := pstore1_data >> (i * parameter.xLen)
        amoalu
      }
      pstore1_storegen_data := (if (!usingDataScratchpad) VecInit(amoalus.map(_.io.out)).asUInt
                                else {
                                  val mask = FillInterleaved(8, Mux(s2_correct, 0.U, pstore1_mask))
                                  VecInit(amoalus.map(_.io.out_unmasked)).asUInt & mask | s2_data_word_corrected & ~mask
                                })
    }.getOrElse {
      if (!usingAtomics) {
        assert(!(s1_valid_masked && s1_read && s1_write), "unsupported D$ operation")
      }
    }

    // flushes
    if (!usingDataScratchpad)
      when(RegNext(reset.asBool)) { resetting := true.B }
    val flushCounterNext = flushCounter +& 1.U
    val flushDone        = (flushCounterNext >> log2Ceil(nSets)) === nWays.U
    val flushCounterWrap = flushCounterNext(log2Ceil(nSets) - 1, 0)
    ccover(
      s2_valid_masked && s2_cmd_flush_all && s2_meta_error,
      "TAG_ECC_ERROR_DURING_FENCE_I",
      "D$ ECC error in tag array during cache flush"
    )
    ccover(
      s2_valid_masked && s2_cmd_flush_all && s2_data_error,
      "DATA_ECC_ERROR_DURING_FENCE_I",
      "D$ ECC error in data array during cache flush"
    )
    s1_flush_valid               := metaArb.io
      .in(5)
      .fire && !s1_flush_valid && !s2_flush_valid_pre_tag_ecc && release_state === s_ready && !release_ack_wait
    metaArb.io.in(5).valid       := flushing && !flushed
    metaArb.io.in(5).bits.write  := false.B
    metaArb.io.in(5).bits.idx    := flushCounter(idxBits - 1, 0)
    metaArb.io.in(5).bits.addr   := Cat(io.cpu.req.bits.addr >> untagBits, metaArb.io.in(5).bits.idx << blockOffBits)
    metaArb.io.in(5).bits.way_en := metaArb.io.in(4).bits.way_en
    metaArb.io.in(5).bits.data   := metaArb.io.in(4).bits.data

    // Only flush D$ on FENCE.I if some cached executable regions are untracked.
    if (supports_flush) {
      when(s2_valid_masked && s2_cmd_flush_all) {
        when(!flushed && !io.cpu.s2_kill && !release_ack_wait && !uncachedInFlight.asUInt.orR) {
          flushing     := true.B
          flushing_req := s2_req
        }
      }

      // when(tl_out_a.fire && !s2_uncached) { flushed := false.B }
      when(awQueue.enq.fire && !s2_uncached) { flushed := false.B }
      when(flushing) {
        s1_victim_way := flushCounter >> log2Ceil(nSets)
        when(s2_flush_valid) {
          flushCounter := flushCounterNext
          when(flushDone) {
            flushed                          := true.B
            if (!isPow2(nWays)) flushCounter := flushCounterWrap
          }
        }
        when(flushed && release_state === s_ready && !release_ack_wait) {
          flushing := false.B
        }
      }
    }
    metaArb.io.in(0).valid       := resetting
    metaArb.io.in(0).bits        := metaArb.io.in(5).bits
    metaArb.io.in(0).bits.write  := true.B
    metaArb.io.in(0).bits.way_en := ~0.U(nWays.W)
    metaArb.io.in(0).bits.data   := tECC.encode(L1Metadata(0.U, ClientMetadata(0.U)).asUInt)
    when(resetting) {
      flushCounter := flushCounterNext
      when(flushDone) {
        resetting                        := false.B
        if (!isPow2(nWays)) flushCounter := flushCounterWrap
      }
    }

    // gate the clock
    clock_en_reg := !cacheParams.clockGate.B ||
      // io.ptw.customCSRs.disableDCacheClockGate || // todo: customCSRs?
      io.cpu.keep_clock_enabled ||
      metaArb.io.out.valid || // subsumes resetting || flushing
      // s1Release || s2_release ||
      s1_valid || s2_valid ||
      // tlb_port.req.valid ||
      // s1_tlb_req_valid || s2_tlb_req_valid ||
      pstore1_held || pstore2_valid ||
      release_state =/= s_ready ||
      release_ack_wait || !release_queue_empty ||
      !tlb.io.req.ready ||
      cached_grant_wait || uncachedInFlight.asUInt.orR ||
      lrscCount > 0.U || blockProbeAfterGrantCount > 0.U

    // performance events
    io.cpu.perf.acquire                    := io.loadStoreAXI.ar.fire
    io.cpu.perf.release                    := releaseDone
    io.cpu.perf.grant                      := d_done
    io.cpu.perf.tlbMiss                    := io.ptw.req.fire
    io.cpu.perf.storeBufferEmptyAfterLoad  := !((s1_valid && s1_write) ||
      ((s2_valid && s2_write && !s2_waw_hazard) || pstore1_held) ||
      pstore2_valid)
    io.cpu.perf.storeBufferEmptyAfterStore := !((s1_valid && s1_write) ||
      (s2_valid && s2_write && pstore1_rmw) ||
      ((s2_valid && s2_write && !s2_waw_hazard || pstore1_held) && pstore2_valid))
    io.cpu.perf.canAcceptStoreThenLoad     := !(((s2_valid && s2_write && pstore1_rmw) && (s1_valid && s1_write && !s1_waw_hazard)) ||
      (pstore2_valid && pstore1_valid_likely && (s1_valid && s1_write)))
    io.cpu.perf.canAcceptStoreThenRMW      := io.cpu.perf.canAcceptStoreThenLoad && !pstore2_valid
    io.cpu.perf.canAcceptLoadThenLoad      := !((s1_valid && s1_write && needsRead(
      s1_req
    )) && ((s2_valid && s2_write && !s2_waw_hazard || pstore1_held) || pstore2_valid))
    io.cpu.perf.blocked                    := {
      // stop reporting blocked just before unblocking to avoid overly conservative stalling
      /*val beatsBeforeEnd = outer.crossing match {
        case SynchronousCrossing(_) => 2
        case RationalCrossing(_)    => 1 // assumes 1 < ratio <= 2; need more bookkeeping for optimal handling of >2
        case _: AsynchronousCrossing => 1 // likewise
        case _: CreditedCrossing     => 1 // likewise
      }
      val near_end_of_refill =
        if (cacheBlockBytes / beatBytes <= beatsBeforeEnd) io.loadStoreAXI.r.valid
        else {
          val refill_count = RegInit(0.U((cacheBlockBytes / beatBytes).log2.W))
          when(io.loadStoreAXI.r.fire && grantIsRefill) { refill_count := refill_count + 1.U }
          refill_count >= (cacheBlockBytes / beatBytes - beatsBeforeEnd).U
        }
      cached_grant_wait && !near_end_of_refill*/
      false.B // todo: axi grant wait?
    }

    // report errors
    val (data_error, data_error_uncorrectable, data_error_addr) =
      if (usingDataScratchpad) (s2_valid_data_error, s2_data_error_uncorrectable, s2_req.addr)
      else {
        (
          RegNext(io.loadStoreAXI.w.fire && inWriteback && s2_data_error),
          RegNext(s2_data_error_uncorrectable),
          releaseAddress
        ) // This is stable for a cycle after tl_out_c.fire, so don't need a register
      }
    {
      val error_addr =
        Mux(
          metaArb.io.in(1).valid,
          Cat(s2_first_meta_corrected.tag, metaArb.io.in(1).bits.addr(tagLSB - 1, idxLSB)),
          data_error_addr >> idxLSB
        ) << idxLSB
      io.errors.uncorrectable.foreach { u =>
        u.valid := metaArb.io.in(1).valid && s2_meta_error_uncorrectable || data_error && data_error_uncorrectable
        u.bits  := error_addr
      }
      io.errors.correctable.foreach { c =>
        c.valid := metaArb.io.in(1).valid || data_error
        c.bits  := error_addr
        io.errors.uncorrectable.foreach { u => when(u.valid) { c.valid := false.B } }
      }
      // io.errors.bus.valid := tl_out.d.fire && (tl_out.d.bits.denied || tl_out.d.bits.corrupt)
      io.errors.bus.valid := false.B
      io.errors.bus.bits := Mux(grantIsCached, s2_req.addr >> idxLSB << idxLSB, 0.U)

      ccoverNotScratchpad(io.errors.bus.valid && grantIsCached, "D_ERROR_CACHED", "D$ D-channel error, cached")
      ccover(io.errors.bus.valid && !grantIsCached, "D_ERROR_UNCACHED", "D$ D-channel error, uncached")
    }
//
//    if (usingDataScratchpad) {
//      val data_error_cover = Seq(
//        property.CoverBoolean(!data_error, Seq("no_data_error")),
//        property.CoverBoolean(data_error && !data_error_uncorrectable, Seq("data_correctable_error")),
//        property.CoverBoolean(data_error && data_error_uncorrectable, Seq("data_uncorrectable_error"))
//      )
//      val request_source = Seq(
//        property.CoverBoolean(s2_isSlavePortAccess, Seq("from_TL")),
//        property.CoverBoolean(!s2_isSlavePortAccess, Seq("from_CPU"))
//      )
//
//      property.cover(
//        new property.CrossProperty(
//          Seq(data_error_cover, request_source),
//          Seq(),
//          "MemorySystem;;Scratchpad Memory Bit Flip Cross Covers"
//        )
//      )
//    } else {
//
//      val data_error_type = Seq(
//        property.CoverBoolean(!s2_valid_data_error, Seq("no_data_error")),
//        property.CoverBoolean(s2_valid_data_error && !s2_data_error_uncorrectable, Seq("data_correctable_error")),
//        property.CoverBoolean(s2_valid_data_error && s2_data_error_uncorrectable, Seq("data_uncorrectable_error"))
//      )
//      val data_error_dirty = Seq(
//        property.CoverBoolean(!s2_victim_dirty, Seq("data_clean")),
//        property.CoverBoolean(s2_victim_dirty, Seq("data_dirty"))
//      )
//      val request_source = if (supports_flush) {
//        Seq(property.CoverBoolean(!flushing, Seq("access")), property.CoverBoolean(flushing, Seq("during_flush")))
//      } else {
//        Seq(property.CoverBoolean(true.B, Seq("never_flush")))
//      }
//      val tag_error_cover = Seq(
//        property.CoverBoolean(!s2_meta_error, Seq("no_tag_error")),
//        property.CoverBoolean(s2_meta_error && !s2_meta_error_uncorrectable, Seq("tag_correctable_error")),
//        property.CoverBoolean(s2_meta_error && s2_meta_error_uncorrectable, Seq("tag_uncorrectable_error"))
//      )
//      property.cover(
//        new property.CrossProperty(
//          Seq(data_error_type, data_error_dirty, request_source, tag_error_cover),
//          Seq(),
//          "MemorySystem;;Cache Memory Bit Flip Cross Covers"
//        )
//      )
//    }

  } // leaving gated-clock domain
  val dcacheImpl = withClock(gated_clock) { new DCacheModuleImpl }

  def encodeData(x: UInt, poison: Bool)    =
    VecInit(grouped(x, eccBits).map(dECC.encode(_, if (dECC.canDetect) poison else false.B))).asUInt
  def dummyEncodeData(x:    UInt)          = VecInit(grouped(x, eccBits).map(dECC.swizzle)).asUInt
  def decodeData(x:         UInt)          = grouped(x, dECC.width(eccBits)).map(dECC.decode)
  def eccMask(byteMask:     UInt)          = VecInit(grouped(byteMask, eccBytes).map(_.orR)).asUInt
  def eccByteMask(byteMask: UInt)          = FillInterleaved(eccBytes, eccMask(byteMask))

  def likelyNeedsRead(req: HellaCacheReq): Bool = {
    // req.cmd.isOneOf(M_XWR, M_PFW)
    val res = !Seq(M_XWR, M_PFW).map(_ === req.cmd).reduce(_ || _) || req.size < log2Ceil(eccBytes).U
    assert(!needsRead(req) || res)
    res
  }

  def isRead(cmd:        UInt) = Seq(M_XRD, M_XLR, M_XSC).map(_ === cmd).reduce(_ || _)
  def isWrite(cmd:       UInt) = cmd === M_XWR || cmd === M_PWR || cmd === M_XSC
  def isWriteIntent(cmd: UInt) = isWrite(cmd) || cmd === M_PFW || cmd === M_XLR

  def needsRead(req: HellaCacheReq) =
    isRead(req.cmd) ||
      (isWrite(req.cmd) && (req.cmd === M_PWR || req.size < log2Ceil(eccBytes).U))

  def ccover(
    cond:                Bool,
    label:               String,
    desc:                String
  )(
    implicit sourceInfo: SourceInfo
  ) = {}
  def ccoverNotScratchpad(
    cond:                Bool,
    label:               String,
    desc:                String
  )(
    implicit sourceInfo: SourceInfo
  ) = {}

  require(
    !usingVM || tagLSB <= pgIdxBits,
    s"D$$ set size must not exceed ${1 << (pgIdxBits - 10)} KiB; got ${(nSets * cacheBlockBytes) >> 10} KiB"
  )
  def tagLSB:            Int  = untagBits
  def probeIdx(b: UInt): UInt = b(idxMSB, idxLSB)
}

class StoreGen(typ: UInt, addr: UInt, dat: UInt, maxSize: Int) {
  val size = Wire(UInt(log2Ceil(log2Ceil(maxSize) + 1).W))
  size := typ
  def misaligned: Bool =
    (addr & ((1.U << size) - 1.U)(log2Ceil(maxSize) - 1, 0)).orR

  def mask = {
    var res = 1.U
    for (i <- 0 until log2Ceil(maxSize)) {
      val upper = Mux(addr(i), res, 0.U) | Mux(size >= (i + 1).U, ((BigInt(1) << (1 << i)) - 1).U, 0.U)
      val lower = Mux(addr(i), 0.U, res)
      res = Cat(upper, lower)
    }
    res
  }

  protected def genData(i: Int): UInt =
    if (i >= log2Ceil(maxSize)) dat
    else Mux(size === i.U, Fill(1 << (log2Ceil(maxSize) - i), dat((8 << i) - 1, 0)), genData(i + 1))

  def data     = genData(0)
  def wordData = genData(2)
}

class LoadGen(typ: UInt, signed: Bool, addr: UInt, dat: UInt, zero: Bool, maxSize: Int) {
  private val size = new StoreGen(typ, addr, dat, maxSize).size

  private def genData(logMinSize: Int): UInt = {
    var res = dat
    for (i <- log2Ceil(maxSize) - 1 to logMinSize by -1) {
      val pos     = 8 << i
      val shifted = Mux(addr(i), res(2 * pos - 1, pos), res(pos - 1, 0))
      val doZero  = (i == 0).B && zero
      val zeroed  = Mux(doZero, 0.U, shifted)
      res = Cat(
        Mux(size === i.U || doZero, Fill(8 * maxSize - pos, signed && zeroed(pos - 1)), res(8 * maxSize - 1, pos)),
        zeroed
      )
    }
    res
  }

  def wordData = genData(2)
  def data     = genData(0)
}
