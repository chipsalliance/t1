// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.tile

import chisel3._
import chisel3.experimental.hierarchy.{Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleGenerator, SerializableModuleParameter}
import chisel3.util.experimental.BitSet
import chisel3.util.log2Ceil
import chisel3.probe.{define, Probe, ProbeValue}
import org.chipsalliance.amba.axi4.bundle.{AXI4BundleParameter, AXI4ROIrrevocable, AXI4RWIrrevocable}
import org.chipsalliance.rocketv.{
  BHTParameter,
  FPU,
  FPUParameter,
  FPUProbe,
  Frontend,
  FrontendParameter,
  HellaCache,
  HellaCacheArbiter,
  HellaCacheArbiterParameter,
  HellaCacheParameter,
  PTW,
  PTWParameter,
  Rocket,
  RocketParameter,
  RocketProbe,
  RocketTileParameter
}
import org.chipsalliance.rvdecoderdb.Instruction
import org.chipsalliance.t1.rtl.decoder.T1CustomInstruction
import org.chipsalliance.t1.rtl.vrf.RamType
import org.chipsalliance.t1.rtl.vrf.RamType.{p0rp1w, p0rw, p0rwp1rw}
import org.chipsalliance.t1.rtl.lsu.LSUProbe
import org.chipsalliance.t1.rtl.vrf.VRFProbe
import org.chipsalliance.t1.rtl.{
  LaneAdder,
  LaneAdderParam,
  LaneDiv,
  LaneDivFP,
  LaneDivFPParam,
  LaneDivParam,
  LaneFloat,
  LaneFloatParam,
  LaneMul,
  LaneMulParam,
  LaneProbe,
  LaneShifter,
  LaneShifterParameter,
  LogicParam,
  MaskedLogic,
  OtherUnit,
  OtherUnitParam,
  T1,
  T1Parameter,
  T1Probe,
  VFUInstantiateParameter
}

object T1RocketTileParameter {
  implicit def bitSetP: upickle.default.ReadWriter[BitSet] = upickle.default
    .readwriter[String]
    .bimap[BitSet](
      bs => bs.terms.map("b" + _.rawString).mkString("\n"),
      str => if (str.isEmpty) BitSet.empty else BitSet.fromString(str)
    )

  implicit val vrfRamTypeP: upickle.default.ReadWriter[RamType] = upickle.default.ReadWriter.merge(
    upickle.default.macroRW[p0rw.type],
    upickle.default.macroRW[p0rp1w.type],
    upickle.default.macroRW[p0rwp1rw.type]
  )

  implicit def rwP: upickle.default.ReadWriter[T1RocketTileParameter] = upickle.default.macroRW[T1RocketTileParameter]
}

