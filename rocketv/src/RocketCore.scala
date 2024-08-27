// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.{Instance, Instantiate, instantiable}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.probe.{Probe, ProbeValue, define}
import chisel3.util.circt.ClockGate
import chisel3.util.experimental.decode.DecodeBundle
import chisel3.util.{BitPat, Cat, DecoupledIO, Fill, MuxLookup, PriorityEncoder, PriorityMux, Queue, RegEnable, Valid, log2Ceil, log2Up}
import org.chipsalliance.rocketv.rvdecoderdbcompat.Causes
import org.chipsalliance.rvdecoderdb.Instruction

class FPUScoreboardProbe extends Bundle {
  val fpuSetScoreBoard: Bool = Bool()
  val vectorSetScoreBoard: Bool = Bool()
  val memSetScoreBoard: Bool = Bool()
  val scoreBoardSetAddress: UInt = UInt(5.W)

  val fpuClearScoreBoard: Valid[UInt] = Valid(UInt(5.W))
  val vectorClearScoreBoard: Valid[UInt] = Valid(UInt(5.W))
  val memClearScoreBoard: Valid[UInt] = Valid(UInt(5.W))
}

class RocketProbe(param: RocketParameter) extends Bundle {
  val rfWen: Bool = Bool()
  val rfWaddr: UInt = UInt(param.lgNXRegs.W)
  val rfWdata: UInt = UInt(param.xLen.W)
  // rocket is idle
  val waitWen: Bool = new Bool()
  val waitWaddr: UInt = UInt(param.lgNXRegs.W)
  val isVector: Bool = Bool()
  val idle: Bool = Bool()
  // fpu score board
  val fpuScoreboard: Option[FPUScoreboardProbe] = Option.when(param.usingFPU)(new FPUScoreboardProbe)
}

object RocketParameter {
  implicit def rwP: upickle.default.ReadWriter[RocketParameter] = upickle.default.macroRW[RocketParameter]
}

case class RocketParameter(
                            useAsyncReset: Boolean,
                            clockGate: Boolean,
                            instructionSets: Set[String],
                            vLen: Int,
                            usingUser: Boolean,
                            hartIdLen: Int,
                            nPMPs: Int,
                            asidBits: Int,
                            nBreakpoints: Int,
                            usingBTB: Boolean,
                            useBPWatch: Boolean,
                            mcontextWidth: Int,
                            scontextWidth: Int,
                            mulDivLantency: Int,
                            divUnroll: Int,
                            divEarlyOut: Boolean,
                            divEarlyOutGranularity: Int,
                            mulUnroll: Int,
                            mulEarlyOut: Boolean,
                            paddrBits: Int,
                            cacheBlockBytes: Int,
                            hasBeu: Boolean,
                            fastLoadByte: Boolean,
                            fastLoadWord: Boolean,
                            dcacheNSets: Int,
                            flushOnFenceI: Boolean,
                            usingT1: Boolean
                          )
  extends SerializableModuleParameter {
  // interface to T1
  def usingVector = hasInstructionSet("rv_v")

  // fixed for now
  def usingRVE = false
  def usingDataScratchpad: Boolean = false
  def hasDataECC: Boolean = false
  def vmidBits = 0
  def nPerfCounters = 0

  // calculated
  def lgNXRegs = if (usingRVE) 4 else 5

  def pipelinedMul: Boolean = usingMulDiv && mulUnroll == xLen

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

  def coreInstBytes = (if (usingCompressed) 16 else 32) / 8

  private def hasInstructionSet(setName: String): Boolean =
    instructions.flatMap(_.instructionSets.map(_.name)).contains(setName)

  private def hasInstruction(instName: String): Boolean = instructions.map(_.name).contains(instName)

  def xLen: Int =
    (hasInstructionSet("rv32_i"), hasInstructionSet("rv64_i")) match {
      case (true, true) => throw new Exception("cannot support both rv32 and rv64 together")
      case (true, false) => 32
      case (false, true) => 64
      case (false, false) => throw new Exception("no basic instruction found.")
    }

  def fLen: Option[Int] =
    (
      hasInstructionSet("rv_f") || hasInstructionSet("rv64_f"),
      hasInstructionSet("rv_d") || hasInstructionSet("rv64_d")
    ) match {
      case (false, false) => None
      case (true, false) => Some(32)
      case (false, true) => Some(64)
      case (true, true) => Some(64)
    }

  def minFLen: Option[Int] =
    if (hasInstructionSet("rv_zfh") || hasInstructionSet("rv64_zfh") || hasInstructionSet("rv_d_zfh"))
      Some(16)
    else
      fLen

  def usingMulDiv = hasInstructionSet("rv_m") || hasInstructionSet("rv64_m")

  def usingAtomics = hasInstructionSet("rv_a") || hasInstructionSet("rv64_a")

  def usingVM = hasInstructionSet("sfence.vma")

  def usingSupervisor = hasInstruction("sret")

  // static to false for now
  def usingHypervisor = hasInstructionSet("rv_h") || hasInstructionSet("rv64_h")

  def usingDebug = hasInstructionSet("rv_sdext")

  def usingCompressed = hasInstructionSet("rv_c")

  def usingFPU = fLen.isDefined

  // static to false for now
  def haveCease = hasInstruction("cease")

  // static to false for now
  def usingNMI = hasInstructionSet("rv_smrnmi")

  // calculated parameter
  def fetchWidth: Int = if (usingCompressed) 2 else 1

  def resetVectorLen: Int = {
    val externalLen = paddrBits
    require(externalLen <= xLen, s"External reset vector length ($externalLen) must be <= XLEN ($xLen)")
    require(externalLen <= vaddrBitsExtended, s"External reset vector length ($externalLen) must be <= virtual address bit width ($vaddrBitsExtended)")
    externalLen
  }

  val nLocalInterrupts: Int = 0

  def pgIdxBits: Int = 12
  def pgLevels: Int = if (xLen == 64) 3 /* Sv39 */ else 2 /* Sv32 */
  def pgLevelBits: Int = 10 - log2Ceil(xLen / 32)
  def maxSVAddrBits: Int = pgIdxBits + pgLevels * pgLevelBits
  def maxHypervisorExtraAddrBits: Int = 2
  def hypervisorExtraAddrBits: Int = if (usingHypervisor) maxHypervisorExtraAddrBits else 0
  def maxHVAddrBits: Int = maxSVAddrBits + hypervisorExtraAddrBits
  def vaddrBits: Int = if (usingVM) {
    val v = maxHVAddrBits
    require(v == xLen || xLen > v && v > paddrBits)
    v
  } else {
    // since virtual addresses sign-extend but physical addresses
    // zero-extend, make room for a zero sign bit for physical addresses
    (paddrBits + 1) min xLen
  }
  def vpnBits: Int = vaddrBits - pgIdxBits
  def ppnBits: Int = paddrBits - pgIdxBits
  def vpnBitsExtended: Int = vpnBits + (if (vaddrBits < xLen) (if (usingHypervisor) 1 else 0) + 1 else 0)

  def vaddrBitsExtended: Int = vpnBitsExtended + pgIdxBits
  // btb entries
  def btbEntries: Int = 28
  def bhtHistoryLength: Option[Int] = Some(8)
  def bhtCounterLength: Option[Int] = Some(1)
  def coreInstBits: Int = if (usingCompressed) 16 else 32
  def coreMaxAddrBits: Int = paddrBits max vaddrBitsExtended
  def lgCacheBlockBytes: Int = log2Ceil(cacheBlockBytes)
  def blockOffBits = lgCacheBlockBytes
  // todo: 64 -> dcacheParan.nset
  def idxBits: Int = log2Ceil(dcacheNSets)
  // dCache untage bits
  def untagBits: Int = blockOffBits + idxBits
  def dcacheReqTagBits: Int = 6
  def dcacheArbPorts: Int = 1 + (if(usingVM) 1 else 0) + (if(usingDataScratchpad) 1 else 0)
  def coreDataBits: Int = xLen max fLen.getOrElse(0)
  def coreDataBytes: Int = coreDataBits / 8
  def separateUncachedResp: Boolean = false
  def minPgLevels: Int = {
    val res = xLen match {
      case 32 => 2
      case 64 => 3
    }
    require(pgLevels >= res)
    res
  }

  def maxPAddrBits: Int = {
    require(xLen == 32 || xLen == 64, s"Only XLENs of 32 or 64 are supported, but got $xLen")
    xLen match { case 32 => 34; case 64 => 56 }
  }

  val csrParameter: CSRParameter = CSRParameter(
    useAsyncReset: Boolean,
    vLen: Int,
    xLen: Int,
    fLen.getOrElse(0): Int,
    hartIdLen: Int,
    mcontextWidth: Int,
    scontextWidth: Int,
    asidBits: Int,
    vmidBits: Int,
    nPMPs: Int,
    nPerfCounters: Int,
    paddrBits: Int,
    nBreakpoints: Int,
    usingSupervisor: Boolean,
    usingFPU: Boolean,
    usingUser: Boolean,
    usingVM: Boolean,
    usingCompressed: Boolean,
    usingAtomics: Boolean,
    usingDebug: Boolean,
    usingMulDiv: Boolean,
    usingVector: Boolean
  )
  val decoderParameter = DecoderParameter(
    instructionSets,
    pipelinedMul,
    flushOnFenceI,
    // todo: default = 16?
    minFLen.getOrElse(16),
    xLen
  )
  val iBufParameter: IBufParameter = IBufParameter(
    useAsyncReset,
    xLen,
    usingCompressed,
    vaddrBits,
    btbEntries,
    vaddrBitsExtended,
    bhtHistoryLength,
    bhtCounterLength,
    fetchWidth
  )
  val breakpointUnitParameter: BreakpointUnitParameter = BreakpointUnitParameter(
    nBreakpoints,
    xLen,
    useBPWatch,
    vaddrBits,
    mcontextWidth,
    scontextWidth
  )
  val aluParameter: ALUParameter = ALUParameter(xLen)
  val mulDivParameter: MulDivParameter = MulDivParameter(
    useAsyncReset:          Boolean,
    mulDivLantency:                Int,
    xLen:                  Int,
    divUnroll:              Int,
    divEarlyOut:            Boolean,
    divEarlyOutGranularity: Int,
    mulUnroll:              Int,
    mulEarlyOut:            Boolean,
    decoderParameter:       DecoderParameter
  )
  val mulParameter: Option[PipelinedMultiplierParameter] = Option.when(usingMulDiv && mulUnroll == xLen)(PipelinedMultiplierParameter(
    useAsyncReset: Boolean,
    2,
    xLen:         Int
  ))
}

/** The Interface of [[Rocket]].
  * The [[Rocket]] is the public
  */
