// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.{Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.probe.{Probe, define}
import chisel3.util.experimental.BitSet
import chisel3.util.log2Ceil
import org.chipsalliance.amba.axi4.bundle.{AXI4BundleParameter, AXI4ROIrrevocable, AXI4RWIrrevocable}
import org.chipsalliance.rvdecoderdb.Instruction

object RocketTileParameter {
  implicit def bitSetP: upickle.default.ReadWriter[BitSet] = upickle.default
    .readwriter[String]
    .bimap[BitSet](
      bs => bs.terms.map("b" + _.rawString).mkString("\n"),
      str => if (str.isEmpty) BitSet.empty else BitSet.fromString(str)
    )

  implicit def rwP: upickle.default.ReadWriter[RocketTileParameter] = upickle.default.macroRW[RocketTileParameter]
}

/**
  * Core:
  * isa: parse from isa string
  * vlen: parse from isa string, e.g. rv32imfd_zvl64b_zve32f
  * priv: m|s|u
  *
  * Memory:
  * AXI width
  * PMA config
  *
  * uarch:
  * - clockGate: sync
  * - hartIdLen: log2 hart size, 1
  * - fenceIFlushDCache: flush DCache on fence.i: true
  * - nPMPs: pmp region size, 8
  * - asidBits: ASID length, 0
  * - nBreakpoints: todo, 0
  * - useBPWatch: todo, false
  * - mcontextWidth: todo, 0
  * - scontextWidth: todo, 0
  * - hasBeu: has bus error unit, false
  *
  * - fastLoadByte: todo, true
  * - fastLoadWord: todo, false
  *   - if (fastLoadByte) io.dmem.resp.bits.data(xLen-1, 0)
  *   - else if (fastLoadWord) io.dmem.resp.bits.data_word_bypass(xLen-1, 0)
  *   - else wb_reg_wdata
  *
  * - mulDivLatency:
  * - divUnroll:
  * - divEarlyOut:
  * - divEarlyOutGranularity:
  * - mulUnroll:
  * - mulEarlyOut:
  *
  * - itlbNSets: ???
  * - itlbNWays: ???
  * - itlbNSectors: ???
  * - itlbNSuperpageEntries: ???
  *
  * - usingBTB:
  *   - btbEntries: 28
  *   - btbNMatchBits: 14
  *   - btbUpdatesOutOfOrder: false
  *   - nPages: 6
  *   - nRAS: 6
  * - usingBHT:
  *   - nEntries: 512
  *   - counterLength: 1
  *   - historyLength: 8
  *   - historyBits: 3
  *
  * - icache/dcache size: 16K, 32K
  * - cacheBlockBytes: 32
  * - cache way: 4
  * - cache banksize: 32
  * - iCachePrefetch: false, todo, AXI Hint.
  */