case class T1RocketTileParameter(
  instructionSets: Seq[String],
  cacheBlockBytes: Int,
  nPMPs:           Int,
  cacheable:       BitSet,
  sideEffects:     BitSet,
  dcacheNSets:     Int,
  dcacheNWays:     Int,
  dcacheRowBits:   Int,
  iCacheNSets:     Int,
  iCacheNWays:     Int,
  iCachePrefetch:  Boolean,
  dLen:            Int,
  vrfBankSize:     Int,
  vrfRamType:      RamType)
    extends SerializableModuleParameter {
  require(instructionSets.count(Seq("Zve32x", "Zve32f").contains) == 1, "at least support one Zve32x or Zve32f")

  val useAsyncReset: Boolean = false
  val clockGate:     Boolean = false

  val paddrBits:              Int                  = xLen
  // TODO: add S in the future
  val priv:                   String               = "m"
  val hartIdLen:              Int                  = 1
  val useBPWatch:             Boolean              = false
  val mcontextWidth:          Int                  = 0
  val scontextWidth:          Int                  = 0
  val asidBits:               Int                  = 0
  val resetVectorBits:        Int                  = paddrBits
  val nBreakpoints:           Int                  = 0
  // TODO: set to 0
  val dtlbNSets:              Int                  = 1
  val dtlbNWays:              Int                  = 32
  val itlbNSets:              Int                  = 1
  val itlbNWays:              Int                  = 32
  val itlbNSectors:           Int                  = 4
  val itlbNSuperpageEntries:  Int                  = 4
  val nPTECacheEntries:       Int                  = 9
  val nL2TLBWays:             Int                  = 1
  val nL2TLBEntries:          Int                  = 0
  // T1 doens't check exception.
  val legal:                  BitSet               = BitSet.fromRange(0, 1 << paddrBits)
  val read:                   BitSet               = BitSet.fromRange(0, 1 << paddrBits)
  val write:                  BitSet               = BitSet.fromRange(0, 1 << paddrBits)
  val putPartial:             BitSet               = BitSet.fromRange(0, 1 << paddrBits)
  val logic:                  BitSet               = BitSet.fromRange(0, 1 << paddrBits)
  val arithmetic:             BitSet               = BitSet.fromRange(0, 1 << paddrBits)
  val exec:                   BitSet               = BitSet.fromRange(0, 1 << paddrBits)
  val btbEntries:             Int                  = 28
  val btbNMatchBits:          Int                  = 14
  val btbUpdatesOutOfOrder:   Boolean              = false
  val nPages:                 Int                  = 6
  val nRAS:                   Int                  = 6
  val bhtParameter:           Option[BHTParameter] = Some(
    BHTParameter(nEntries = 512, counterLength = 1, historyLength = 8, historyBits = 3)
  )
  // TODO: remove it
  val mulDivLatency:          Int                  = 0
  val divUnroll:              Int                  = 1
  val divEarlyOut:            Boolean              = false
  val divEarlyOutGranularity: Int                  = 1
  val mulUnroll:              Int                  = 1
  val mulEarlyOut:            Boolean              = false
  val sfmaLatency:            Int                  = 3
  val dfmaLatency:            Int                  = 4
  val divSqrt:                Boolean              = true
  // TODO: check decoder
  val flushOnFenceI:          Boolean              = true
  val fastLoadByte:           Boolean              = false
  val fastLoadWord:           Boolean              = true
  val maxUncachedInFlight:    Int                  = 1
  val separateUncachedResp:   Boolean              = false

  // calculate
  def usingUser: Boolean = priv.contains("u")

  def usingSupervisor: Boolean = priv.contains("s")

  def vLen: Int = instructionSets.collectFirst { case s"zvl${vlen}b" =>
    vlen.toInt
  }.get

  // static for now
  def hasBeu: Boolean = false

  def usingNMI: Boolean = false

  def usingHypervisor: Boolean = false

  def usingDataScratchpad: Boolean = false

  def nLocalInterrupts: Int = 0

  def dcacheArbPorts: Int = 2

  def tagECC: Option[String] = None

  def dataECC: Option[String] = None

  def pgLevelBits: Int = 10 - log2Ceil(xLen / 32)

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

  def usingVM = hasInstructionSet("sfence.vma")

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
    instructionSets.toSet,
    vLen,
    usingUser,
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
    flushOnFenceI,
    usingT1 = true
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
    useAsyncReset = useAsyncReset:                 Boolean,
    clockGate = clockGate:                         Boolean,
    xLen = xLen:                                   Int,
    usingAtomics = usingAtomics:                   Boolean,
    usingDataScratchpad = usingDataScratchpad:     Boolean,
    usingVM = usingVM:                             Boolean,
    usingCompressed = usingCompressed:             Boolean,
    usingBTB = usingBTB:                           Boolean,
    itlbNSets = itlbNSets:                         Int,
    itlbNWays = itlbNWays:                         Int,
    itlbNSectors = itlbNSectors:                   Int,
    itlbNSuperpageEntries = itlbNSuperpageEntries: Int,
    blockBytes = cacheBlockBytes:                  Int,
    iCacheNSets = iCacheNSets:                     Int,
    iCacheNWays = iCacheNWays:                     Int,
    iCachePrefetch = iCachePrefetch:               Boolean,
    btbEntries = btbEntries:                       Int,
    btbNMatchBits = btbNMatchBits:                 Int,
    btbUpdatesOutOfOrder = btbUpdatesOutOfOrder:   Boolean,
    nPages = nPages:                               Int,
    nRAS = nRAS:                                   Int,
    nPMPs = nPMPs:                                 Int,
    paddrBits = paddrBits:                         Int,
    pgLevels = pgLevels:                           Int,
    asidBits = asidBits:                           Int,
    bhtParameter = bhtParameter:                   Option[BHTParameter],
    legal = legal:                                 BitSet,
    cacheable = cacheable:                         BitSet,
    read = read:                                   BitSet,
    write = write:                                 BitSet,
    putPartial = putPartial:                       BitSet,
    logic = logic:                                 BitSet,
    arithmetic = arithmetic:                       BitSet,
    exec = exec:                                   BitSet,
    sideEffects = sideEffects:                     BitSet
  )

  def fpuParameter: Option[FPUParameter] = fLen.zip(minFLen).map { case (fLen, minFLen) =>
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

  val vfuInstantiateParameter =
    if (instructionSets.contains("Zve32f"))
      VFUInstantiateParameter(
        slotCount = 4,
        logicModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0, 1, 2, 3))
        ),
        aluModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(1)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(2)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(3))
        ),
        shifterModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0, 1, 2, 3))
        ),
        mulModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
        ),
        divModuleParameters = Seq(),
        divfpModuleParameters =
          Seq((SerializableModuleGenerator(classOf[LaneDivFP], LaneDivFPParam(32, 1)), Seq(0, 1, 2, 3))),
        otherModuleParameters = Seq(
          (
            SerializableModuleGenerator(
              classOf[OtherUnit],
              OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
            ),
            Seq(0, 1, 2, 3)
          )
        ),
        floatModuleParameters =
          Seq((SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(0, 1, 2, 3))),
        zvbbModuleParameters = Seq()
      )
    else
      VFUInstantiateParameter(
        slotCount = 4,
        logicModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0, 1, 2, 3))
        ),
        aluModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(1)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(2)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(3))
        ),
        shifterModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0, 1, 2, 3))
        ),
        mulModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
        ),
        divModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneDiv], LaneDivParam(32, 1)), Seq(0, 1, 2, 3))
        ),
        divfpModuleParameters = Seq(),
        otherModuleParameters = Seq(
          (
            SerializableModuleGenerator(
              classOf[OtherUnit],
              OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
            ),
            Seq(0, 1, 2, 3)
          )
        ),
        floatModuleParameters = Seq(),
        zvbbModuleParameters = Seq()
      )

  def t1Parameter: T1Parameter = T1Parameter(
    vLen = vLen,
    dLen = dLen,
    extensions = instructionSets.filter(Seq("Zve32x", "Zve32f").contains),
    // empty for now.
    t1customInstructions = Seq(),
    vrfBankSize = vrfBankSize,
    vrfRamType = vrfRamType,
    vfuInstantiateParameter = vfuInstantiateParameter
  )

  def instructionFetchParameter: AXI4BundleParameter = frontendParameter.instructionFetchParameter

  def itimParameter: Option[AXI4BundleParameter] = frontendParameter.itimParameter

  def loadStoreParameter: AXI4BundleParameter = hellaCacheParameter.loadStoreParameter

  def dtimParameter: Option[AXI4BundleParameter] = hellaCacheParameter.dtimParameter

  def t1HighBandwidthParameter: AXI4BundleParameter = t1Parameter.axi4BundleParameter

  def t1HightOutstandingParameter: AXI4BundleParameter = t1Parameter.axi4BundleParameter.copy(dataWidth = 32)
}