class RocketInterface(parameter: RocketParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val hartid = Flipped(UInt(parameter.hartIdLen.W))
  val interrupts = Flipped(new TileInterrupts(parameter.usingSupervisor, parameter.nLocalInterrupts, parameter.usingNMI, parameter.resetVectorLen))
  val buserror = Input(Bool())
  val imem = new FrontendIO(
    parameter.vaddrBitsExtended,
    parameter.vaddrBits,
    parameter.asidBits,
    parameter.btbEntries,
    parameter.bhtHistoryLength,
    parameter.bhtCounterLength,
    parameter.coreInstBits,
    parameter.fetchWidth
  )

  val dmem = new HellaCacheIO(
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

  val ptw = Flipped(
    new DatapathPTWIO(
      parameter.xLen,
      parameter.maxPAddrBits,
      parameter.pgIdxBits: Int,
      parameter.vaddrBits: Int,
      parameter.asidBits: Int,
      parameter.nPMPs,
      parameter.paddrBits: Int
    )
  )
  val fpu = parameter.fLen.map(fLen => Flipped(new FPUCoreIO(parameter.hartIdLen, parameter.xLen, fLen)))
  val t1 = Option.when(parameter.usingT1)(new RocketCoreToT1(parameter.xLen, parameter.vLen))
  val bpwatch = Output(Vec(parameter.nBreakpoints, new BPWatch))
  val cease = Output(Bool())
  val wfi = Output(Bool())
  val traceStall = Input(Bool())
  val rocketProbe = Output(Probe(new RocketProbe(parameter), layers.Verification))
}

/** The [[Rocket]] is the next version of the RocketCore,
 * All micro architectures are from the original RocketCore.
 * The development of [[Rocket]] happens in the T1 project.
 * It will be moved to the standalone pacakge until it get verified.
 *
 * Here are some basic idea of [[Rocket]],
 *  - it should be linkable by providing an verification constraint to other components.
 *  - open expose [[RocketParameter]] and [[RocketInterface]] to users, all internal API are subject to be changed.
 *  - There is no coherent support for the [[Rocket]] until chipsalliance having the CHI interconnect and cache IP.
 *  - The in-tile components contains Frontend, HellaCache, FPU, T1, but the memory subsystem only supports AXI.
 */
@instantiable
class Rocket(val parameter: RocketParameter)
  extends FixedIORawModule(new RocketInterface(parameter))
    with SerializableModule[RocketParameter]
    with ImplicitClock
    with ImplicitReset
    with Public {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset
  val csr: Instance[CSR] = Instantiate(new CSR(parameter.csrParameter))
  val decoder: Instance[Decoder] = Instantiate(new Decoder(parameter.decoderParameter))
  val fpuDecoder: Option[Instance[FPUDecoder]] = Option.when(usingFPU)(Instantiate(new FPUDecoder(parameter.decoderParameter)))
  val instructionBuffer: Instance[IBuf] = Instantiate(new IBuf(parameter.iBufParameter))
  val breakpointUnit: Instance[BreakpointUnit] = Instantiate(new BreakpointUnit(parameter.breakpointUnitParameter))
  val alu: Instance[ALU] = Instantiate(new ALU(parameter.aluParameter))
  val mulDiv: Instance[MulDiv] = Instantiate(new MulDiv(parameter.mulDivParameter))
  val mul: Option[Instance[PipelinedMultiplier]] = parameter.mulParameter.map(p => Instantiate(new PipelinedMultiplier(p)))
  val t1RetireQueue: Option[Queue[T1RdRetire]] = io.t1.map(t1 => Module(new Queue(chiselTypeOf(t1.retire.rd.bits), 32)))

  // compatibility mode.
  object rocketParams {
    def clockGate = parameter.clockGate
    def lgPauseCycles = 5
  };
  def M_XRD     = "b00000".U // int load
  def M_XWR     = "b00001".U // int store
  def M_PFR     = "b00010".U // prefetch with intent to read
  def M_PFW     = "b00011".U // prefetch with intent to write
  def M_XA_SWAP = "b00100".U
  def M_FLUSH_ALL = "b00101".U  // flush all lines
  def M_XLR     = "b00110".U
  def M_XSC     = "b00111".U
  def M_XA_ADD  = "b01000".U
  def M_XA_XOR  = "b01001".U
  def M_XA_OR   = "b01010".U
  def M_XA_AND  = "b01011".U
  def M_XA_MIN  = "b01100".U
  def M_XA_MAX  = "b01101".U
  def M_XA_MINU = "b01110".U
  def M_XA_MAXU = "b01111".U
  def M_PWR     = "b10001".U // partial (masked) store
  def M_SFENCE  = "b10100".U // SFENCE.VMA
  def M_HFENCEV = "b10101".U // HFENCE.VVMA
  def M_HFENCEG = "b10110".U // HFENCE.GVMA
  def M_WOK     = "b10111".U // check write permissions but don't perform a write
  def M_HLVX    = "b10000".U // HLVX instruction

  def lgNXRegs = parameter.lgNXRegs
  def coreDataBytes = parameter.coreDataBytes
  def regAddrMask: Int = (1 << lgNXRegs) - 1
  def xLen: Int = parameter.xLen
  def fLen: Option[Int] = parameter.fLen
  def vaddrBits: Int = parameter.vaddrBits
  def vaddrBitsExtended: Int = parameter.vaddrBitsExtended
  def btbEntries: Int = parameter.btbEntries
  def bhtHistoryLength: Option[Int] = parameter.bhtHistoryLength
  def bhtCounterLength: Option[Int] = parameter.bhtCounterLength
  def nBreakpoints: Int = parameter.nBreakpoints
  def usingAtomics: Boolean = parameter.usingAtomics
  def usingMulDiv: Boolean = parameter.usingMulDiv
  def usingVector: Boolean = parameter.usingVector
  def pipelinedMul: Boolean = parameter.pipelinedMul
  def usingCompressed: Boolean = parameter.usingCompressed
  def usingFPU: Boolean = parameter.usingFPU
  def usingVM: Boolean = parameter.usingVM
  def fastLoadByte: Boolean = parameter.fastLoadByte
  def fastLoadWord: Boolean = parameter.fastLoadWord
  def hypervisorExtraAddrBits: Int = parameter.hypervisorExtraAddrBits
  def usingHypervisor: Boolean = parameter.usingHypervisor
  def flushOnFenceI: Boolean = parameter.flushOnFenceI
  def usingBTB: Boolean = parameter.usingBTB
  def coreInstBytes: Int = parameter.coreInstBytes
  def fetchWidth: Int = parameter.fetchWidth
  def minFLen: Int = parameter.minFLen.getOrElse(0)
  def hasDataECC: Boolean = parameter.hasDataECC

  // Signal outside from internal clock domain.

  val longLatencyStall = Reg(Bool())
  val idRegPause = Reg(Bool())
  val imemMightRequestReg = Reg(Bool())
  val clockEnable = WireDefault(true.B)
  val clockEnableReg = RegInit(true.B)
  val gatedClock =
    Option.when(rocketParams.clockGate)(ClockGate(io.clock, clockEnable)).getOrElse(io.clock)

  csr.io.clock := gatedClock
  csr.io.reset := io.reset
  instructionBuffer.io.clock := gatedClock
  instructionBuffer.io.reset := io.reset
  mulDiv.io.clock := gatedClock
  mulDiv.io.reset := io.reset
  mul.foreach(_.io.clock := gatedClock)
  mul.foreach(_.io.reset := io.reset)
  // leaving gated-clock domain
  val gatedDomain = withClock(gatedClock)(new Gated)

  class Gated {
    // performance counters
    def pipelineIDToWB[T <: Data](x: T): T = RegEnable(RegEnable(RegEnable(x, !ctrlKilled), exPcValid), memPcValid)

    // RF is not a Module.
    val rf = new RegFile(regAddrMask, xLen)

    // wire definations.

    val idDecodeOutput: DecodeBundle = Wire(chiselTypeOf(decoder.io.output))

    val exRegExceptionInterrupt: Bool = Reg(Bool())
    val exRegException:          Bool = Reg(Bool())
    val exRegValid:              Bool = Reg(Bool())
    val exRegRVC:                Bool = Reg(Bool())
    val exRegBTBResponse:        BTBResp = Reg(new BTBResp(vaddrBits, btbEntries, fetchWidth, bhtHistoryLength, bhtCounterLength))
    val exRegFlushPipe:          Bool = Reg(Bool())
    val exRegLoadUse:            Bool = Reg(Bool())
    val exRegCause:              UInt = Reg(UInt())
    val exRegReplay:             Bool = Reg(Bool())
    val exRegPC:                 UInt = Reg(UInt())
    // TODO: add real width here.
    val exRegMemSize: UInt = Reg(UInt())
    // Option.when(usingHypervisor)
    val exRegHLS:            Bool = Reg(Bool())
    val exRegInstruction:    UInt = Reg(UInt())
    val exRegRawInstruction: UInt = Reg(UInt())
    // TODO: what's this?
    val exRegWphit:        Vec[Bool] = Reg(Vec(nBreakpoints, Bool()))
    val exRegDecodeOutput: DecodeBundle = Reg(chiselTypeOf(decoder.io.output))

    val memRegExceptionInterrupt = Reg(Bool())
    val memRegValid = Reg(Bool())
    val memRegRVC = Reg(Bool())
    val memRegBTBResponse = Reg(new BTBResp(
      vaddrBits,
      btbEntries,
      fetchWidth,
      bhtHistoryLength,
      bhtCounterLength
    ))
    val memRegException = Reg(Bool())
    val memRegReplay = Reg(Bool())
    val memRegFlushPipe = Reg(Bool())
    val memRegCause = Reg(UInt())
    val memRegSlowBypass = Reg(Bool())
    val memRegLoad = Reg(Bool())
    val memRegStore = Reg(Bool())
    val memRegSfence = Reg(Bool())
    val memRegPc = Reg(UInt())
    val memRegInstruction = Reg(UInt())
    val memRegMemSize = Reg(UInt())
    val memRegDecodeOutput: DecodeBundle = Reg(chiselTypeOf(decoder.io.output))

    /** virtualization mode? */
    val memRegHlsOrDv = Reg(Bool())
    val memRegRawInstruction = Reg(UInt())
    val memRegWdata = Reg(UInt())
    val memRegRS2 = Reg(UInt())
    val memBranchTaken = Reg(Bool())
    val takePcMem = Wire(Bool())
    val memRegWphit = Reg(Vec(nBreakpoints, Bool()))

    val wbRegValid = Reg(Bool())
    val wbRegException = Reg(Bool())
    val wbRegReplay = Reg(Bool())
    val wbRegFlushPipe = Reg(Bool())
    val wbRegCause = Reg(UInt())
    val wbRegSfence = Reg(Bool())
    val wbRegPc = Reg(UInt())
    val wbRegDecodeOutput: DecodeBundle = Reg(chiselTypeOf(decoder.io.output))
    val wbRegMemSize = Reg(UInt())
    val wbRegHlsOrDv = Reg(Bool())
    val wbRegHfenceV = Reg(Bool())
    val wbRegHfenceG = Reg(Bool())
    val wbRegInstruction = Reg(UInt())
    val wbRegRawInstruction = Reg(UInt())
    val wbRegWdata = Reg(UInt())
    val wbRegRS2 = Reg(UInt())
    val wbRegWphit = Reg(Vec(nBreakpoints, Bool()))
    val takePcWb = Wire(Bool())

    val takePcMemWb = takePcWb || takePcMem
    val takePc = takePcMemWb

    // From IBUF to ID
    instructionBuffer.io.imem <> io.imem.resp
    val instructionBufferOut = instructionBuffer.io.inst.head
    // TODO: does these really has its meaning? I don't think so:(
    val idExpandedInstruction: ExpandedInstruction = instructionBufferOut.bits.inst
    val idRawInstruction:      UInt = instructionBufferOut.bits.raw
    val idInstruction:         UInt = idExpandedInstruction.bits
    idDecodeOutput := decoder.io.output
    instructionBuffer.io.kill := takePc
    // 5. Instruction goes to Rocket Decoder
    decoder.io.instruction := idInstruction

    // Optional circuit: Optional add this circuit for RVE.
    def decodeReg(x: UInt): (Bool, UInt) = ((if (x.getWidth - 1 < lgNXRegs) 0.U else x(x.getWidth - 1, lgNXRegs)).asBool, x(lgNXRegs - 1, 0))
    val (idRaddr3Illegal: Bool, idRaddr3: UInt) = decodeReg(idExpandedInstruction.rs3)
    val (idRaddr2Illegal: Bool, idRaddr2: UInt) = decodeReg(idExpandedInstruction.rs2)
    val (idRaddr1Illegal: Bool, idRaddr1: UInt) = decodeReg(idExpandedInstruction.rs1)
    val (idWaddrIllegal: Bool, idWaddr: UInt) = decodeReg(idExpandedInstruction.rd)

    val idLoadUse:  Bool = Wire(Bool())
    val idRegFence: Bool = RegInit(false.B)
    // TODO: T1 needs to access RS1 and RS2 under some instructions.
    //       FP goes to a different path, parameter.decoderParameter.rfs1 is never used...
    val idRen:      Seq[Bool] = IndexedSeq(idDecodeOutput(parameter.decoderParameter.rxs1), idDecodeOutput(parameter.decoderParameter.rxs2))
    val idRaddr:    Seq[UInt] = IndexedSeq(idRaddr1, idRaddr2)
    // 6. Read RF out.
    val idRs:       Seq[UInt] = idRaddr.map(rf.read)
    // instruction get killed at exec stage if true.
    val ctrlKilled: Bool = Wire(Bool())

    // TODO: additional decode out?

    def isOneOf(x:UInt, s: Seq[UInt]): Bool = VecInit(s.map(x === _)).asUInt.orR

    val idCsrEn:             Bool = isOneOf(idDecodeOutput(parameter.decoderParameter.csr), Seq(parameter.csrParameter.S, parameter.csrParameter.C, parameter.csrParameter.W))
    val idSystemInstruction: Bool = idDecodeOutput(parameter.decoderParameter.csr) === parameter.csrParameter.I
    val idCsrRen:            Bool = isOneOf(idDecodeOutput(parameter.decoderParameter.csr), Seq(parameter.csrParameter.S, parameter.csrParameter.C)) && idExpandedInstruction.rs1 === 0.U
    val idCsr =
      Mux(idSystemInstruction && idDecodeOutput(parameter.decoderParameter.mem), parameter.csrParameter.N, Mux(idCsrRen, parameter.csrParameter.R, idDecodeOutput(parameter.decoderParameter.csr)))
    val idCsrFlush =
      idSystemInstruction ||
        (idCsrEn && !idCsrRen && csr.io.decode(0).writeFlush) ||
        Option.when(parameter.usingVector)(idDecodeOutput(parameter.decoderParameter.vectorCSR)).getOrElse(false.B)
    val idRfIllegal: Bool =
      idRaddr2Illegal && idDecodeOutput(parameter.decoderParameter.rxs2) ||
        idRaddr1Illegal && idDecodeOutput(parameter.decoderParameter.rxs1) ||
        idWaddrIllegal && idDecodeOutput(parameter.decoderParameter.wxd)
    val idCsrIllegalRW: Bool =
      idCsrEn && (csr.io.decode(0).readIllegal || !idCsrRen && csr.io.decode(0).writeIllegal)
    val idSystemIllegal: Bool =
      !instructionBufferOut.bits.rvc && (idSystemInstruction && csr.io.decode(0).systemIllegal)

    val idAtomicIllegal: Option[Bool] =
      Option.when(usingAtomics)(idDecodeOutput(parameter.decoderParameter.amo) && !csr.io.status.isa('a' - 'a'))
    val idMulDivIllegal: Option[Bool] =
      Option.when(usingMulDiv)(
        Option.when(pipelinedMul)(idDecodeOutput(parameter.decoderParameter.mul)).getOrElse(false.B) ||
          idDecodeOutput(parameter.decoderParameter.div) && !csr.io.status.isa('m' - 'a')
      )
    val idCompressIllegal: Option[Bool] =
      Option.when(usingCompressed)(instructionBufferOut.bits.rvc && !csr.io.status.isa('c' - 'a'))
    val idFpIllegal: Option[Bool] =
      io.fpu.map(fpu => idDecodeOutput(parameter.decoderParameter.fp) && (csr.io.decode(0).fpIllegal || fpu.illegal_rm))
    val idDpIllegal: Option[Bool] = Option.when(usingFPU)(idDecodeOutput(parameter.decoderParameter.dp) && !csr.io.status.isa('d' - 'a'))

    // TODO: vector illegal:
    //       - vector is not enabled but a vector instruction is decoded.
    val idIllegalInstruction: Bool =
      !idDecodeOutput(parameter.decoderParameter.isLegal) ||
        idRfIllegal ||
        idCsrIllegalRW ||
        idSystemIllegal ||
        idMulDivIllegal.getOrElse(false.B) ||
        idAtomicIllegal.getOrElse(false.B) ||
        idFpIllegal.getOrElse(false.B) ||
        idDpIllegal.getOrElse(false.B) ||
        idCompressIllegal.getOrElse(false.B)
    val idVirtualInstruction: Bool =
      idDecodeOutput(parameter.decoderParameter.isLegal) &&
        (
          (idCsrEn &&
            !(!idCsrRen && csr.io.decode(0).writeIllegal) &&
            csr.io.decode(0).virtualAccessIllegal) || (
            !instructionBufferOut.bits.rvc &&
              idSystemInstruction &&
              csr.io.decode(0).virtualSystemIllegal
            )
          )

    // stall decode for fences (now, for AMO.rl; later, for AMO.aq and FENCE)
    val idAmoAquire:  Bool = idInstruction(26)
    val idAmoRelease: Bool = idInstruction(25)
    // TODO: what's this?
    val idFenceSucc:  UInt = idInstruction(23, 20)
    val idFenceNext:  Bool = idDecodeOutput(parameter.decoderParameter.fence) || idDecodeOutput(parameter.decoderParameter.amo) && idAmoAquire
    val idMemoryBusy: Bool = !io.dmem.ordered || io.dmem.req.valid
    val idDoFence =
      idMemoryBusy &&
        (idDecodeOutput(parameter.decoderParameter.amo) && idAmoRelease ||
          idDecodeOutput(parameter.decoderParameter.fenceI) ||
          idRegFence && idDecodeOutput(parameter.decoderParameter.mem))

    // TODO: if vector is non-empty, don't take breakpoint.
    breakpointUnit.io.status := csr.io.status
    breakpointUnit.io.bp := csr.io.bp
    breakpointUnit.io.pc := instructionBuffer.io.pc
    breakpointUnit.io.ea := memRegWdata
    breakpointUnit.io.mcontext := csr.io.mcontext
    breakpointUnit.io.scontext := csr.io.scontext

    val idException0 = instructionBufferOut.bits.xcpt0
    val idException1 = instructionBufferOut.bits.xcpt1
    val (idException, idCause) = checkExceptions(
      List(
        (csr.io.interrupt, csr.io.interruptCause),
        (breakpointUnit.io.debug_if, parameter.csrParameter.debugTriggerCause.U),
        (breakpointUnit.io.xcpt_if, Causes.breakpoint.U),
        (idException0.pf, Causes.fetch_page_fault.U),
        (idException0.gf, Causes.fetch_guest_page_fault.U),
        (idException0.ae, Causes.fetch_access.U),
        (idException1.pf, Causes.fetch_page_fault.U),
        (idException1.gf, Causes.fetch_guest_page_fault.U),
        (idException1.ae, Causes.fetch_access.U),
        (idVirtualInstruction, Causes.virtual_instruction.U),
        (idIllegalInstruction, Causes.illegal_instruction.U)
      )
    )

    val idCoverCauses: Seq[(Int, String)] = List(
      (parameter.csrParameter.debugTriggerCause, "DEBUG_TRIGGER"),
      (Causes.breakpoint, "BREAKPOINT"),
      (Causes.fetch_access, "FETCH_ACCESS"),
      (Causes.illegal_instruction, "ILLEGAL_INSTRUCTION")
    ) ++ Option.when(usingVM)((Causes.fetch_page_fault, "FETCH_PAGE_FAULT"))

    // Bypass signals
    val dcacheBypassData: UInt =
      if (fastLoadByte) io.dmem.resp.bits.data(xLen - 1, 0)
      else if (fastLoadWord) io.dmem.resp.bits.data_word_bypass(xLen - 1, 0)
      else wbRegWdata
    // detect bypass opportunities
    val exWaddr:  UInt = exRegInstruction(11, 7) & regAddrMask.U
    val memWaddr: UInt = memRegInstruction(11, 7) & regAddrMask.U
    val wbWaddr:  UInt = wbRegInstruction(11, 7) & regAddrMask.U
    val bypassSources: Seq[(Bool, UInt, UInt)] = IndexedSeq(
      (true.B, 0.U, 0.U), // treat reading x0 as a bypass
      (exRegValid && exRegDecodeOutput(parameter.decoderParameter.wxd), exWaddr, memRegWdata),
      (memRegValid && memRegDecodeOutput(parameter.decoderParameter.wxd) && !memRegDecodeOutput(parameter.decoderParameter.mem), memWaddr, wbRegWdata),
      (memRegValid && memRegDecodeOutput(parameter.decoderParameter.wxd), memWaddr, dcacheBypassData)
    )
    val idBypassSources: Seq[Seq[Bool]] = idRaddr.map(raddr => bypassSources.map(s => s._1 && s._2 === raddr))

    // execute stage
    val bypassMux:     Vec[UInt] = VecInit(bypassSources.map(_._3))
    val exRegRsBypass: Vec[Bool] = Reg(Vec(idRaddr.size, Bool()))
    val exRegRsLSB:    Vec[UInt] = Reg(Vec(idRaddr.size, UInt(log2Ceil(bypassSources.size).W)))
    val exRegRsMSB:    Vec[UInt] = Reg(Vec(idRaddr.size, UInt()))
    val exRs: Seq[UInt] = Seq.tabulate(idRaddr.size)(i =>
      Mux(exRegRsBypass(i), bypassMux(exRegRsLSB(i)), Cat(exRegRsMSB(i), exRegRsLSB(i)))
    )
    val exImm: SInt = ImmGen(exRegDecodeOutput(parameter.decoderParameter.selImm), exRegInstruction)

    def A1_RS1 = 1.U(2.W)
    def A1_PC = 2.U(2.W)

    def A2_ZERO = 0.U(2.W)
    def A2_SIZE = 1.U(2.W)
    def A2_RS2 = 2.U(2.W)
    def A2_IMM = 3.U(2.W)

    val exOp1: SInt =
      MuxLookup(exRegDecodeOutput(parameter.decoderParameter.selAlu1), 0.S)(Seq(A1_RS1 -> exRs(0).asSInt, A1_PC -> exRegPC.asSInt))
    val exOp2: SInt = MuxLookup(exRegDecodeOutput(parameter.decoderParameter.selAlu2), 0.S)(
      Seq(A2_RS2 -> exRs(1).asSInt, A2_IMM -> exImm, A2_SIZE -> Mux(exRegRVC, 2.S, 4.S))
    )

    alu.io.dw := exRegDecodeOutput(parameter.decoderParameter.aluDoubleWords)
    alu.io.fn := exRegDecodeOutput(parameter.decoderParameter.aluFn)
    alu.io.in2 := exOp2.asUInt
    alu.io.in1 := exOp1.asUInt

    // multiplier and divider
    // TODO: waive them if !usingMulDiv
    mulDiv.io.req.valid := exRegValid && Option.when(usingMulDiv)(exRegDecodeOutput(parameter.decoderParameter.div)).getOrElse(false.B)
    mulDiv.io.req.bits.dw := exRegDecodeOutput(parameter.decoderParameter.aluDoubleWords)
    mulDiv.io.req.bits.fn := exRegDecodeOutput(parameter.decoderParameter.aluFn)
    mulDiv.io.req.bits.in1 := exRs(0)
    mulDiv.io.req.bits.in2 := exRs(1)
    mulDiv.io.req.bits.tag := exWaddr
    mul.foreach { m =>
      m.io.req.valid := exRegValid && exRegDecodeOutput(parameter.decoderParameter.mul)
      m.io.req.bits := mulDiv.io.req.bits
    }

    exRegValid := !ctrlKilled
    exRegReplay := !takePc && instructionBufferOut.valid && instructionBufferOut.bits.replay
    exRegException := !ctrlKilled && idException
    exRegExceptionInterrupt := !takePc && instructionBufferOut.valid && csr.io.interrupt

    // ID goes to EX
    when(!ctrlKilled) {
      exRegDecodeOutput := idDecodeOutput
      exRegRVC := instructionBufferOut.bits.rvc
      exRegDecodeOutput(parameter.decoderParameter.csr) := idCsr
      when(idDecodeOutput(parameter.decoderParameter.fence) && idFenceSucc === 0.U) { idRegPause := true.B }
      when(idFenceNext) { idRegFence := true.B }
      when(idException) { // pass PC down ALU writeback pipeline for badaddr
        exRegDecodeOutput(parameter.decoderParameter.aluFn) := parameter.aluParameter.FN_ADD
        exRegDecodeOutput(parameter.decoderParameter.aluDoubleWords) := true.B
        exRegDecodeOutput(parameter.decoderParameter.selAlu1) := A1_RS1 // badaddr := instruction
        exRegDecodeOutput(parameter.decoderParameter.selAlu2) := A2_ZERO
        when(idException1.asUInt.orR) { // badaddr := PC+2
          exRegDecodeOutput(parameter.decoderParameter.selAlu1) := A1_PC
          exRegDecodeOutput(parameter.decoderParameter.selAlu2) := A2_SIZE
          exRegRVC := true.B
        }
        when(breakpointUnit.io.xcpt_if || idException0.asUInt.orR) { // badaddr := PC
          exRegDecodeOutput(parameter.decoderParameter.selAlu1) := A1_PC
          exRegDecodeOutput(parameter.decoderParameter.selAlu2) := A2_ZERO
        }
      }
      exRegFlushPipe := idDecodeOutput(parameter.decoderParameter.fenceI) || idCsrFlush
      exRegLoadUse := idLoadUse

      exRegHLS :=
        usingHypervisor.B &&
          idSystemInstruction &&
          isOneOf(idDecodeOutput(parameter.decoderParameter.memCommand), Seq(M_XRD, M_XWR, M_HLVX))
      exRegMemSize := Mux(usingHypervisor.B && idSystemInstruction, idInstruction(27, 26), idInstruction(13, 12))
      when(isOneOf(idDecodeOutput(parameter.decoderParameter.memCommand), Seq(M_SFENCE, M_HFENCEV, M_HFENCEG, M_FLUSH_ALL))       ) {
        exRegMemSize := Cat(idRaddr2 =/= 0.U, idRaddr1 =/= 0.U)
      }
      when(idDecodeOutput(parameter.decoderParameter.memCommand) === M_SFENCE && csr.io.status.v) {
        exRegDecodeOutput(parameter.decoderParameter.memCommand) := M_HFENCEV
      }

      if (flushOnFenceI) {
        when(idDecodeOutput(parameter.decoderParameter.fenceI)) {
          exRegMemSize := 0.U
        }
      }

      Seq.tabulate(idRaddr.size) { i =>
        val doBypass = idBypassSources(i).reduce(_ || _)
        val bypassSource = PriorityEncoder(idBypassSources(i))
        exRegRsBypass(i) := doBypass
        exRegRsLSB(i) := bypassSource
        exRegRsMSB(i) := idRs(i) >> log2Ceil(bypassSources.size)
        when(idRen(i) && !doBypass) {
          exRegRsLSB(i) := idRs(i)(log2Ceil(bypassSources.size) - 1, 0)
        }
      }
      when(idIllegalInstruction || idVirtualInstruction) {
        val inst = Mux(instructionBufferOut.bits.rvc, idRawInstruction(15, 0), idRawInstruction)
        exRegRsBypass(0) := false.B
        exRegRsLSB(0) := inst(log2Ceil(bypassSources.size) - 1, 0)
        exRegRsMSB(0) := inst >> log2Ceil(bypassSources.size)
      }
    }
    // ID goes to EX but with interrupt or replay
    when(!ctrlKilled || csr.io.interrupt || instructionBufferOut.bits.replay) {
      exRegCause := idCause
      exRegInstruction := idInstruction
      exRegRawInstruction := idRawInstruction
      exRegPC := instructionBuffer.io.pc
      exRegBTBResponse := instructionBuffer.io.btb_resp
      exRegWphit := breakpointUnit.io.bpwatch.map { bpw => bpw.ivalid(0) }
    }
    // replay inst in ex stage?
    val exPcValid:    Bool = exRegValid || exRegReplay || exRegExceptionInterrupt
    val wbDcacheMiss: Bool = wbRegDecodeOutput(parameter.decoderParameter.mem) && !io.dmem.resp.valid
    val replayExStructural: Bool = exRegDecodeOutput(parameter.decoderParameter.mem) && !io.dmem.req.ready || Option
      .when(usingMulDiv)(exRegDecodeOutput(parameter.decoderParameter.div))
      .getOrElse(false.B) && !mulDiv.io.req.ready
    val replayExLoadUse: Bool = wbDcacheMiss && exRegLoadUse
    val replayEx:        Bool = exRegReplay || (exRegValid && (replayExStructural || replayExLoadUse))
    val ctrlKillx:       Bool = takePcMemWb || replayEx || !exRegValid
    // detect 2-cycle load-use delay for LB/LH/SC
    val exSlowBypass: Bool = exRegDecodeOutput(parameter.decoderParameter.memCommand) === M_XSC || exRegMemSize < 2.U
    val exSfence: Bool =
      usingVM.B &&
        exRegDecodeOutput(parameter.decoderParameter.mem) &&
        (exRegDecodeOutput(parameter.decoderParameter.memCommand) === M_SFENCE ||
          exRegDecodeOutput(parameter.decoderParameter.memCommand) === M_HFENCEV ||
          exRegDecodeOutput(parameter.decoderParameter.memCommand) === M_HFENCEG)

    val (exException: Bool, exCause: UInt) = checkExceptions(
      List((exRegExceptionInterrupt || exRegException, exRegCause))
    )
    val exCoverCauses: Seq[(Int, String)] = idCoverCauses
//    coverExceptions(exException, exCause, "EXECUTE", exCoverCauses)

    // memory stage
    val memPcValid: Bool = memRegValid || memRegReplay || memRegExceptionInterrupt
    val memBranchTarget: SInt = memRegPc.asSInt +
      Mux(
        memRegDecodeOutput(parameter.decoderParameter.isBranch) && memBranchTaken,
        ImmGen(ImmGen.IMM_SB, memRegInstruction),
        Mux(memRegDecodeOutput(parameter.decoderParameter.isJal), ImmGen(ImmGen.IMM_UJ, memRegInstruction), Mux(memRegRVC, 2.S, 4.S))
      )
    val memNextPC: UInt = (Mux(
      memRegDecodeOutput(parameter.decoderParameter.isJalr) || memRegSfence,
      encodeVirtualAddress(memRegWdata, memRegWdata).asSInt,
      memBranchTarget
    ) & (-2).S).asUInt
    val memWrongNpc: Bool =
      Mux(
        exPcValid,
        memNextPC =/= exRegPC,
        Mux(
          instructionBufferOut.valid || instructionBuffer.io.imem.valid,
          memNextPC =/= instructionBuffer.io.pc,
          true.B
        )
      )
    val memNpcMisaligned: Bool = !csr.io.status.isa('c' - 'a') && memNextPC(1) && !memRegSfence
    val memIntWdata: UInt = Mux(
      !memRegException && (memRegDecodeOutput(parameter.decoderParameter.isJalr) ^ memNpcMisaligned),
      memBranchTarget,
      memRegWdata.asSInt
    ).asUInt
    val memCfi: Bool =
      memRegDecodeOutput(parameter.decoderParameter.isBranch) || memRegDecodeOutput(parameter.decoderParameter.isJalr) || memRegDecodeOutput(parameter.decoderParameter.isJal)
    val memCfiTaken: Bool =
      (memRegDecodeOutput(parameter.decoderParameter.isBranch) && memBranchTaken) || memRegDecodeOutput(
        parameter.decoderParameter.isJalr
      ) || memRegDecodeOutput(parameter.decoderParameter.isJal)
    val memDirectionMisprediction: Bool =
      memRegDecodeOutput(parameter.decoderParameter.isBranch) && memBranchTaken =/= (usingBTB.B && memRegBTBResponse.taken)
    val memMisprediction: Bool = if (usingBTB) memWrongNpc else memCfiTaken
    takePcMem := memRegValid && !memRegException && (memMisprediction || memRegSfence)

    memRegValid := !ctrlKillx
    memRegReplay := !takePcMemWb && replayEx
    memRegException := !ctrlKillx && exException
    memRegExceptionInterrupt := !takePcMemWb && exRegExceptionInterrupt

    // on pipeline flushes, cause mem_npc to hold the sequential npc, which
    // will drive the W-stage npc mux
    when(memRegValid && memRegFlushPipe) {
      memRegSfence := false.B
    }.elsewhen(exPcValid) {
      memRegDecodeOutput := exRegDecodeOutput
      memRegRVC := exRegRVC

      def isAMOLogical(cmd: UInt) = isOneOf(cmd, Seq(M_XA_SWAP, M_XA_XOR, M_XA_OR, M_XA_AND))
      def isAMOArithmetic(cmd: UInt) = isOneOf(cmd, Seq(M_XA_ADD, M_XA_MIN, M_XA_MAX, M_XA_MINU, M_XA_MAXU))
      def isAMO(cmd: UInt) = isAMOLogical(cmd) || isAMOArithmetic(cmd)
      def isRead(cmd: UInt) = isOneOf(cmd, Seq(M_XRD, M_HLVX, M_XLR, M_XSC)) || isAMO(cmd)
      def isWrite(cmd: UInt) = cmd === M_XWR || cmd === M_PWR || cmd === M_XSC || isAMO(cmd)

      memRegLoad := exRegDecodeOutput(parameter.decoderParameter.mem) && isRead(exRegDecodeOutput(parameter.decoderParameter.memCommand))
      memRegStore := exRegDecodeOutput(parameter.decoderParameter.mem) && isWrite(exRegDecodeOutput(parameter.decoderParameter.memCommand))
      memRegSfence := exSfence
      memRegBTBResponse := exRegBTBResponse
      memRegFlushPipe := exRegFlushPipe
      memRegSlowBypass := exSlowBypass
      memRegWphit := exRegWphit

      memRegCause := exCause
      memRegInstruction := exRegInstruction
      memRegRawInstruction := exRegRawInstruction
      memRegMemSize := exRegMemSize
      memRegHlsOrDv := io.dmem.req.bits.dv
      memRegPc := exRegPC
      // IDecode ensured they are 1H
      memRegWdata := alu.io.out
      memBranchTaken := alu.io.cmp_out

      when(
        exRegDecodeOutput(parameter.decoderParameter.rxs2) && (exRegDecodeOutput(parameter.decoderParameter.mem) || exSfence)
      ) {
        val size = exRegMemSize
        memRegRS2 := new StoreGen(size, 0.U, exRs(1), coreDataBytes).data
      }.elsewhen(exRegDecodeOutput(parameter.decoderParameter.rxs2) && Option.when(usingVector)(exRegDecodeOutput(parameter.decoderParameter.vector)).getOrElse(false.B)) {
        // for setvl
        memRegRS2 := exRs(1)
      }
      when(exRegDecodeOutput(parameter.decoderParameter.isJalr) && csr.io.status.debug) {
        // flush I$ on D-mode JALR to effect uncached fetch without D$ flush
        memRegDecodeOutput(parameter.decoderParameter.fenceI) := true.B
        memRegFlushPipe := true.B
      }
    }

    val memBreakpoint = (memRegLoad && breakpointUnit.io.xcpt_ld) || (memRegStore && breakpointUnit.io.xcpt_st)
    val memDebugBreakpoint = (memRegLoad && breakpointUnit.io.debug_ld) || (memRegStore && breakpointUnit.io.debug_st)
    val (memLoadStoreException, memLoadStoreCause) = checkExceptions(
      List((memDebugBreakpoint, parameter.csrParameter.debugTriggerCause.U), (memBreakpoint, Causes.breakpoint.U))
    )

    val (memException, memCause) = checkExceptions(
      List(
        (memRegExceptionInterrupt || memRegException, memRegCause),
        (memRegValid && memNpcMisaligned, Causes.misaligned_fetch.U),
        (memRegValid && memLoadStoreException, memLoadStoreCause)
      )
    )

//    val memCoverCauses = (exCoverCauses ++ List(
//      (CSR.debugTriggerCause, "DEBUG_TRIGGER"),
//      (Causes.breakpoint, "BREAKPOINT"),
//      (Causes.misaligned_fetch, "MISALIGNED_FETCH")
//    )).distinct
//    coverExceptions(memException, memCause, "MEMORY", memCoverCauses)

    val dcacheKillMem =
      memRegValid && memRegDecodeOutput(parameter.decoderParameter.wxd) && io.dmem.replay_next // structural hazard on writeback port
    // TODO: vectorKillMem?
    val fpuKillMem = io.fpu.map(fpu => memRegValid && memRegDecodeOutput(parameter.decoderParameter.fp) && fpu.nack_mem)
    val replayMem = dcacheKillMem || memRegReplay || fpuKillMem.getOrElse(false.B)
    val killmCommon = dcacheKillMem || takePcWb || memRegException || !memRegValid
    mulDiv.io.kill := killmCommon && RegNext(mulDiv.io.req.fire)
    val ctrlKillm = killmCommon || memException || fpuKillMem.getOrElse(false.B)

    // writeback stage
    wbRegValid := !ctrlKillm
    wbRegReplay := replayMem && !takePcWb
    wbRegException := memException && !takePcWb
    wbRegFlushPipe := !ctrlKillm && memRegFlushPipe
    when(memPcValid) {
      wbRegDecodeOutput := memRegDecodeOutput
      wbRegSfence := memRegSfence
      wbRegWdata := io.fpu
        .map(fpu =>
          Mux(
            !memRegException && memRegDecodeOutput(parameter.decoderParameter.fp) && memRegDecodeOutput(parameter.decoderParameter.wxd),
            fpu.toint_data,
            Mux(
              !memRegException && Option.when(usingVector)(memRegDecodeOutput(parameter.decoderParameter.vectorReadFRs1)).getOrElse(false.B),
              fpu.store_data,
              memIntWdata
            )

          )
        )
        .getOrElse(memIntWdata)
      when(memRegSfence || Option.when(usingVector)(memRegDecodeOutput(parameter.decoderParameter.vector)).getOrElse(false.B)) {
        wbRegRS2 := memRegRS2
      }
      wbRegCause := memCause
      wbRegInstruction := memRegInstruction
      wbRegRawInstruction := memRegRawInstruction
      wbRegMemSize := memRegMemSize
      wbRegHlsOrDv := memRegHlsOrDv
      wbRegHfenceV := memRegDecodeOutput(parameter.decoderParameter.memCommand) === M_HFENCEV
      wbRegHfenceG := memRegDecodeOutput(parameter.decoderParameter.memCommand) === M_HFENCEG
      wbRegPc := memRegPc

      wbRegWphit.lazyZip(memRegWphit).lazyZip(breakpointUnit.io.bpwatch).foreach {case (wbRegWphit, memRegWphit, bpw) =>
        wbRegWphit := memRegWphit || ((bpw.rvalid(0) && memRegLoad) || (bpw.wvalid(0) && memRegStore))
      }
    }

    val (wbException, wbCause) = checkExceptions(
      List(
        (wbRegException, wbRegCause),
        (wbRegValid && wbRegDecodeOutput(parameter.decoderParameter.mem) && io.dmem.s2_xcpt.pf.st, Causes.store_page_fault.U),
        (wbRegValid && wbRegDecodeOutput(parameter.decoderParameter.mem) && io.dmem.s2_xcpt.pf.ld, Causes.load_page_fault.U),
        (wbRegValid && wbRegDecodeOutput(parameter.decoderParameter.mem) && io.dmem.s2_xcpt.gf.st, Causes.store_guest_page_fault.U),
        (wbRegValid && wbRegDecodeOutput(parameter.decoderParameter.mem) && io.dmem.s2_xcpt.gf.ld, Causes.load_guest_page_fault.U),
        (wbRegValid && wbRegDecodeOutput(parameter.decoderParameter.mem) && io.dmem.s2_xcpt.ae.st, Causes.store_access.U),
        (wbRegValid && wbRegDecodeOutput(parameter.decoderParameter.mem) && io.dmem.s2_xcpt.ae.ld, Causes.load_access.U),
        (wbRegValid && wbRegDecodeOutput(parameter.decoderParameter.mem) && io.dmem.s2_xcpt.ma.st, Causes.misaligned_store.U),
        (wbRegValid && wbRegDecodeOutput(parameter.decoderParameter.mem) && io.dmem.s2_xcpt.ma.ld, Causes.misaligned_load.U)
      )
    )

    val wbCoverCauses = Seq(
      (Causes.misaligned_store, "MISALIGNED_STORE"),
      (Causes.misaligned_load, "MISALIGNED_LOAD"),
      (Causes.store_access, "STORE_ACCESS"),
      (Causes.load_access, "LOAD_ACCESS")
    ) ++
      Option
        .when(usingVM)(
          Seq(
            (Causes.store_page_fault, "STORE_PAGE_FAULT"),
            (Causes.load_page_fault, "LOAD_PAGE_FAULT")
          )
        )
        .getOrElse(Seq()) ++
      Option
        .when(usingHypervisor)(
          Seq(
            (Causes.store_guest_page_fault, "STORE_GUEST_PAGE_FAULT"),
            (Causes.load_guest_page_fault, "LOAD_GUEST_PAGE_FAULT")
          )
        )
        .getOrElse(Seq())
//    coverExceptions(wbException, wbCause, "WRITEBACK", wbCoverCauses)

    val wbPcValid: Bool = wbRegValid || wbRegReplay || wbRegException
    val wbWxd:     Bool = wbRegValid && wbRegDecodeOutput(parameter.decoderParameter.wxd)
    val wbSetSboard: Bool =
      wbDcacheMiss ||
        Option.when(usingMulDiv)(wbRegDecodeOutput(parameter.decoderParameter.div)).getOrElse(false.B) ||
        Option
          .when(usingVector) {
            // 8. set Int scoreboard
            wbRegDecodeOutput(parameter.decoderParameter.wxd) && wbRegDecodeOutput(parameter.decoderParameter.vector) && !wbRegDecodeOutput(parameter.decoderParameter.vectorCSR)
          }
          .getOrElse(false.B)
    val replayWbCommon: Bool = io.dmem.s2_nack || wbRegReplay
    val replayWbCsr:    Bool = wbRegValid && csr.io.rwStall
    val replayWb:       Bool = replayWbCommon || replayWbCsr
    takePcWb := replayWb || wbException || csr.io.eret || wbRegFlushPipe

    // writeback arbitration
    val dmemResponseXpu:    Bool = !io.dmem.resp.bits.tag(0).asBool
    val dmemResponseFpu:    Bool = io.dmem.resp.bits.tag(0).asBool
    val dmemResponseWaddr:  UInt = io.dmem.resp.bits.tag(5, 1)
    val dmemResponseValid:  Bool = io.dmem.resp.valid && io.dmem.resp.bits.has_data
    val dmemResponseReplay: Bool = dmemResponseValid && io.dmem.resp.bits.replay

    mulDiv.io.resp.ready := !wbWxd
    val longlatencyWdata:    UInt = WireDefault(mulDiv.io.resp.bits.data)
    val longlatencyWaddress: UInt = WireDefault(mulDiv.io.resp.bits.tag)
    val longLatencyWenable:  Bool = WireDefault(mulDiv.io.resp.fire)

    when(dmemResponseReplay && dmemResponseXpu) {
      mulDiv.io.resp.ready := false.B
      longlatencyWaddress := dmemResponseWaddr
      longLatencyWenable := true.B
    }

    val wbValid = wbRegValid && !replayWb && !wbException
    val wbWen = wbValid && wbRegDecodeOutput(parameter.decoderParameter.wxd)
    // RF is at WB stage
    val rfWen = wbWen || longLatencyWenable
    val rfWaddr = Mux(longLatencyWenable, longlatencyWaddress, wbWaddr)
    val rfWdata = Mux(
      dmemResponseValid && dmemResponseXpu,
      io.dmem.resp.bits.data(xLen - 1, 0),
      Mux(
        longLatencyWenable,
        longlatencyWdata,
        Mux(
          (wbRegDecodeOutput(parameter.decoderParameter.csr) =/= parameter.csrParameter.N) || Option.when(usingVector)(wbRegDecodeOutput(parameter.decoderParameter.vectorCSR)).getOrElse(false.B),
          csr.io.rw.rdata,
          Mux(
            Option.when(usingMulDiv && pipelinedMul)(wbRegDecodeOutput(parameter.decoderParameter.mul)).getOrElse(false.B),
            mul.map(_.io.resp.bits.data).getOrElse(wbRegWdata),
            wbRegWdata
          )
        )
      )
    )
    when(rfWen) { rf.write(rfWaddr, rfWdata) }

    // hook up control/status regfile
    csr.io.ungatedClock := io.clock
    csr.io.decode(0).inst := idInstruction
    csr.io.exception := wbException
    csr.io.cause := wbCause
    csr.io.retire := wbValid
    csr.io.inst(0) := (
      if (usingCompressed)
        Cat(Mux(wbRegRawInstruction(1, 0).andR, wbRegInstruction >> 16, 0.U), wbRegRawInstruction(15, 0))
      else wbRegInstruction
      )
    csr.io.interrupts.tileInterrupts := io.interrupts
    csr.io.interrupts.buserror.foreach( _ := io.buserror )
    csr.io.hartid := io.hartid
    io.fpu.map { fpu =>
      fpu.fcsr_rm := csr.io.fcsrRm
      csr.io.fcsrFlags := fpu.fcsr_flags
      fpu.time := csr.io.time(31, 0)
      fpu.hartid := io.hartid
    }.getOrElse {
      csr.io.fcsrFlags := DontCare
    }
    csr.io.pc := wbRegPc
    val tvalDmemAddr = !wbRegException
    val tvalAnyAddr = tvalDmemAddr ||
      isOneOf(wbRegCause, Seq(
        Causes.breakpoint.U,
        Causes.fetch_access.U,
        Causes.fetch_page_fault.U,
        Causes.fetch_guest_page_fault.U
      ))
    val tvalInstruction = wbRegCause === Causes.illegal_instruction.U
    val tvalValid = wbException && (tvalAnyAddr || tvalInstruction)
    csr.io.gva := wbException && (tvalAnyAddr && csr.io.status.v || tvalDmemAddr && wbRegHlsOrDv)
    csr.io.tval := Mux(tvalValid, encodeVirtualAddress(wbRegWdata, wbRegWdata), 0.U)
    csr.io.htval := {
      val htvalValidImem = wbRegException && wbRegCause === Causes.fetch_guest_page_fault.U
      val htvalImem = Mux(htvalValidImem, io.imem.gpa.bits, 0.U)
      assert(!htvalValidImem || io.imem.gpa.valid)

      val htvalValidDmem =
        wbException && tvalDmemAddr && io.dmem.s2_xcpt.gf.asUInt.orR && !io.dmem.s2_xcpt.pf.asUInt.orR
      val htvalDmem = Mux(htvalValidDmem, io.dmem.s2_gpa, 0.U)

      (htvalDmem | htvalImem) >> hypervisorExtraAddrBits
    }
    io.ptw.ptbr := csr.io.ptbr
    io.ptw.hgatp := csr.io.hgatp
    io.ptw.vsatp := csr.io.vsatp
//    io.ptw.customCSRs.csrs.zip(csr.io.customCSRs).foreach { case (lhs, rhs) => lhs <> rhs }
    io.ptw.status := csr.io.status
    io.ptw.hstatus := csr.io.hstatus
    io.ptw.gstatus := csr.io.gstatus
    io.ptw.pmp := csr.io.pmp
    csr.io.rw.addr := wbRegInstruction(31, 20)
    csr.io.rw.cmd := parameter.csrParameter.maskCmd(wbRegValid, wbRegDecodeOutput(parameter.decoderParameter.csr))
    csr.io.rw.wdata := wbRegWdata
    csr.io.vectorCsr.foreach(_ := wbRegDecodeOutput(parameter.decoderParameter.vectorCSR))
    csr.io.wbRegRS2.foreach(_ := wbRegRS2)

    io.bpwatch.zip(wbRegWphit).zip(csr.io.bp)
    io.bpwatch.lazyZip(wbRegWphit).lazyZip(csr.io.bp).foreach {
      case (iobpw, wphit, bp) =>
        iobpw.valid := wphit
        iobpw.action := bp.control.action
        // tie off bpwatch valids
        iobpw.rvalid := false.B
        iobpw.wvalid := false.B
        iobpw.ivalid := false.B
    }

    val hazardTargets = Seq(
      (idDecodeOutput(parameter.decoderParameter.rxs1) && idRaddr1 =/= 0.U, idRaddr1),
      (idDecodeOutput(parameter.decoderParameter.rxs2) && idRaddr2 =/= 0.U, idRaddr2),
      (idDecodeOutput(parameter.decoderParameter.wxd) && idWaddr =/= 0.U, idWaddr)
    )
    val fpHazardTargets = io.fpu.map(fpu =>
      Seq(
        (fpu.dec.ren1, idRaddr1),
        (fpu.dec.ren2, idRaddr2),
        (fpu.dec.ren3, idRaddr3),
        (fpu.dec.wen, idWaddr)
      )
    )

    val scoreboard: Scoreboard = new Scoreboard(32, true)
    scoreboard.clear(longLatencyWenable, longlatencyWaddress)
    def idScoreboardClearBypass(r: UInt): Bool = {
      // ll_waddr arrives late when D$ has ECC, so reshuffle the hazard check
      if (!hasDataECC) longLatencyWenable && longlatencyWaddress === r
      else
        mulDiv.io.resp.fire && mulDiv.io.resp.bits.tag === r || dmemResponseReplay && dmemResponseXpu && dmemResponseWaddr === r
    }
    val idScoreboardHazard: Bool =
      checkHazards(hazardTargets, rd => scoreboard.read(rd) && !idScoreboardClearBypass(rd))
    scoreboard.set(wbSetSboard && wbWen, wbWaddr)

    // stall for RAW/WAW hazards on CSRs, loads, AMOs, and mul/div in execute stage.
    val exCannotBypass: Bool =
      exRegDecodeOutput(parameter.decoderParameter.csr) =/= parameter.csrParameter.N ||
        exRegDecodeOutput(parameter.decoderParameter.isJalr) ||
        exRegDecodeOutput(parameter.decoderParameter.mem) ||
        Option.when(usingMulDiv && pipelinedMul)(exRegDecodeOutput(parameter.decoderParameter.mul)).getOrElse(false.B) ||
        Option.when(usingMulDiv)(exRegDecodeOutput(parameter.decoderParameter.div)).getOrElse(false.B) ||
        Option.when(usingFPU)(exRegDecodeOutput(parameter.decoderParameter.fp)).getOrElse(false.B) ||
        Option.when(usingVector)(exRegDecodeOutput(parameter.decoderParameter.vector)).getOrElse(false.B)
    val dataHazardEx: Bool = exRegDecodeOutput(parameter.decoderParameter.wxd) && checkHazards(hazardTargets, _ === exWaddr)
    val fpDataHazardEx: Option[Bool] = fpHazardTargets.map(fpHazardTargets =>
      idDecodeOutput(parameter.decoderParameter.fp) && exRegDecodeOutput(parameter.decoderParameter.wfd) && checkHazards(fpHazardTargets, _ === exWaddr)
    )
    val idExHazard: Bool = exRegValid && (dataHazardEx && exCannotBypass || fpDataHazardEx.getOrElse(false.B))

    // stall for RAW/WAW hazards on CSRs, LB/LH, and mul/div in memory stage.
    // TODO: what's BH?
    val memMemCmdBh: Bool =
      if (fastLoadWord) (!fastLoadByte).B && memRegSlowBypass
      else true.B
    val memCannotBypass: Bool =
      memRegDecodeOutput(parameter.decoderParameter.csr) =/= parameter.csrParameter.N ||
        memRegDecodeOutput(parameter.decoderParameter.mem) && memMemCmdBh ||
        Option.when(usingMulDiv && pipelinedMul)(memRegDecodeOutput(parameter.decoderParameter.mul)).getOrElse(false.B) ||
        Option.when(usingMulDiv)(memRegDecodeOutput(parameter.decoderParameter.div)).getOrElse(false.B) ||
        Option.when(usingFPU)(memRegDecodeOutput(parameter.decoderParameter.fp)).getOrElse(false.B) ||
        Option.when(usingVector)(memRegDecodeOutput(parameter.decoderParameter.vector)).getOrElse(false.B)
    val dataHazardMem: Bool = memRegDecodeOutput(parameter.decoderParameter.wxd) && checkHazards(hazardTargets, _ === memWaddr)
    val fpDataHazardMem: Option[Bool] = fpHazardTargets.map(fpHazardTargets =>
      idDecodeOutput(parameter.decoderParameter.fp) &&
        memRegDecodeOutput(parameter.decoderParameter.wfd) &&
        checkHazards(fpHazardTargets, _ === memWaddr)
    )
    val idMemHazard: Bool = memRegValid && (dataHazardMem && memCannotBypass || fpDataHazardMem.getOrElse(false.B))
    idLoadUse := memRegValid && dataHazardMem && memRegDecodeOutput(parameter.decoderParameter.mem)
    // stall for RAW/WAW hazards on load/AMO misses and mul/div in writeback.
    val dataHazardWb: Bool = wbRegDecodeOutput(parameter.decoderParameter.wxd) && checkHazards(hazardTargets, _ === wbWaddr)
    val fpDataHazardWb: Bool = fpHazardTargets
      .map(fpHazardTargets =>
        idDecodeOutput(parameter.decoderParameter.fp) &&
          wbRegDecodeOutput(parameter.decoderParameter.wfd) &&
          checkHazards(fpHazardTargets, _ === wbWaddr)
      )
      .getOrElse(false.B)
    val idWbHazard: Bool = wbRegValid && (dataHazardWb && wbSetSboard || fpDataHazardWb)
    val idStallFpu: Bool =
      io.fpu
        .zip(fpHazardTargets)
        .map {
          case (fpu, fpHazardTargets) =>
            val fpScoreboard = new Scoreboard(32)
            // 8. set FP scoreboard
            fpScoreboard.set(((wbDcacheMiss || Option.when(usingVector)(wbRegDecodeOutput(parameter.decoderParameter.vector)).getOrElse(false.B)) && wbRegDecodeOutput(parameter.decoderParameter.wfd) || fpu.sboard_set) && wbValid, wbWaddr)
            fpScoreboard.clear(dmemResponseReplay && dmemResponseFpu, dmemResponseWaddr)
            t1RetireQueue.foreach(q => fpScoreboard.clear(q.io.deq.fire && q.io.deq.bits.isFp, q.io.deq.bits.rdAddress))
            fpScoreboard.clear(fpu.sboard_clr, fpu.sboard_clra)

            checkHazards(fpHazardTargets, fpScoreboard.read)
        }
        .getOrElse(false.B)

    val dcacheBlocked: Bool = {
      // speculate that a blocked D$ will unblock the cycle after a Grant
      val blocked = Reg(Bool())
      blocked := !io.dmem.req.ready && io.dmem.clock_enabled && !io.dmem.perf.grant && (blocked || io.dmem.req.valid || io.dmem.s2_nack)
      blocked && !io.dmem.perf.grant
    }

    // vector stall
    val vectorLSUEmpty:  Option[Bool] = Option.when(usingVector)(Wire(Bool()))
    val vectorQueueFull: Option[Bool] = Option.when(usingVector)(Wire(Bool()))
    val vectorStall: Option[Bool] = Option.when(usingVector) {
      val vectorLSUNotClear =
        (exRegValid && exRegDecodeOutput(parameter.decoderParameter.vectorLSU)) ||
          (memRegValid && memRegDecodeOutput(parameter.decoderParameter.vectorLSU)) ||
          (wbRegValid && wbRegDecodeOutput(parameter.decoderParameter.vectorLSU)) ||
          !vectorLSUEmpty.get
      // Vector instruction queue is full
      // TODO: need cover.
      (idDecodeOutput(parameter.decoderParameter.vector) && vectorQueueFull.get) ||
        // There is an outstanding LSU.
        (idDecodeOutput(parameter.decoderParameter.mem) && !idDecodeOutput(parameter.decoderParameter.vector) && vectorLSUNotClear)
    }

    // TODO: vector stall
    val ctrlStalld: Bool =
      idExHazard || idMemHazard || idWbHazard || idScoreboardHazard || idDoFence || idRegPause ||
        csr.io.csrStall || csr.io.singleStep && (exRegValid || memRegValid || wbRegValid) ||
        idCsrEn && csr.io.decode(0).fpCsr && !io.fpu.map(_.fcsr_rdy).getOrElse(false.B) || io.traceStall ||
        !clockEnable ||
        Option.when(usingFPU)((idDecodeOutput(parameter.decoderParameter.fp) || idDecodeOutput(parameter.decoderParameter.vectorReadFRs1)) && idStallFpu).getOrElse(false.B) ||
        idDecodeOutput(parameter.decoderParameter.mem) && dcacheBlocked || // reduce activity during D$ misses
        Option
          .when(usingMulDiv)(
            idDecodeOutput(
              parameter.decoderParameter.div
            ) && (!(mulDiv.io.req.ready || (mulDiv.io.resp.valid && !wbWxd)) || mulDiv.io.req.valid)
          )
          .getOrElse(false.B) || // reduce odds of replay
        // TODO: vectorStall is large, we may need it to gate the scalar core.
        vectorStall.getOrElse(false.B)

    ctrlKilled :=
      // IBUF not bubble
      !instructionBuffer.io.inst(0).valid ||
        // Miss
        instructionBufferOut.bits.replay ||
        // flush
        takePcMemWb ||
        //
        ctrlStalld ||
        csr.io.interrupt

    io.imem.req.valid := takePc
    io.imem.req.bits.speculative := !takePcWb
    // flush or branch misprediction
    io.imem.req.bits.pc := Mux(
      wbException || csr.io.eret,
      csr.io.evec, // exception or [m|s]ret
      Mux(
        replayWb,
        wbRegPc, // replay
        memNextPC
      )
    )
    io.imem.flush_icache := wbRegValid && wbRegDecodeOutput(parameter.decoderParameter.fenceI) && !io.dmem.s2_nack
    io.imem.might_request := {
      imemMightRequestReg := exPcValid || memPcValid /*|| io.ptw.customCSRs.disableICacheClockGate*/
      imemMightRequestReg
    }
    io.imem.progress := RegNext(wbRegValid && !replayWbCommon)
    io.imem.sfence.valid := wbRegValid && wbRegSfence
    io.imem.sfence.bits.rs1 := wbRegMemSize(0)
    io.imem.sfence.bits.rs2 := wbRegMemSize(1)
    io.imem.sfence.bits.addr := wbRegWdata
    io.imem.sfence.bits.asid := wbRegRS2
    io.imem.sfence.bits.hv := wbRegHfenceV
    io.imem.sfence.bits.hg := wbRegHfenceG
    io.ptw.sfence := io.imem.sfence

    instructionBufferOut.ready := !ctrlStalld

    io.imem.btb_update.valid := memRegValid && !takePcWb && memWrongNpc && (!memCfi || memCfiTaken)
    io.imem.btb_update.bits.isValid := memCfi
    io.imem.btb_update.bits.cfiType :=
      Mux(
        (memRegDecodeOutput(parameter.decoderParameter.isJal) || memRegDecodeOutput(parameter.decoderParameter.isJalr)) && memWaddr(0),
        CFIType.call,
        Mux(
          memRegDecodeOutput(parameter.decoderParameter.isJalr) && (memRegInstruction(19, 15) & regAddrMask.U) === BitPat("b00?01"),
          CFIType.ret,
          Mux(memRegDecodeOutput(parameter.decoderParameter.isJal) || memRegDecodeOutput(parameter.decoderParameter.isJalr), CFIType.jump, CFIType.branch)
        )
      )
    io.imem.btb_update.bits.target := io.imem.req.bits.pc
    io.imem.btb_update.bits.br_pc := (if (usingCompressed) memRegPc + Mux(memRegRVC, 0.U, 2.U) else memRegPc)
    io.imem.btb_update.bits.pc := ~(~io.imem.btb_update.bits.br_pc | (coreInstBytes * fetchWidth - 1).U)
    io.imem.btb_update.bits.prediction := memRegBTBResponse
    io.imem.btb_update.bits.taken := DontCare

    io.imem.bht_update.valid := memRegValid && !takePcWb
    io.imem.bht_update.bits.pc := io.imem.btb_update.bits.pc
    io.imem.bht_update.bits.taken := memBranchTaken
    io.imem.bht_update.bits.mispredict := memWrongNpc
    io.imem.bht_update.bits.branch := memRegDecodeOutput(parameter.decoderParameter.isBranch)
    io.imem.bht_update.bits.prediction := memRegBTBResponse.bht

    // Connect RAS in Frontend
    io.imem.ras_update := DontCare

    io.fpu.foreach { fpu =>
      fpuDecoder.get.io.instruction := idInstruction
      fpu.dec := fpuDecoder.get.io.output
      fpu.valid := !ctrlKilled && (
        idDecodeOutput(parameter.decoderParameter.fp) ||
          // vector read frs1
          (fpu.dec.ren1 && idDecodeOutput(parameter.decoderParameter.vector))
      )
      fpu.killx := ctrlKillx
      fpu.killm := killmCommon
      fpu.inst := idInstruction
      fpu.fromint_data := exRs(0)
      fpu.dmem_resp_val := dmemResponseValid && dmemResponseFpu
      fpu.dmem_resp_data := (if (minFLen == 32) io.dmem.resp.bits.data_word_bypass else io.dmem.resp.bits.data)
      fpu.dmem_resp_type := io.dmem.resp.bits.size
      fpu.dmem_resp_tag := dmemResponseWaddr
//      fpu.keep_clock_enabled := io.ptw.customCSRs.disableCoreClockGate
      fpu.keep_clock_enabled := false.B
    }

    // TODO: T1 only logic
    io.t1.foreach { t1 =>
      // T1 Issue
      val maxCount: Int = 32
      val t1IssueQueue = Module(new Queue(chiselTypeOf(t1.issue.bits), maxCount))
      t1IssueQueue.io.enq.valid :=
        wbRegValid && !replayWbCommon && wbRegDecodeOutput(parameter.decoderParameter.vector) &&
          !wbRegDecodeOutput(parameter.decoderParameter.vectorCSR)
      t1IssueQueue.io.enq.bits.instruction := wbRegInstruction
      t1IssueQueue.io.enq.bits.rs1Data := wbRegWdata
      t1IssueQueue.io.enq.bits.rs2Data := wbRegRS2
      t1IssueQueue.io.enq.bits.vtype := csr.io.csrToVector.get.vtype
      t1IssueQueue.io.enq.bits.vl := csr.io.csrToVector.get.vl
      t1IssueQueue.io.enq.bits.vstart := csr.io.csrToVector.get.vstart
      t1IssueQueue.io.enq.bits.vcsr := csr.io.csrToVector.get.vcsr
      t1.issue.valid := t1IssueQueue.io.deq.valid
      t1.issue.bits := t1IssueQueue.io.deq.bits
      t1IssueQueue.io.deq.ready := t1.issue.ready
      // For each different retirements, it should maintain different scoreboard
      val t1CSRRetireQueue: Queue[T1CSRRetire] = Module(new Queue(chiselTypeOf(t1.retire.csr.bits), maxCount))
      val t1XRDRetireQueue: Queue[T1RdRetire] = t1RetireQueue.get

      val countWidth = log2Up(maxCount)
      def counterManagement(size: Int, margin: Int = 0)(grant: Bool, release: Bool, flush: Option[Bool] = None) = {
        val counter: UInt = RegInit(0.U(size.W))
        val nextCount = counter + Mux(grant, 1.U(size.W), (-1.S(size.W)).asUInt)
        val updateCounter = grant ^ release
        when(updateCounter) {
          counter := nextCount
        }
        flush.foreach(f => when(f)(counter := 0.U))
        val empty = (updateCounter && nextCount === 0.U) || counter === 0.U
        val fullCounter: Int = (1 << size) - 1 - margin
        val full = (updateCounter && nextCount >= fullCounter.U) || counter >= fullCounter.U
        (empty, full)
      }
      // T1 Memory Scoreboard
      val t1MemoryGrant:   Bool = t1IssueQueue.io.enq.valid && wbRegDecodeOutput(parameter.decoderParameter.vectorLSU)
      val t1MemoryRelease: Bool = t1.retire.mem.fire
      // todo: handle vector lsu in pipe
      // +1: There are instructions that will enter t1
      val (lsuEmpty, _) = counterManagement(countWidth + 1)(t1MemoryGrant, t1MemoryRelease)
      // T1 CSR Scoreboard
      // todo: add wbRegDecodeOutput(vectorWriteCsr)
      val t1CSRGrant:   Bool = false.B
      val t1CSRRelease: Bool = false.B // t1CSRRetireQueue.io.deq.fire
      val (t1CSREmpty, _) = counterManagement(countWidth + 1)(t1CSRGrant, t1CSRRelease)
      // T1 XRD Scoreboard?

      // Maintain vector counter
      // There may be 4 instructions in the pipe
      val (vectorEmpty, vectorFull) = counterManagement(countWidth, 4)(t1IssueQueue.io.enq.valid, t1.issue.fire)
      vectorLSUEmpty.foreach(_ := lsuEmpty)
      vectorQueueFull.foreach(_ := vectorFull)

      t1XRDRetireQueue.io.enq.valid := t1.retire.rd.valid
      t1XRDRetireQueue.io.enq.bits := t1.retire.rd.bits
      t1CSRRetireQueue.io.enq.valid := t1.retire.csr.valid
      t1CSRRetireQueue.io.enq.bits := t1.retire.csr.bits
      // todo: write csr here
      t1CSRRetireQueue.io.deq.ready := true.B

      val vectorTryToWriteRd = t1XRDRetireQueue.io.deq.valid && !t1XRDRetireQueue.io.deq.bits.isFp
      val vectorTryToWriteFP = t1XRDRetireQueue.io.deq.valid && t1XRDRetireQueue.io.deq.bits.isFp
      t1XRDRetireQueue.io.deq.ready := (!(wbWxd || (dmemResponseReplay && dmemResponseXpu)) || !vectorTryToWriteRd) && (!(dmemResponseReplay && dmemResponseFpu) || !vectorTryToWriteFP)

      when(t1XRDRetireQueue.io.deq.fire && vectorTryToWriteRd) {
        longlatencyWdata := t1.retire.rd.bits.rdData
        longlatencyWaddress := t1.retire.rd.bits.rdAddress
        longLatencyWenable := true.B
      }
      io.fpu.foreach { fpu =>
        when(!(dmemResponseValid && dmemResponseFpu)) {
          fpu.dmem_resp_val := t1XRDRetireQueue.io.deq.valid && vectorTryToWriteFP
          fpu.dmem_resp_data := t1XRDRetireQueue.io.deq.bits.rdData
          // todo: 32 bit only
          fpu.dmem_resp_type := 2.U
          fpu.dmem_resp_tag := t1XRDRetireQueue.io.deq.bits.rdAddress
        }
      }

      // probe defination
      layer.block(layers.Verification) {
        val probeWire = Wire(new RocketProbe(parameter))
        define(io.rocketProbe, ProbeValue(probeWire))

        probeWire.rfWen := rfWen
        probeWire.rfWaddr := rfWaddr
        probeWire.rfWdata := rfWdata

        probeWire.waitWen := wbSetSboard && wbWen
        probeWire.waitWaddr := wbWaddr
        // vector commit || vector write rd
        probeWire.isVector := io.t1.map { t1 =>
          wbRegValid && wbRegDecodeOutput(parameter.decoderParameter.vector) &&
            !wbRegDecodeOutput(parameter.decoderParameter.vectorCSR)
        }.getOrElse(false.B) || t1RetireQueue.map(q => q.io.deq.fire).getOrElse(false.B)
        probeWire.idle := vectorEmpty

        probeWire.fpuScoreboard.foreach { case fpProbe =>
          fpProbe.memSetScoreBoard := wbValid && wbDcacheMiss && wbRegDecodeOutput(parameter.decoderParameter.wfd)
          fpProbe.vectorSetScoreBoard := wbValid && wbRegDecodeOutput(parameter.decoderParameter.wfd) && Option.when(usingVector)(wbRegDecodeOutput(parameter.decoderParameter.vector)).getOrElse(false.B)
          fpProbe.scoreBoardSetAddress := wbWaddr

          io.fpu.map { fpu =>
            fpProbe.fpuSetScoreBoard := wbValid && wbRegDecodeOutput(parameter.decoderParameter.wfd) && fpu.sboard_set
            fpProbe.fpuClearScoreBoard.valid := fpu.sboard_clr
            fpProbe.fpuClearScoreBoard.bits := fpu.sboard_clra
          }

          fpProbe.vectorClearScoreBoard.valid := t1RetireQueue.map(q => q.io.deq.fire && q.io.deq.bits.isFp).getOrElse(false.B)
          fpProbe.vectorClearScoreBoard.bits := t1RetireQueue.map(q => q.io.deq.bits.rdAddress).getOrElse(0.U)

          fpProbe.memClearScoreBoard.valid := dmemResponseReplay && dmemResponseFpu
          fpProbe.memClearScoreBoard.bits := dmemResponseWaddr
        }
      }
    }

    io.dmem.req.valid := exRegValid && exRegDecodeOutput(parameter.decoderParameter.mem)
    val ex_dcache_tag = Cat(exWaddr, Option.when(usingFPU)(exRegDecodeOutput(parameter.decoderParameter.fp)).getOrElse(false.B))
//    require(coreParams.dcacheReqTagBits >= ex_dcache_tag.getWidth)
    io.dmem.req.bits.tag := ex_dcache_tag
    io.dmem.req.bits.cmd := exRegDecodeOutput(parameter.decoderParameter.memCommand)
    io.dmem.req.bits.size := exRegMemSize
    io.dmem.req.bits.signed := !Mux(exRegHLS, exRegInstruction(20), exRegInstruction(14))
    io.dmem.req.bits.phys := false.B
    io.dmem.req.bits.addr := encodeVirtualAddress(exRs(0), alu.io.adder_out)
    io.dmem.req.bits.idx.foreach(_ := io.dmem.req.bits.addr)
    io.dmem.req.bits.dprv := Mux(exRegHLS, csr.io.hstatus.spvp, csr.io.status.dprv)
    io.dmem.req.bits.dv := exRegHLS || csr.io.status.dv
    io.dmem.req.bits.no_alloc := DontCare
    io.dmem.req.bits.no_xcpt := DontCare
    io.dmem.req.bits.data := DontCare
    io.dmem.req.bits.mask := DontCare
    io.dmem.s1_data.data := io.fpu
      .map(fpu => Mux(memRegDecodeOutput(parameter.decoderParameter.fp), Fill(xLen.max(fLen.get) / fLen.get, fpu.store_data), memRegRS2))
      .getOrElse(memRegRS2)
    io.dmem.s1_data.mask := DontCare

    io.dmem.s1_kill := killmCommon || memLoadStoreException || fpuKillMem.getOrElse(false.B)
    io.dmem.s2_kill := false.B
    // don't let D$ go to sleep if we're probably going to use it soon
    io.dmem.keep_clock_enabled := instructionBufferOut.valid && idDecodeOutput(parameter.decoderParameter.mem) && !csr.io.csrStall

    // gate the clock
    val unpause: Bool =
      csr.io.time(rocketParams.lgPauseCycles - 1, 0) === 0.U || csr.io.inhibitCycle || io.dmem.perf.release || takePc
    when(unpause) { idRegPause := false.B }
    io.cease := csr.io.status.cease && !clockEnableReg
    io.wfi := csr.io.status.wfi
    if (rocketParams.clockGate) {
      longLatencyStall := csr.io.csrStall || io.dmem.perf.blocked || idRegPause && !unpause
      clockEnable := clockEnableReg || exPcValid || (!longLatencyStall && io.imem.resp.valid)
      clockEnableReg :=
        exPcValid || memPcValid || wbPcValid || // instruction in flight
//          io.ptw.customCSRs.disableCoreClockGate || // chicken bit
          !mulDiv.io.req.ready || // mul/div in flight
          io.fpu.map(!_.fcsr_rdy).getOrElse(false.B) || // long-latency FPU in flight
          io.dmem.replay_next || // long-latency load replaying
          (!longLatencyStall && (instructionBufferOut.valid || io.imem.resp.valid)) // instruction pending

      assert(!(exPcValid || memPcValid || wbPcValid) || clockEnable)
    }

    // evaluate performance counters
    val icacheBlocked = !(io.imem.resp.valid || RegNext(io.imem.resp.valid))
    // todo: perfEvents here.
//    csr.io.counters.foreach { c => c.inc := RegNext(perfEvents.evaluate(c.eventSel)) }

  }

  def checkExceptions(x: Seq[(Bool, UInt)]) =
    (x.map(_._1).reduce(_ || _), PriorityMux(x))

  def checkHazards(targets: Seq[(Bool, UInt)], cond: UInt => Bool) =
    targets.map(h => h._1 && cond(h._2)).reduce(_ || _)

  def encodeVirtualAddress(a0: UInt, ea: UInt) = if (vaddrBitsExtended == vaddrBits) ea
  else {
    // efficient means to compress 64-bit VA into vaddrBits+1 bits
    // (VA is bad if VA(vaddrBits) != VA(vaddrBits-1))
    val b = vaddrBitsExtended - 1
    val a = (a0 >> b).asSInt
    val msb = Mux(a === 0.S || a === -1.S, ea(b), !ea(b - 1))
    Cat(msb, ea(b - 1, 0))
  }

  class Scoreboard(n: Int, zero: Boolean = false) {
    def set(en:            Bool, addr: UInt): Unit = update(en, _next | mask(en, addr))
    def clear(en:          Bool, addr: UInt): Unit = update(en, _next & ~mask(en, addr))
    def read(addr:         UInt): Bool = r(addr)
    def readBypassed(addr: UInt):      Bool = _next(addr)

    private val _r = RegInit(0.U(n.W))
    private val r = if (zero) (_r >> 1 << 1) else _r
    private var _next = r
    private var ens = false.B
    private def mask(en: Bool, addr: UInt) = Mux(en, 1.U << addr, 0.U)
    private def update(en: Bool, update: UInt) = {
      _next = update
      ens = ens || en
      when(ens) { _r := _next }
    }
  }
}

class RegFile(n: Int, w: Int, zero: Boolean = false) {
  val rf: Mem[UInt] = Mem(n, UInt(w.W))
  private def access(addr: UInt): UInt = rf(~addr(log2Ceil(n) - 1, 0))
  private val reads = collection.mutable.ArrayBuffer[(UInt, UInt)]()
  private var canRead = true
  def read(addr: UInt) = {
    require(canRead)
    reads += addr -> Wire(UInt())
    reads.last._2 := Mux(zero.B && addr === 0.U, 0.U, access(addr))
    reads.last._2
  }
  def write(addr: UInt, data: UInt) = {
    canRead = false
    when(addr =/= 0.U) {
      access(addr) := data
      for ((raddr, rdata) <- reads)
        when(addr === raddr) { rdata := data }
    }
  }
}