case class RocketTileParameter(
  useAsyncReset:          Boolean,
  clockGate:              Boolean,
  instructionSets:        Set[String],
  priv:                   String,
  hartIdLen:              Int,
  useBPWatch:             Boolean,
  mcontextWidth:          Int,
  scontextWidth:          Int,
  asidBits:               Int,
  resetVectorBits:        Int,
  nBreakpoints:           Int,
  dtlbNWays:              Int,
  dtlbNSets:              Int,
  itlbNSets:              Int,
  itlbNWays:              Int,
  itlbNSectors:           Int,
  itlbNSuperpageEntries:  Int,
  nPTECacheEntries:       Int,
  nL2TLBWays:             Int,
  nL2TLBEntries:          Int,
  paddrBits:              Int,
  cacheBlockBytes:        Int,
  nPMPs:                  Int,
  legal:                  BitSet,
  cacheable:              BitSet,
  read:                   BitSet,
  write:                  BitSet,
  putPartial:             BitSet,
  logic:                  BitSet,
  arithmetic:             BitSet,
  exec:                   BitSet,
  sideEffects:            BitSet,
  btbEntries:             Int,
  btbNMatchBits:          Int,
  btbUpdatesOutOfOrder:   Boolean,
  nPages:                 Int,
  nRAS:                   Int,
  bhtParameter:           Option[BHTParameter],
  mulDivLatency:         Int,
  divUnroll:              Int,
  divEarlyOut:            Boolean,
  divEarlyOutGranularity: Int,
  mulUnroll:              Int,
  mulEarlyOut:            Boolean,
  sfmaLatency:            Int,
  dfmaLatency:            Int,
  divSqrt:                Boolean,
  flushOnFenceI:          Boolean,
  fastLoadByte:           Boolean,
  fastLoadWord:           Boolean,
  dcacheNSets:            Int,
  dcacheNWays:            Int,
  dcacheRowBits:          Int,
  maxUncachedInFlight:    Int,
  separateUncachedResp:   Boolean,
  iCacheNSets:            Int,
  iCacheNWays:            Int,
  iCachePrefetch:         Boolean)
    extends SerializableModuleParameter {

  // calculate
  def usingUser: Boolean = priv.contains("u")

  def usingSupervisor: Boolean = priv.contains("s")

  def vLen: Option[Int] = instructionSets.collectFirst {
    case s"zvl${vlen}b" => vlen.toInt
  }

  // static for now
  def hasBeu:              Boolean = false
  def usingHypervisor:     Boolean = false
  def usingDataScratchpad: Boolean = false
  def nLocalInterrupts:    Int = 0
  def dcacheArbPorts:      Int = 2
  def tagECC:              Option[String] = None
  def dataECC:             Option[String] = None
  def pgLevelBits:         Int = 10 - log2Ceil(xLen / 32)
  def instructions: Seq[Instruction] =
    org.chipsalliance.rvdecoderdb
      .instructions(
        org.chipsalliance.rvdecoderdb.extractResource(getClass.getClassLoader)
      )
      .filter(instruction =>
        (
          instructionSets ++
            // Four mandatory instruction sets.
            Seq("rv_i", "rv_zicsr", "rv_zifencei", "rv_system")
        ).contains(instruction.instructionSet.name)
      )
      .toSeq
      .filter {
        // special case for rv32 pseudo from rv64
        case i if i.pseudoFrom.isDefined && Seq("slli", "srli", "srai").contains(i.name) => true
        case i if i.pseudoFrom.isDefined                                                 => false
        case _                                                                           => true
      }
      .sortBy(i => (i.instructionSet.name, i.name))
  private def hasInstructionSet(setName: String): Boolean =
    instructions.flatMap(_.instructionSets.map(_.name)).contains(setName)
  private def hasInstruction(instName: String): Boolean = instructions.map(_.name).contains(instName)

  def usingBTB: Boolean = btbEntries > 0
  def xLen: Int =
    (hasInstructionSet("rv32_i"), hasInstructionSet("rv64_i")) match {
      case (true, true)   => throw new Exception("cannot support both rv32 and rv64 together")
      case (true, false)  => 32
      case (false, true)  => 64
      case (false, false) => throw new Exception("no basic instruction found.")
    }
  def fLen: Option[Int] =
    (
      hasInstructionSet("rv_f") || hasInstructionSet("rv64_f"),
      hasInstructionSet("rv_d") || hasInstructionSet("rv64_d")
    ) match {
      case (false, false) => None
      case (true, false)  => Some(32)
      case (false, true)  => Some(64)
      case (true, true)   => Some(64)
    }

  def usingVM = hasInstruction("sfence.vma")
  def usingNMI = hasInstructionSet("rv_smrnmi")

  def pgLevels: Int = xLen match {
    case 32 => 2
    case 64 => 3
  }

  def usingAtomics = hasInstructionSet("rv_a") || hasInstructionSet("rv64_a")

  def usingCompressed = hasInstructionSet("rv_c")

  def minFLen: Option[Int] =
    if (hasInstructionSet("rv_zfh") || hasInstructionSet("rv64_zfh") || hasInstructionSet("rv_d_zfh"))
      Some(16)
    else
      fLen

  def rocketParameter: RocketParameter = RocketParameter(
    useAsyncReset,
    clockGate,
    instructionSets,
    vLen.getOrElse(0),
    usingUser,
    usingSupervisor,
    hartIdLen,
    nPMPs,
    asidBits,
    nBreakpoints,
    usingBTB,
    useBPWatch,
    mcontextWidth,
    scontextWidth,
    mulDivLatency,
    divUnroll,
    divEarlyOut,
    divEarlyOutGranularity,
    mulUnroll,
    mulEarlyOut,
    paddrBits,
    cacheBlockBytes,
    hasBeu,
    fastLoadByte,
    fastLoadWord,
    dcacheNSets,
    flushOnFenceI
  )

  def hellaCacheParameter: HellaCacheParameter = HellaCacheParameter(
    useAsyncReset:        Boolean,
    clockGate:            Boolean,
    xLen:                 Int,
    fLen.getOrElse(0):    Int,
    usingVM:              Boolean,
    paddrBits:            Int,
    cacheBlockBytes:      Int,
    dcacheNWays:          Int,
    dcacheNSets:          Int,
    dcacheRowBits:        Int,
    dtlbNSets:            Int,
    dtlbNWays:            Int,
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
    sideEffects:          BitSet
  )

  def hellaCacheArbiterParameter: HellaCacheArbiterParameter = HellaCacheArbiterParameter(
    useAsyncReset:        Boolean,
    xLen:                 Int,
    fLen.getOrElse(0):    Int,
    paddrBits:            Int,
    cacheBlockBytes:      Int,
    dcacheNSets:          Int,
    usingVM:              Boolean,
    separateUncachedResp: Boolean
  )

  def ptwParameter: PTWParameter = PTWParameter(
    useAsyncReset:     Boolean,
    clockGate:         Boolean,
    usingVM:           Boolean,
    usingHypervisor:   Boolean,
    xLen:              Int,
    fLen.getOrElse(0): Int,
    paddrBits:         Int,
    asidBits:          Int,
    pgLevels:          Int,
    nPTECacheEntries:  Int,
    nL2TLBWays:        Int,
    nL2TLBEntries:     Int,
    nPMPs:             Int
  )

  def frontendParameter: FrontendParameter = FrontendParameter(
    useAsyncReset:         Boolean,
    clockGate:             Boolean,
    xLen:                  Int,
    usingAtomics:          Boolean,
    usingDataScratchpad:   Boolean,
    usingVM:               Boolean,
    usingCompressed:       Boolean,
    usingBTB:              Boolean,
    itlbNSets:             Int,
    itlbNWays:             Int,
    itlbNSectors:          Int,
    itlbNSuperpageEntries: Int,
    cacheBlockBytes:       Int,
    iCacheNSets:           Int,
    iCacheNWays:           Int,
    iCachePrefetch:        Boolean,
    btbEntries:            Int,
    btbNMatchBits:         Int,
    btbUpdatesOutOfOrder:  Boolean,
    nPages:                Int,
    nRAS:                  Int,
    nPMPs:                 Int,
    paddrBits:             Int,
    pgLevels:              Int,
    asidBits:              Int,
    bhtParameter:          Option[BHTParameter],
    legal:                 BitSet,
    cacheable:             BitSet,
    read:                  BitSet,
    write:                 BitSet,
    putPartial:            BitSet,
    logic:                 BitSet,
    arithmetic:            BitSet,
    exec:                  BitSet,
    sideEffects:           BitSet
  )

  def fpuParameter: Option[FPUParameter] = fLen.zip(minFLen).map {
    case (fLen, minFLen) =>
      FPUParameter(
        useAsyncReset: Boolean,
        clockGate:     Boolean,
        xLen:          Int,
        fLen:          Int,
        minFLen:       Int,
        sfmaLatency:   Int,
        dfmaLatency:   Int,
        divSqrt:       Boolean,
        hartIdLen:     Int
      )
  }

  def instructionFetchParameter: AXI4BundleParameter = frontendParameter.instructionFetchParameter

  def itimParameter: Option[AXI4BundleParameter] = frontendParameter.itimParameter

  def loadStoreParameter: AXI4BundleParameter = hellaCacheParameter.loadStoreParameter

  def dtimParameter: Option[AXI4BundleParameter] = hellaCacheParameter.dtimParameter
}