class T1RocketProbe(parameter: T1RocketTileParameter) extends Bundle {
  val rocketProbe: RocketProbe      = Output(new RocketProbe(parameter.rocketParameter))
  val fpuProbe:    Option[FPUProbe] = parameter.fpuParameter.map(param => Output(new FPUProbe(param)))
  val t1Probe:     T1Probe          = Output(new T1Probe(parameter.t1Parameter))
}

class T1RocketTileInterface(parameter: T1RocketTileParameter) extends Bundle {
  val clock       = Input(Clock())
  val reset       = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  // todo: Const
  val hartid      = Flipped(UInt(parameter.hartIdLen.W))
  val resetVector = Input(Const(UInt(parameter.resetVectorBits.W)))

  val debug: Bool         = Input(Bool())
  val mtip:  Bool         = Input(Bool())
  val msip:  Bool         = Input(Bool())
  val meip:  Bool         = Input(Bool())
  val seip:  Option[Bool] = Option.when(parameter.usingSupervisor)(Bool())
  val lip:   Vec[Bool]    = Vec(parameter.nLocalInterrupts, Bool())
  val nmi                = Option.when(parameter.usingNMI)(Bool())
  val nmiInterruptVector = Option.when(parameter.usingNMI)(UInt(parameter.resetVectorBits.W))
  val nmiIxceptionVector = Option.when(parameter.usingNMI)(UInt(parameter.resetVectorBits.W))
  // TODO: buserror should be handled by NMI
  val buserror: Bool = Input(Bool())
  val wfi:      Bool = Output(Bool())
  val halt:     Bool = Output(Bool())

  val instructionFetchAXI: AXI4ROIrrevocable         =
    org.chipsalliance.amba.axi4.bundle.AXI4ROIrrevocable(parameter.instructionFetchParameter)
  val itimAXI:             Option[AXI4RWIrrevocable] =
    parameter.itimParameter.map(p => Flipped(org.chipsalliance.amba.axi4.bundle.AXI4RWIrrevocable(p)))

  val loadStoreAXI: AXI4RWIrrevocable         =
    org.chipsalliance.amba.axi4.bundle.AXI4RWIrrevocable(parameter.loadStoreParameter)
  val dtimAXI:      Option[AXI4RWIrrevocable] =
    parameter.dtimParameter.map(p => Flipped(org.chipsalliance.amba.axi4.bundle.AXI4RWIrrevocable(p)))