class RocketTileInterface(parameter: RocketTileParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())

  val hartid = Flipped(UInt(parameter.hartIdLen.W))
  val resetVector = Input(UInt(parameter.resetVectorBits.W))

  val debug: Bool = Input(Bool())
  val mtip:  Bool = Input(Bool())
  val msip:  Bool = Input(Bool())
  val meip:  Bool = Input(Bool())
  val seip:  Option[Bool] = Option.when(parameter.usingSupervisor)(Input(Bool()))
  val lip:   Vec[Bool] = Input(Vec(parameter.nLocalInterrupts, Bool()))
  val nmi = Option.when(parameter.usingNMI)(Input(Bool()))
  val nmiInterruptVector = Option.when(parameter.usingNMI)(Input(UInt(parameter.resetVectorBits.W)))
  val nmiIxceptionVector = Option.when(parameter.usingNMI)(Input(UInt(parameter.resetVectorBits.W)))
  // TODO: buserror should be handled by NMI
  val buserror: Bool = Input(Bool())
  val wfi:      Bool = Output(Bool())
  val halt:     Bool = Output(Bool())

  val instructionFetchAXI: AXI4ROIrrevocable =
    org.chipsalliance.amba.axi4.bundle.AXI4ROIrrevocable(parameter.instructionFetchParameter)
  val itimAXI: Option[AXI4RWIrrevocable] =
    parameter.itimParameter.map(p => Flipped(org.chipsalliance.amba.axi4.bundle.AXI4RWIrrevocable(p)))

  val loadStoreAXI: AXI4RWIrrevocable =
    org.chipsalliance.amba.axi4.bundle.AXI4RWIrrevocable(parameter.loadStoreParameter)
  val dtimAXI: Option[AXI4RWIrrevocable] =
    parameter.dtimParameter.map(p => Flipped(org.chipsalliance.amba.axi4.bundle.AXI4RWIrrevocable(p)))

  val rocketProbe = Output(Probe(new RocketProbe(parameter.rocketParameter)))
}

class RocketTile(val parameter: RocketTileParameter)
    extends FixedIORawModule(new RocketTileInterface(parameter))
    with SerializableModule[RocketTileParameter] {
  val rocket:     Instance[Rocket] = Instantiate(new Rocket(parameter.rocketParameter))
  val frontend:   Instance[Frontend] = Instantiate(new Frontend(parameter.frontendParameter))
  val hellaCache: Instance[HellaCache] = Instantiate(new HellaCache(parameter.hellaCacheParameter))
  val hellaCacheArbiter: Instance[HellaCacheArbiter] = Instantiate(
    new HellaCacheArbiter(parameter.hellaCacheArbiterParameter)
  )
  val ptw: Instance[PTW] = Instantiate(new PTW(parameter.ptwParameter))
  val fpu: Option[Instance[FPU]] = parameter.fpuParameter.map(fpuParameter => Instantiate(new FPU(fpuParameter)))

  rocket.io.clock := io.clock
  rocket.io.reset := io.reset
  rocket.io.hartid := io.hartid
  rocket.io.interrupts.debug := io.debug
  rocket.io.interrupts.mtip := io.mtip
  rocket.io.interrupts.msip := io.msip
  rocket.io.interrupts.meip := io.meip
  rocket.io.interrupts.seip.foreach(_ := io.seip.get)
  rocket.io.interrupts.lip := io.lip
  rocket.io.interrupts.nmi.foreach { nmi =>
    nmi.rnmi := io.nmi.get
    nmi.rnmi_interrupt_vector := io.nmiInterruptVector.get
    nmi.rnmi_exception_vector := io.nmiIxceptionVector.get
  }
  // @todo make it optional
  rocket.io.buserror := io.buserror
  io.wfi := rocket.io.wfi
  io.loadStoreAXI <> hellaCache.io.loadStoreAXI
  io.dtimAXI.zip(hellaCache.io.dtimAXI).foreach { case (io, hellaCache) => io <> hellaCache }
  io.instructionFetchAXI <> frontend.io.instructionFetchAXI
  io.itimAXI.zip(frontend.io.itimAXI).foreach { case (io, frontend) => io <> frontend }
  // design for halt and beu, only use the halt function for now.
  io.halt := Seq(frontend.io.nonDiplomatic.errors.uncorrectable, hellaCache.io.errors.uncorrectable)
    .flatMap(_.map(_.valid))
    .foldLeft(false.B)(_ || _)

  // rocket core io
  rocket.io.imem <> frontend.io.nonDiplomatic.cpu
  hellaCacheArbiter.io.requestor(0) <> rocket.io.dmem
  rocket.io.ptw <> ptw.io.dpath
  rocket.io.fpu.zip(fpu.map(_.io.core)).foreach { case (core, fpu) => core <> fpu }
  // used by trace module
  rocket.io.bpwatch := DontCare
  // don't use for now, this is design for report the custom cease status.
  // rocket.io.cease
  // it will be used in the future w/ trace support.
  rocket.io.traceStall := false.B

  // frontend io
  frontend.io.clock := io.clock
  frontend.io.reset := io.reset
  frontend.io.resetVector := io.resetVector
  ptw.io.requestor(0) <> frontend.io.nonDiplomatic.ptw

  // hellacache io
  hellaCache.io.clock := io.clock
  hellaCache.io.reset := io.reset
  ptw.io.requestor(1) <> hellaCache.io.ptw
  hellaCache.io.cpu <> hellaCacheArbiter.io.mem

  // ptw io
  ptw.io.clock := io.clock
  ptw.io.reset := io.reset
  hellaCacheArbiter.io.requestor(1) <> ptw.io.mem

  // hellacache arbiter io
  hellaCacheArbiter.io.clock := io.clock
  hellaCacheArbiter.io.reset := io.reset

  fpu.foreach { fpu =>
    fpu.io.clock := io.clock
    fpu.io.reset := io.reset
    // @todo: remove it from FPU.
    fpu.io.cp_req <> DontCare
    fpu.io.cp_resp <> DontCare
  }

  // probe
  define(io.rocketProbe, rocket.io.rocketProbe)
}