  val highBandwidthAXI:   AXI4RWIrrevocable =
    org.chipsalliance.amba.axi4.bundle.AXI4RWIrrevocable(parameter.t1HighBandwidthParameter)
  val highOutstandingAXI: AXI4RWIrrevocable =
    org.chipsalliance.amba.axi4.bundle.AXI4RWIrrevocable(parameter.t1HightOutstandingParameter)

  // TODO: merge it.
  val t1RocketProbe: T1RocketProbe = Output(Probe(new T1RocketProbe(parameter), layers.Verification))
}

class T1RocketTile(val parameter: T1RocketTileParameter)
    extends FixedIORawModule(new T1RocketTileInterface(parameter))
    with SerializableModule[T1RocketTileParameter]
    with Public {
  val rocket:            Instance[Rocket]            = Instantiate(new Rocket(parameter.rocketParameter))
  val frontend:          Instance[Frontend]          = Instantiate(new Frontend(parameter.frontendParameter))
  val hellaCache:        Instance[HellaCache]        = Instantiate(new HellaCache(parameter.hellaCacheParameter))
  val hellaCacheArbiter: Instance[HellaCacheArbiter] = Instantiate(
    new HellaCacheArbiter(parameter.hellaCacheArbiterParameter)
  )
  val ptw:               Instance[PTW]               = Instantiate(new PTW(parameter.ptwParameter))
  val fpu:               Option[Instance[FPU]]       = parameter.fpuParameter.map(fpuParameter => Instantiate(new FPU(fpuParameter)))
  val t1:                Instance[T1]                = Instantiate(new T1(parameter.t1Parameter))

  rocket.io.clock            := io.clock
  rocket.io.reset            := io.reset
  rocket.io.hartid           := io.hartid
  rocket.io.interrupts.debug := io.debug
  rocket.io.interrupts.mtip  := io.mtip
  rocket.io.interrupts.msip  := io.msip
  rocket.io.interrupts.meip  := io.meip
  rocket.io.interrupts.seip.foreach(_ := io.seip.get)
  rocket.io.interrupts.lip   := io.lip
  rocket.io.interrupts.nmi.foreach { nmi =>
    nmi.rnmi                  := io.nmi.get
    nmi.rnmi_interrupt_vector := io.nmiInterruptVector.get
    nmi.rnmi_exception_vector := io.nmiIxceptionVector.get
  }
  // @todo make it optional
  rocket.io.buserror         := io.buserror
  io.wfi                     := rocket.io.wfi
  io.loadStoreAXI <> hellaCache.io.loadStoreAXI
  io.dtimAXI.zip(hellaCache.io.dtimAXI).foreach { case (io, hellaCache) => io <> hellaCache }
  io.instructionFetchAXI <> frontend.io.instructionFetchAXI
  io.itimAXI.zip(frontend.io.itimAXI).foreach { case (io, frontend) => io <> frontend }
  // design for halt and beu, only use the halt function for now.
  io.halt                    := Seq(frontend.io.nonDiplomatic.errors.uncorrectable, hellaCache.io.errors.uncorrectable)
    .flatMap(_.map(_.valid))
    .foldLeft(false.B)(_ || _)

  // rocket core io
  rocket.io.imem <> frontend.io.nonDiplomatic.cpu
  hellaCacheArbiter.io.requestor(0) <> rocket.io.dmem
  rocket.io.ptw <> ptw.io.dpath
  rocket.io.fpu.zip(fpu.map(_.io.core)).foreach { case (core, fpu) => core <> fpu }
  // match connect
  t1.io.issue <> rocket.io.t1.get.issue
  rocket.io.t1.get.retire <> t1.io.retire
  // used by trace module
  rocket.io.bpwatch    := DontCare
  // don't use for now, this is design for report the custom cease status.
  // rocket.io.cease
  // it will be used in the future w/ trace support.
  rocket.io.traceStall := false.B

  // frontend io
  frontend.io.clock       := io.clock
  frontend.io.reset       := io.reset
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
  if (hellaCacheArbiter.io.requestor.size > 1) {
    hellaCacheArbiter.io.requestor(1) <> ptw.io.mem
  } else {
    ptw.io.mem <> DontCare
  }

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
  t1.io.clock := io.clock
  t1.io.reset := io.reset
  io.highBandwidthAXI <> t1.io.highBandwidthLoadStorePort
  io.highOutstandingAXI <> t1.io.indexedLoadStorePort

  // probe
  layer.block(layers.Verification) {
    val probeWire = Wire(new T1RocketProbe(parameter))
    define(io.t1RocketProbe, ProbeValue(probeWire))
    probeWire.rocketProbe := probe.read(rocket.io.rocketProbe)
    probeWire.t1Probe     := probe.read(t1.io.t1Probe)
    probeWire.fpuProbe.foreach { fpuProbe =>
      fpuProbe := probe.read(fpu.get.io.fpuProbe)
    }
  }
}
