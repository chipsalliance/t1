// See chipsalliance:rocket-chip LICENSE.Berkeley for license details.
// See chipsalliance:rocket-chip LICENSE.SiFive for license details.

package org.chipsalliance.t1.rocketcore

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle
import freechips.rocketchip.util._
import org.chipsalliance.cde.config.{Field, Parameters}
import org.chipsalliance.t1.rockettile.{VectorRequest, VectorResponse}

import scala.collection.mutable.ArrayBuffer

// TODO: remove it.
import freechips.rocketchip.rocket.{Causes, MulDivParams, RocketCoreParams}
import freechips.rocketchip.tile.{CoreInterrupts, FPUCoreIO, HasCoreParameters}

case object RISCVOpcodesPath extends Field[os.Path]

trait HasRocketCoreParameters extends HasCoreParameters {
  lazy val rocketParams: RocketCoreParams = tileParams.core.asInstanceOf[RocketCoreParams]

  val fastLoadWord = rocketParams.fastLoadWord
  val fastLoadByte = rocketParams.fastLoadByte

  val mulDivParams = rocketParams.mulDiv.getOrElse(MulDivParams()) // TODO ask andrew about this

  val aluFn = new ALUFN

  require(!fastLoadByte || fastLoadWord)
  require(!rocketParams.haveFSDirty, "rocket doesn't support setting fs dirty from outside, please disable haveFSDirty")
  require(!usingConditionalZero, "Zicond is not yet implemented in ABLU")
}

class Rocket(tile: RocketTile)(implicit val p: Parameters) extends Module with HasRocketCoreParameters {
  // Checker
  require(decodeWidth == 1 /* TODO */ && retireWidth == decodeWidth)
  require(!(coreParams.useRVE && coreParams.fpu.nonEmpty), "Can't select both RVE and floating-point")
  require(!(coreParams.useRVE && coreParams.useHypervisor), "Can't select both RVE and Hypervisor")

  // Parameters
  val pipelinedMul: Boolean = usingMulDiv && mulDivParams.mulUnroll == xLen
  val decoder: InstructionDecoder = new org.chipsalliance.t1.rocketcore.InstructionDecoder(
    org.chipsalliance.t1.rocketcore.InstructionDecoderParameter(
      // TODO: configurable
      (org.chipsalliance.rvdecoderdb.fromFile.instructions(p(RISCVOpcodesPath)) ++
        org.chipsalliance.t1.rocketcore.CustomInstructions.rocketSet).filter { i =>
        i.instructionSets.map(_.name) match {
          // I
          case s if s.contains("rv_i")   => true
          case s if s.contains("rv32_i") => xLen == 32
          case s if s.contains("rv64_i") => xLen == 64
          // M
          case s if s.contains("rv_m")   => usingMulDiv
          case s if s.contains("rv64_m") => (xLen == 64) && usingMulDiv
          // A
          case s if s.contains("rv_a")   => usingAtomics
          case s if s.contains("rv64_a") => (xLen == 64) && usingAtomics
          // ZICSR
          case s if s.contains("rv_zicsr") => true
          // ZIFENCEI
          case s if s.contains("rv_zifencei") => true
          // F
          case s if s.contains("rv_f")   => !(fLen == 0)
          case s if s.contains("rv64_f") => (xLen == 64) && !(fLen == 0)
          // D
          case s if s.contains("rv_d")   => fLen == 64
          case s if s.contains("rv64_d") => (xLen == 64) && (fLen == 64)
          // ZFH
          case s if s.contains("rv_zfh")   => minFLen == 16
          case s if s.contains("rv64_zfh") => (xLen == 64) && (minFLen == 16)
          case s if s.contains("rv_d_zfh") => (fLen == 64) && (minFLen == 16)

          // Priv
          case s if s.contains("rv_system") => true
          // Supervisor
          case s if s.contains("rv_s") =>
            i.name match {
              // if support superviosr but don't support virtual memory, raise illinstr.
              case s if s.contains("sfence.vma") => usingVM
              case s if s.contains("sret")       => usingSupervisor
            }
          case s if s.contains("rv_smrnmi") => usingNMI
          // Hypervisor
          case s if s.contains("rv_h")   => usingHypervisor
          case s if s.contains("rv64_h") => (xLen == 64) && usingHypervisor
          // Debug
          case s if s.contains("rv_sdext") => usingDebug

          // T1 Vector
          case s if s.contains("rv_v") => usingVector
          // unratified but supported.
          case s if s.contains("rv_zicond") => usingConditionalZero
          // custom
          case s if s.contains("rv_rocket") =>
            i.name match {
              case "c.flush.d.l1"   => coreParams.haveCFlush
              case "c.discard.d.l1" => coreParams.haveCFlush
              case "cease"          => rocketParams.haveCease
            }
          case _ => false
        }
      }.filter {
        // special case for rv32 pseudo from rv64
        case i if i.pseudoFrom.isDefined && Seq("slli", "srli", "srai").contains(i.name) => true
        case i if i.pseudoFrom.isDefined                                                 => false
        case _                                                                           => true
      }.toSeq.distinct,
      pipelinedMul,
      tile.dcache.flushOnFenceI
    )
  )
  val lgNXRegs:    Int = if (coreParams.useRVE) 4 else 5
  val regAddrMask: Int = (1 << lgNXRegs) - 1

  val hartid = IO(Input(UInt(hartIdLen.W)))
  val interrupts = IO(Input(new CoreInterrupts()))
  val imem = IO(new FrontendIO)
  val dmem = IO(new HellaCacheIO)
  val ptw = IO(Flipped(new DatapathPTWIO()))
  val fpu = Option.when(usingFPU)(IO(Flipped(new FPUCoreIO())))
  val bpwatch = IO(Output(Vec(coreParams.nBreakpoints, new BPWatch(coreParams.retireWidth))))
  val cease = IO(Output(Bool()))
  val wfi = IO(Output(Bool()))
  val traceStall = IO(Input(Bool()))
  val t1Request = Option.when(usingVector)(IO(Valid(new VectorRequest(xLen))))
  val t1Response = Option.when(usingVector)(IO(Flipped(Decoupled(new VectorResponse(xLen)))))
  // logic for T1
  val t1IssueQueueRelease = Option.when(usingVector)(IO(Input(Bool())))

  // Signal outside from internal clock domain.

  val longLatencyStall = Reg(Bool())
  val idRegPause = Reg(Bool())
  val imemMightRequestReg = Reg(Bool())
  val clockEnable = WireDefault(true.B)
  val clockEnableReg = RegInit(true.B)
  val gatedClock =
    Option.when(rocketParams.clockGate)(ClockGate(clock, clockEnable, "rocket_clock_gate")).getOrElse(clock)
  // leaving gated-clock domain
  val gatedDomain = withClock(gatedClock)(new Gated)

  class Gated {
    // performance counters
    def pipelineIDToWB[T <: Data](x: T): T = RegEnable(RegEnable(RegEnable(x, !ctrlKilled), exPcValid), memPcValid)
    // TODO: remove it and probe signal to verification modules
    // format: off
    val perfEvents: EventSets = new EventSets(
      Seq(
        new EventSet(
          (mask, hits) => Mux(wbException, mask(0), wbValid && pipelineIDToWB((mask & hits).orR)),
          Seq(
            ("exception", () => false.B),
            // TODO: why no FPU here?
            ("load", () => idDecodeOutput(decoder.mem) && idDecodeOutput(decoder.memCommand) === M_XRD && !Option.when(usingFPU)(idDecodeOutput(decoder.fp)).getOrElse(false.B)),
            ("store", () => idDecodeOutput(decoder.mem) && idDecodeOutput(decoder.memCommand) === M_XWR && !Option.when(usingFPU)(idDecodeOutput(decoder.fp)).getOrElse(false.B)),
            ("system", () => idDecodeOutput(decoder.csr) =/= CSR.N),
            ("arith", () => idDecodeOutput(decoder.wxd) && !( idDecodeOutput(decoder.isJal) || idDecodeOutput(decoder.isJalr) || idDecodeOutput(decoder.mem) || Option.when(usingFPU)(idDecodeOutput(decoder.fp)).getOrElse(false.B) || Option.when(usingMulDiv && pipelinedMul)(idDecodeOutput(decoder.mul)).getOrElse(false.B) || Option.when(usingMulDiv)(idDecodeOutput(decoder.div)).getOrElse(false.B) || idDecodeOutput(decoder.csr) =/= CSR.N )),
            ("branch", () => idDecodeOutput(decoder.isBranch)),
            ("jal", () => idDecodeOutput(decoder.isJal)),
            ("jalr", () => idDecodeOutput(decoder.isJalr))
          ) ++
            Option.when(usingAtomics)(Seq(
              ("amo", () => idDecodeOutput(decoder.mem) && (isAMO(idDecodeOutput(decoder.memCommand)) || idDecodeOutput(decoder.memCommand).isOneOf(M_XLR, M_XSC)))
            )).getOrElse(Seq()) ++
            Option.when(usingMulDiv)(Seq(
              ("mul", () => if (pipelinedMul) idDecodeOutput(decoder.mul) else idDecodeOutput(decoder.div) && (idDecodeOutput(decoder.aluFn) & aluFn.FN_DIV) =/= aluFn.FN_DIV),
              ("div", () => if (pipelinedMul) idDecodeOutput(decoder.div) else idDecodeOutput(decoder.div) && (idDecodeOutput(decoder.aluFn) & aluFn.FN_DIV) === aluFn.FN_DIV)
            )).getOrElse(Seq()) ++
            fpu.map(fpu => Seq(
              ("fp load", () => idDecodeOutput(decoder.fp) && fpu.dec.ldst && fpu.dec.wen),
              ("fp store", () => idDecodeOutput(decoder.fp) && fpu.dec.ldst && !fpu.dec.wen),
              ("fp add", () => idDecodeOutput(decoder.fp) && fpu.dec.fma && fpu.dec.swap23),
              ("fp mul", () => idDecodeOutput(decoder.fp) && fpu.dec.fma && !fpu.dec.swap23 && !fpu.dec.ren3),
              ("fp mul-add", () => idDecodeOutput(decoder.fp) && fpu.dec.fma && fpu.dec.ren3),
              ("fp div/sqrt", () => idDecodeOutput(decoder.fp) && (fpu.dec.div || fpu.dec.sqrt)),
              ("fp other", () => idDecodeOutput(decoder.fp) && !(fpu.dec.ldst || fpu.dec.fma || fpu.dec.div || fpu.dec.sqrt ))
            )).getOrElse(Seq())
        ),
        new EventSet(
          (mask, hits) => (mask & hits).orR,
          Seq(
            ("load-use interlock", () => idExHazard && exRegDecodeOutput(decoder.mem) || idMemHazard && memRegDecodeOutput(decoder.mem) || idWbHazard && wbRegDecodeOutput(decoder.mem)           ),
            ("long-latency interlock", () => idScoreboardHazard),
            ("csr interlock", () => idExHazard && exRegDecodeOutput(decoder.csr) =/= CSR.N || idMemHazard && memRegDecodeOutput(decoder.csr) =/= CSR.N || idWbHazard && wbRegDecodeOutput(decoder.csr) =/= CSR.N),
            ("I$ blocked", () => icacheBlocked),
            ("D$ blocked", () => idDecodeOutput(decoder.mem) && dcacheBlocked),
            ("branch misprediction", () => takePcMem && memDirectionMisprediction),
            ("control-flow target misprediction", () => takePcMem && memMisprediction && memCfi && !memDirectionMisprediction && !icacheBlocked),
            ("flush", () => wbRegFlushPipe),
            ("replay", () => replayWb)
          ) ++
            Option.when(usingMulDiv)(Seq(
              ("mul/div interlock", () => idExHazard && (Option.when(pipelinedMul)(exRegDecodeOutput(decoder.mul)).getOrElse(false.B) || exRegDecodeOutput(decoder.div)) || idMemHazard && (Option.when(pipelinedMul)(memRegDecodeOutput(decoder.mul)).getOrElse(false.B) || memRegDecodeOutput(decoder.div)) || idWbHazard && wbRegDecodeOutput(decoder.div))
            )).getOrElse(Seq()) ++
            Option.when(usingFPU)(Seq(
              ("fp interlock", () => idExHazard && exRegDecodeOutput(decoder.fp) || idMemHazard && memRegDecodeOutput(decoder.fp) || idWbHazard && wbRegDecodeOutput(decoder.fp) || idDecodeOutput(decoder.fp) && idStallFpu)
            )).getOrElse(Seq())
        ),
        new EventSet(
          (mask, hits) => (mask & hits).orR,
          Seq(
            ("I$ miss", () => imem.perf.acquire),
            ("D$ miss", () => dmem.perf.acquire),
            ("D$ release", () =>dmem.perf.release),
            ("ITLB miss", () => imem.perf.tlbMiss),
            ("DTLB miss", () => dmem.perf.tlbMiss),
            ("L2 TLB miss", () => ptw.perf.l2miss)
          )
        )
      )
    )
    // format: on

    // Start RTL Here
    // instantiate modules
    // TODO: remove implicit parameter for them.

    val csr: CSRFile = Module(new CSRFile(perfEvents, coreParams.customCSRs.decls))

    // TODO: move to Parameter Level or LazyModule level.
    /** Decoder instantiated, input from IF, output to ID. */
    val decoderModule = Module(new RawModule {
      override def desiredName: String = "RocketDecoder"
      val instruction = IO(Input(UInt(32.W)))
      val output = IO(Output(decoder.table.bundle))
      output := decoder.table.decode(instruction)
    })
    val instructionBuffer:   IBuf = Module(new IBuf)
    val breakpointUnit:      BreakpointUnit = Module(new BreakpointUnit(nBreakpoints))
    val arithmeticLogicUnit: ALU = Module(new ALU())
    val muldiv = Module(
      new MulDiv(if (pipelinedMul) mulDivParams.copy(mulUnroll = 0) else mulDivParams, width = xLen, aluFn = aluFn)
    ).suggestName(if (pipelinedMul) "div" else "muldiv")
    val mul = pipelinedMul.option(Module(new PipelinedMultiplier(xLen, 2, aluFn = aluFn)))
    // RF is not a Module.
    val rf = new RegFile(regAddrMask, xLen)

    // wire definations.

    val idDecodeOutput: DecodeBundle = Wire(decoder.table.bundle)

    val exRegExceptionInterrupt: Bool = Reg(Bool())
    val exRegException:          Bool = Reg(Bool())
    val exRegValid:              Bool = Reg(Bool())
    val exRegRVC:                Bool = Reg(Bool())
    val exRegBTBResponse:        BTBResp = Reg(new BTBResp)
    val exRegFlushPipe:          Bool = Reg(Bool())
    val exRegLoadUse:            Bool = Reg(Bool())
    val exRegCause:              UInt = Reg(UInt())
    val exRegReplay:             Bool = Reg(Bool())
    val exRegPC:                 UInt = Reg(UInt())
    // TODO: add real width here.
    val exRegMemSize: UInt = Reg(UInt())
    // Option.when(usingHypervisor)
    val exRegHLS:            Bool = Reg(Bool())
    val exRegInstruction:    UInt = Reg(Bits())
    val exRegRawInstruction: UInt = Reg(UInt())
    // TODO: what's this?
    val exRegWphit:        Vec[Bool] = Reg(Vec(nBreakpoints, Bool()))
    val exRegDecodeOutput: DecodeBundle = Reg(decoder.table.bundle)

    val memRegExceptionInterrupt = Reg(Bool())
    val memRegValid = Reg(Bool())
    val memRegRVC = Reg(Bool())
    val memRegBTBResponse = Reg(new BTBResp)
    val memRegException = Reg(Bool())
    val memRegReplay = Reg(Bool())
    val memRegFlushPipe = Reg(Bool())
    val memRegCause = Reg(UInt())
    val memRegSlowBypass = Reg(Bool())
    val memRegLoad = Reg(Bool())
    val memRegStore = Reg(Bool())
    val memRegSfence = Reg(Bool())
    val memRegPc = Reg(UInt())
    val memRegInstruction = Reg(Bits())
    val memRegMemSize = Reg(UInt())
    val memRegDecodeOutput: DecodeBundle = Reg(decoder.table.bundle)

    /** virtualization mode? */
    val memRegHlsOrDv = Reg(Bool())
    val memRegRawInstruction = Reg(UInt())
    val memRegWdata = Reg(Bits())
    val memRegRS2 = Reg(Bits())
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
    val wbRegDecodeOutput: DecodeBundle = Reg(decoder.table.bundle)
    val wbRegMemSize = Reg(UInt())
    val wbRegHlsOrDv = Reg(Bool())
    val wbRegHfenceV = Reg(Bool())
    val wbRegHfenceG = Reg(Bool())
    val wbRegInstruction = Reg(Bits())
    val wbRegRawInstruction = Reg(UInt())
    val wbRegWdata = Reg(Bits())
    val wbRegRS2 = Reg(Bits())
    val wbRegWphit = Reg(Vec(nBreakpoints, Bool()))
    val takePcWb = Wire(Bool())

    val takePcMemWb = takePcWb || takePcMem
    val takePc = takePcMemWb

    // From IBUF to ID
    instructionBuffer.io.imem <> imem.resp
    val instructionBufferOut: DecoupledIO[Instruction] = instructionBuffer.io.inst.head
    // TODO: does these really has its meaning? I don't think so:(
    val idExpandedInstruction: ExpandedInstruction = instructionBufferOut.bits.inst
    val idRawInstruction:      UInt = instructionBufferOut.bits.raw
    val idInstruction:         UInt = idExpandedInstruction.bits
    idDecodeOutput := decoderModule.output
    instructionBuffer.io.kill := takePc
    decoderModule.instruction := idInstruction

    def decodeReg(x: UInt): (Bool, UInt) = (x.extract(x.getWidth - 1, lgNXRegs).asBool, x(lgNXRegs - 1, 0))
    val (idRaddr3Illegal: Bool, idRaddr3: UInt) = decodeReg(idExpandedInstruction.rs3)
    val (idRaddr2Illegal: Bool, idRaddr2: UInt) = decodeReg(idExpandedInstruction.rs2)
    val (idRaddr1Illegal: Bool, idRaddr1: UInt) = decodeReg(idExpandedInstruction.rs1)
    val (idWaddrIllegal: Bool, idWaddr: UInt) = decodeReg(idExpandedInstruction.rd)

    val idLoadUse:  Bool = Wire(Bool())
    val idRegFence: Bool = RegInit(false.B)
    val idRen:      Seq[Bool] = IndexedSeq(idDecodeOutput(decoder.rxs1), idDecodeOutput(decoder.rxs2))
    val idRaddr:    Seq[UInt] = IndexedSeq(idRaddr1, idRaddr2)
    val idRs:       Seq[UInt] = idRaddr.map(rf.read)
    val ctrlKilled: Bool = Wire(Bool())

    // TODO: additional decode out?
    val idCsrEn:             Bool = idDecodeOutput(decoder.csr).isOneOf(CSR.S, CSR.C, CSR.W)
    val idSystemInstruction: Bool = idDecodeOutput(decoder.csr) === CSR.I
    val idCsrRen:            Bool = idDecodeOutput(decoder.csr).isOneOf(CSR.S, CSR.C) && idExpandedInstruction.rs1 === 0.U
    val idCsr =
      Mux(idSystemInstruction && idDecodeOutput(decoder.mem), CSR.N, Mux(idCsrRen, CSR.R, idDecodeOutput(decoder.csr)))
    val idCsrFlush = idSystemInstruction || (idCsrEn && !idCsrRen && csr.io.decode(0).writeFlush)
    val idRfIllegal: Bool =
      idRaddr2Illegal && idDecodeOutput(decoder.rxs2) ||
        idRaddr1Illegal && idDecodeOutput(decoder.rxs1) ||
        idWaddrIllegal && idDecodeOutput(decoder.wxd)
    val idCsrIllegalRW: Bool =
      idCsrEn && (csr.io.decode(0).readIllegal || !idCsrRen && csr.io.decode(0).writeIllegal)
    val idSystemIllegal: Bool =
      !instructionBufferOut.bits.rvc && (idSystemInstruction && csr.io.decode(0).systemIllegal)

    val idAtomicIllegal: Option[Bool] =
      Option.when(usingAtomics)(idDecodeOutput(decoder.amo) && !csr.io.status.isa('a' - 'a'))
    val idMulDivIllegal: Option[Bool] =
      Option.when(usingMulDiv)(
        Option.when(pipelinedMul)(idDecodeOutput(decoder.mul)).getOrElse(false.B) ||
          idDecodeOutput(decoder.div) && !csr.io.status.isa('m' - 'a')
      )
    val idCompressIllegal: Option[Bool] =
      Option.when(usingCompressed)(instructionBufferOut.bits.rvc && !csr.io.status.isa('c' - 'a'))
    val idFpIllegal: Option[Bool] =
      fpu.map(fpu => idDecodeOutput(decoder.fp) && (csr.io.decode(0).fpIllegal || fpu.illegal_rm))
    val idDpIllegal: Option[Bool] = Option.when(usingFPU)(idDecodeOutput(decoder.dp) && !csr.io.status.isa('d' - 'a'))

    val idIllegalInstruction: Bool =
      !idDecodeOutput(decoder.isLegal) ||
        idRfIllegal ||
        idCsrIllegalRW ||
        idSystemIllegal ||
        idMulDivIllegal.getOrElse(false.B) ||
        idAtomicIllegal.getOrElse(false.B) ||
        idFpIllegal.getOrElse(false.B) ||
        idDpIllegal.getOrElse(false.B) ||
        idCompressIllegal.getOrElse(false.B)
    val idVirtualInstruction: Bool =
      idDecodeOutput(decoder.isLegal) &&
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
    val idFenceNext:  Bool = idDecodeOutput(decoder.fence) || idDecodeOutput(decoder.amo) && idAmoAquire
    val idMemoryBusy: Bool = !dmem.ordered || dmem.req.valid
    val idDoFence =
      idMemoryBusy &&
        (idDecodeOutput(decoder.amo) && idAmoRelease ||
          idDecodeOutput(decoder.fenceI) ||
          idRegFence && idDecodeOutput(decoder.mem))

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
        (breakpointUnit.io.debug_if, CSR.debugTriggerCause.U),
        (breakpointUnit.io.xcpt_if, Causes.breakpoint.U),
        (idException0.pf.inst, Causes.fetch_page_fault.U),
        (idException0.gf.inst, Causes.fetch_guest_page_fault.U),
        (idException0.ae.inst, Causes.fetch_access.U),
        (idException1.pf.inst, Causes.fetch_page_fault.U),
        (idException1.gf.inst, Causes.fetch_guest_page_fault.U),
        (idException1.ae.inst, Causes.fetch_access.U),
        (idVirtualInstruction, Causes.virtual_instruction.U),
        (idIllegalInstruction, Causes.illegal_instruction.U)
      )
    )

    val idCoverCauses: Seq[(Int, String)] = List(
      (CSR.debugTriggerCause, "DEBUG_TRIGGER"),
      (Causes.breakpoint, "BREAKPOINT"),
      (Causes.fetch_access, "FETCH_ACCESS"),
      (Causes.illegal_instruction, "ILLEGAL_INSTRUCTION")
    ) ++ Option.when(usingVM)((Causes.fetch_page_fault, "FETCH_PAGE_FAULT"))
    // TODO: move it to verification module.
    coverExceptions(idException, idCause, "DECODE", idCoverCauses)

    // Bypass signals
    val dcacheBypassData: UInt =
      if (fastLoadByte) dmem.resp.bits.data(xLen - 1, 0)
      else if (fastLoadWord) dmem.resp.bits.data_word_bypass(xLen - 1, 0)
      else wbRegWdata
    // detect bypass opportunities
    val exWaddr:  UInt = exRegInstruction(11, 7) & regAddrMask.U
    val memWaddr: UInt = memRegInstruction(11, 7) & regAddrMask.U
    val wbWaddr:  UInt = wbRegInstruction(11, 7) & regAddrMask.U
    val bypassSources: Seq[(Bool, UInt, UInt)] = IndexedSeq(
      (true.B, 0.U, 0.U), // treat reading x0 as a bypass
      (exRegValid && exRegDecodeOutput(decoder.wxd), exWaddr, memRegWdata),
      (memRegValid && memRegDecodeOutput(decoder.wxd) && !memRegDecodeOutput(decoder.mem), memWaddr, wbRegWdata),
      (memRegValid && memRegDecodeOutput(decoder.wxd), memWaddr, dcacheBypassData)
    )
    val idBypassSources: Seq[Seq[Bool]] = idRaddr.map(raddr => bypassSources.map(s => s._1 && s._2 === raddr))

    // execute stage
    val bypassMux:     Seq[UInt] = bypassSources.map(_._3)
    val exRegRsBypass: Vec[Bool] = Reg(Vec(idRaddr.size, Bool()))
    val exRegRsLSB:    Vec[UInt] = Reg(Vec(idRaddr.size, UInt(log2Ceil(bypassSources.size).W)))
    val exRegRsMSB:    Vec[UInt] = Reg(Vec(idRaddr.size, UInt()))
    val exRs: Seq[UInt] = Seq.tabulate(idRaddr.size)(i =>
      Mux(exRegRsBypass(i), bypassMux(exRegRsLSB(i)), Cat(exRegRsMSB(i), exRegRsLSB(i)))
    )
    val exImm: SInt = ImmGen(exRegDecodeOutput(decoder.selImm), exRegInstruction)
    val exOp1: SInt =
      MuxLookup(exRegDecodeOutput(decoder.selAlu1), 0.S)(Seq(A1_RS1 -> exRs(0).asSInt, A1_PC -> exRegPC.asSInt))
    val exOp2: SInt = MuxLookup(exRegDecodeOutput(decoder.selAlu2), 0.S)(
      Seq(A2_RS2 -> exRs(1).asSInt, A2_IMM -> exImm, A2_SIZE -> Mux(exRegRVC, 2.S, 4.S))
    )

    arithmeticLogicUnit.io.dw := exRegDecodeOutput(decoder.aluDoubleWords)
    arithmeticLogicUnit.io.fn := exRegDecodeOutput(decoder.aluFn)
    arithmeticLogicUnit.io.in2 := exOp2.asUInt
    arithmeticLogicUnit.io.in1 := exOp1.asUInt

    // multiplier and divider
    // TODO: waive them if !usingMulDiv
    muldiv.io.req.valid := exRegValid && Option.when(usingMulDiv)(exRegDecodeOutput(decoder.div)).getOrElse(false.B)
    muldiv.io.req.bits.dw := exRegDecodeOutput(decoder.aluDoubleWords)
    muldiv.io.req.bits.fn := exRegDecodeOutput(decoder.aluFn)
    muldiv.io.req.bits.in1 := exRs(0)
    muldiv.io.req.bits.in2 := exRs(1)
    muldiv.io.req.bits.tag := exWaddr
    mul.foreach { m =>
      m.io.req.valid := exRegValid && exRegDecodeOutput(decoder.mul)
      m.io.req.bits := muldiv.io.req.bits
    }

    exRegValid := !ctrlKilled
    exRegReplay := !takePc && instructionBufferOut.valid && instructionBufferOut.bits.replay
    exRegException := !ctrlKilled && idException
    exRegExceptionInterrupt := !takePc && instructionBufferOut.valid && csr.io.interrupt

    // ID goes to EX
    when(!ctrlKilled) {
      exRegDecodeOutput := idDecodeOutput
      exRegRVC := instructionBufferOut.bits.rvc
      exRegDecodeOutput(decoder.csr) := idCsr
      when(idDecodeOutput(decoder.fence) && idFenceSucc === 0.U) { idRegPause := true.B }
      when(idFenceNext) { idRegFence := true.B }
      when(idException) { // pass PC down ALU writeback pipeline for badaddr
        exRegDecodeOutput(decoder.aluFn) := aluFn.FN_ADD
        exRegDecodeOutput(decoder.aluDoubleWords) := DW_XPR
        exRegDecodeOutput(decoder.selAlu1) := A1_RS1 // badaddr := instruction
        exRegDecodeOutput(decoder.selAlu2) := A2_ZERO
        when(idException1.asUInt.orR) { // badaddr := PC+2
          exRegDecodeOutput(decoder.selAlu1) := A1_PC
          exRegDecodeOutput(decoder.selAlu2) := A2_SIZE
          exRegRVC := true.B
        }
        when(breakpointUnit.io.xcpt_if || idException0.asUInt.orR) { // badaddr := PC
          exRegDecodeOutput(decoder.selAlu1) := A1_PC
          exRegDecodeOutput(decoder.selAlu2) := A2_ZERO
        }
      }
      exRegFlushPipe := idDecodeOutput(decoder.fenceI) || idCsrFlush
      exRegLoadUse := idLoadUse
      exRegHLS :=
        usingHypervisor.B &&
        idSystemInstruction &&
        idDecodeOutput(decoder.memCommand).isOneOf(M_XRD, M_XWR, M_HLVX)
      exRegMemSize := Mux(usingHypervisor.B && idSystemInstruction, idInstruction(27, 26), idInstruction(13, 12))
      when(idDecodeOutput(decoder.memCommand).isOneOf(M_SFENCE, M_HFENCEV, M_HFENCEG, M_FLUSH_ALL)) {
        exRegMemSize := Cat(idRaddr2 =/= 0.U, idRaddr1 =/= 0.U)
      }
      when(idDecodeOutput(decoder.memCommand) === M_SFENCE && csr.io.status.v) {
        exRegDecodeOutput(decoder.memCommand) := M_HFENCEV
      }

      if (tile.dcache.flushOnFenceI) {
        when(idDecodeOutput(decoder.fenceI)) {
          exRegMemSize := 0.U
        }
      }

      Seq.tabulate(idRaddr.size) { i =>
        val doBypass = idBypassSources(i).reduce(_ || _)
        val bypassSource = PriorityEncoder(idBypassSources(i))
        exRegRsBypass(i) := doBypass
        exRegRsLSB(i) := bypassSource
        when(idRen(i) && !doBypass) {
          exRegRsLSB(i) := idRs(i)(log2Ceil(bypassSources.size) - 1, 0)
          exRegRsMSB(i) := idRs(i) >> log2Ceil(bypassSources.size)
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
    val wbDcacheMiss: Bool = wbRegDecodeOutput(decoder.mem) && !dmem.resp.valid
    val replayExStructural: Bool = exRegDecodeOutput(decoder.mem) && !dmem.req.ready || Option
      .when(usingMulDiv)(exRegDecodeOutput(decoder.div))
      .getOrElse(false.B) && !muldiv.io.req.ready
    val replayExLoadUse: Bool = wbDcacheMiss && exRegLoadUse
    val replayEx:        Bool = exRegReplay || (exRegValid && (replayExStructural || replayExLoadUse))
    val ctrlKillx:       Bool = takePcMemWb || replayEx || !exRegValid
    // detect 2-cycle load-use delay for LB/LH/SC
    val exSlowBypass: Bool = exRegDecodeOutput(decoder.memCommand) === M_XSC || exRegMemSize < 2.U
    val exSfence: Bool =
      usingVM.B &&
        exRegDecodeOutput(decoder.mem) &&
        (exRegDecodeOutput(decoder.memCommand) === M_SFENCE ||
          exRegDecodeOutput(decoder.memCommand) === M_HFENCEV ||
          exRegDecodeOutput(decoder.memCommand) === M_HFENCEG)

    val (exException: Bool, exCause: UInt) = checkExceptions(
      List((exRegExceptionInterrupt || exRegException, exRegCause))
    )
    val exCoverCauses: Seq[(Int, String)] = idCoverCauses
    coverExceptions(exException, exCause, "EXECUTE", exCoverCauses)

    // memory stage
    val memPcValid: Bool = memRegValid || memRegReplay || memRegExceptionInterrupt
    val memBranchTarget: SInt = memRegPc.asSInt +
      Mux(
        memRegDecodeOutput(decoder.isBranch) && memBranchTaken,
        ImmGen(IMM_SB, memRegInstruction),
        Mux(memRegDecodeOutput(decoder.isJal), ImmGen(IMM_UJ, memRegInstruction), Mux(memRegRVC, 2.S, 4.S))
      )
    val memNextPC: UInt = (Mux(
      memRegDecodeOutput(decoder.isJalr) || memRegSfence,
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
      !memRegException && (memRegDecodeOutput(decoder.isJalr) ^ memNpcMisaligned),
      memBranchTarget,
      memRegWdata.asSInt
    ).asUInt
    val memCfi: Bool =
      memRegDecodeOutput(decoder.isBranch) || memRegDecodeOutput(decoder.isJalr) || memRegDecodeOutput(decoder.isJal)
    val memCfiTaken: Bool =
      (memRegDecodeOutput(decoder.isBranch) && memBranchTaken) || memRegDecodeOutput(
        decoder.isJalr
      ) || memRegDecodeOutput(decoder.isJal)
    val memDirectionMisprediction: Bool =
      memRegDecodeOutput(decoder.isBranch) && memBranchTaken =/= (usingBTB.B && memRegBTBResponse.taken)
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
      memRegLoad := exRegDecodeOutput(decoder.mem) && isRead(exRegDecodeOutput(decoder.memCommand))
      memRegStore := exRegDecodeOutput(decoder.mem) && isWrite(exRegDecodeOutput(decoder.memCommand))
      memRegSfence := exSfence
      memRegBTBResponse := exRegBTBResponse
      memRegFlushPipe := exRegFlushPipe
      memRegSlowBypass := exSlowBypass
      memRegWphit := exRegWphit

      memRegCause := exCause
      memRegInstruction := exRegInstruction
      memRegRawInstruction := exRegRawInstruction
      memRegMemSize := exRegMemSize
      memRegHlsOrDv := dmem.req.bits.dv
      memRegPc := exRegPC
      // IDecode ensured they are 1H
      memRegWdata := arithmeticLogicUnit.io.out
      memBranchTaken := arithmeticLogicUnit.io.cmp_out

      when(
        exRegDecodeOutput(decoder.rxs2) && (exRegDecodeOutput(decoder.mem) || exSfence)
      ) {
        val size = exRegMemSize
        memRegRS2 := new StoreGen(size, 0.U, exRs(1), coreDataBytes).data
      }.elsewhen(exRegDecodeOutput(decoder.rxs2) && exRegDecodeOutput(decoder.vector)) {
        // for setvl
        memRegRS2 := exRs(1)
      }
      when(exRegDecodeOutput(decoder.isJalr) && csr.io.status.debug) {
        // flush I$ on D-mode JALR to effect uncached fetch without D$ flush
        memRegDecodeOutput(decoder.fenceI) := true.B
        memRegFlushPipe := true.B
      }
    }

    val memBreakpoint = (memRegLoad && breakpointUnit.io.xcpt_ld) || (memRegStore && breakpointUnit.io.xcpt_st)
    val memDebugBreakpoint = (memRegLoad && breakpointUnit.io.debug_ld) || (memRegStore && breakpointUnit.io.debug_st)
    val (memLoadStoreException, memLoadStoreCause) = checkExceptions(
      List((memDebugBreakpoint, CSR.debugTriggerCause.U), (memBreakpoint, Causes.breakpoint.U))
    )

    val (memException, memCause) = checkExceptions(
      List(
        (memRegExceptionInterrupt || memRegException, memRegCause),
        (memRegValid && memNpcMisaligned, Causes.misaligned_fetch.U),
        (memRegValid && memLoadStoreException, memLoadStoreCause)
      )
    )

    val memCoverCauses = (exCoverCauses ++ List(
      (CSR.debugTriggerCause, "DEBUG_TRIGGER"),
      (Causes.breakpoint, "BREAKPOINT"),
      (Causes.misaligned_fetch, "MISALIGNED_FETCH")
    )).distinct
    coverExceptions(memException, memCause, "MEMORY", memCoverCauses)

    val dcacheKillMem =
      memRegValid && memRegDecodeOutput(decoder.wxd) && dmem.replay_next // structural hazard on writeback port
    val fpuKillMem = fpu.map(fpu => memRegValid && memRegDecodeOutput(decoder.fp) && fpu.nack_mem)
    val replayMem = dcacheKillMem || memRegReplay || fpuKillMem.getOrElse(false.B)
    val killmCommon = dcacheKillMem || takePcWb || memRegException || !memRegValid
    muldiv.io.kill := killmCommon && RegNext(muldiv.io.req.fire)
    val ctrlKillm = killmCommon || memException || fpuKillMem.getOrElse(false.B)

    // writeback stage
    wbRegValid := !ctrlKillm
    wbRegReplay := replayMem && !takePcWb
    wbRegException := memException && !takePcWb
    wbRegFlushPipe := !ctrlKillm && memRegFlushPipe
    when(memPcValid) {
      wbRegDecodeOutput := memRegDecodeOutput
      wbRegSfence := memRegSfence
      wbRegWdata := fpu
        .map(fpu =>
          Mux(
            !memRegException && memRegDecodeOutput(decoder.fp) && memRegDecodeOutput(decoder.wxd),
            fpu.toint_data,
            memIntWdata
          )
        )
        .getOrElse(memIntWdata)
      when(memRegSfence || memRegDecodeOutput(decoder.vector)) {
        wbRegRS2 := memRegRS2
      }
      wbRegCause := memCause
      wbRegInstruction := memRegInstruction
      wbRegRawInstruction := memRegRawInstruction
      wbRegMemSize := memRegMemSize
      wbRegHlsOrDv := memRegHlsOrDv
      wbRegHfenceV := memRegDecodeOutput(decoder.memCommand) === M_HFENCEV
      wbRegHfenceG := memRegDecodeOutput(decoder.memCommand) === M_HFENCEG
      wbRegPc := memRegPc
      wbRegWphit := memRegWphit | breakpointUnit.io.bpwatch.map { bpw =>
        (bpw.rvalid(0) && memRegLoad) || (bpw.wvalid(0) && memRegStore)
      }

    }

    val (wbException, wbCause) = checkExceptions(
      List(
        (wbRegException, wbRegCause),
        (wbRegValid && wbRegDecodeOutput(decoder.mem) && dmem.s2_xcpt.pf.st, Causes.store_page_fault.U),
        (wbRegValid && wbRegDecodeOutput(decoder.mem) && dmem.s2_xcpt.pf.ld, Causes.load_page_fault.U),
        (wbRegValid && wbRegDecodeOutput(decoder.mem) && dmem.s2_xcpt.gf.st, Causes.store_guest_page_fault.U),
        (wbRegValid && wbRegDecodeOutput(decoder.mem) && dmem.s2_xcpt.gf.ld, Causes.load_guest_page_fault.U),
        (wbRegValid && wbRegDecodeOutput(decoder.mem) && dmem.s2_xcpt.ae.st, Causes.store_access.U),
        (wbRegValid && wbRegDecodeOutput(decoder.mem) && dmem.s2_xcpt.ae.ld, Causes.load_access.U),
        (wbRegValid && wbRegDecodeOutput(decoder.mem) && dmem.s2_xcpt.ma.st, Causes.misaligned_store.U),
        (wbRegValid && wbRegDecodeOutput(decoder.mem) && dmem.s2_xcpt.ma.ld, Causes.misaligned_load.U)
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
    coverExceptions(wbException, wbCause, "WRITEBACK", wbCoverCauses)

    val wbPcValid: Bool = wbRegValid || wbRegReplay || wbRegException
    val wbWxd:     Bool = wbRegValid && wbRegDecodeOutput(decoder.wxd)
    val wbSetSboard: Bool =
      wbDcacheMiss ||
        Option.when(usingMulDiv)(wbRegDecodeOutput(decoder.div)).getOrElse(false.B) ||
        Option.when(usingVector){
          wbRegDecodeOutput(decoder.wxd) && wbRegDecodeOutput(decoder.vector) && !wbRegDecodeOutput(decoder.vectorCSR)
        }.getOrElse(false.B)
    val replayWbCommon: Bool = dmem.s2_nack || wbRegReplay
    val replayWbCsr:    Bool = wbRegValid && csr.io.rwStall
    val replayWb:       Bool = replayWbCommon || replayWbCsr
    takePcWb := replayWb || wbException || csr.io.eret || wbRegFlushPipe

    // writeback arbitration
    val dmemResponseXpu:    Bool = !dmem.resp.bits.tag(0).asBool
    val dmemResponseFpu:    Bool = dmem.resp.bits.tag(0).asBool
    val dmemResponseWaddr:  UInt = dmem.resp.bits.tag(5, 1)
    val dmemResponseValid:  Bool = dmem.resp.valid && dmem.resp.bits.has_data
    val dmemResponseReplay: Bool = dmemResponseValid && dmem.resp.bits.replay

    muldiv.io.resp.ready := !wbWxd
    val longlatencyWdata:    UInt = WireDefault(muldiv.io.resp.bits.data)
    val longlatencyWaddress: UInt = WireDefault(muldiv.io.resp.bits.tag)
    val longLatencyWenable:  Bool = WireDefault(muldiv.io.resp.fire)

    when(dmemResponseReplay && dmemResponseXpu) {
      muldiv.io.resp.ready := false.B
      longlatencyWaddress := dmemResponseWaddr
      longLatencyWenable := true.B
    }

    val wbValid = wbRegValid && !replayWb && !wbException
    val wbWen = wbValid && wbRegDecodeOutput(decoder.wxd)
    // RF is at WB stage
    val rfWen = wbWen || longLatencyWenable
    val rfWaddr = Mux(longLatencyWenable, longlatencyWaddress, wbWaddr)
    val rfWdata = Mux(
      dmemResponseValid && dmemResponseXpu,
      dmem.resp.bits.data(xLen - 1, 0),
      Mux(
        longLatencyWenable,
        longlatencyWdata,
        Mux(
          (wbRegDecodeOutput(decoder.csr) =/= CSR.N) || wbRegDecodeOutput(decoder.vectorCSR),
          csr.io.rw.rdata,
          Mux(
            Option.when(usingMulDiv && pipelinedMul)(wbRegDecodeOutput(decoder.mul)).getOrElse(false.B),
            mul.map(_.io.resp.bits.data).getOrElse(wbRegWdata),
            wbRegWdata
          )
        )
      )
    )
    when(rfWen) { rf.write(rfWaddr, rfWdata) }

    // hook up control/status regfile
    csr.io.ungatedClock := clock
    csr.io.decode(0).inst := idInstruction
    csr.io.exception := wbException
    csr.io.cause := wbCause
    csr.io.retire := wbValid
    csr.io.inst(0) := (
      if (usingCompressed)
        Cat(Mux(wbRegRawInstruction(1, 0).andR, wbRegInstruction >> 16, 0.U), wbRegRawInstruction(15, 0))
      else wbRegInstruction
    )
    csr.io.interrupts := interrupts
    csr.io.hartid := hartid
    fpu.map { fpu =>
      fpu.fcsr_rm := csr.io.fcsrRm
      csr.io.fcsrFlags := fpu.fcsr_flags
      fpu.time := csr.io.time(31, 0)
      fpu.hartid := hartid
    }.getOrElse {
      csr.io.fcsrFlags := DontCare
    }
    csr.io.pc := wbRegPc
    val tvalDmemAddr = !wbRegException
    val tvalAnyAddr = tvalDmemAddr ||
      wbRegCause.isOneOf(
        Causes.breakpoint.U,
        Causes.fetch_access.U,
        Causes.fetch_page_fault.U,
        Causes.fetch_guest_page_fault.U
      )
    val tvalInstruction = wbRegCause === Causes.illegal_instruction.U
    val tvalValid = wbException && (tvalAnyAddr || tvalInstruction)
    csr.io.gva := wbException && (tvalAnyAddr && csr.io.status.v || tvalDmemAddr && wbRegHlsOrDv)
    csr.io.tval := Mux(tvalValid, encodeVirtualAddress(wbRegWdata, wbRegWdata), 0.U)
    csr.io.htval := {
      val htvalValidImem = wbRegException && wbRegCause === Causes.fetch_guest_page_fault.U
      val htvalImem = Mux(htvalValidImem, imem.gpa.bits, 0.U)
      assert(!htvalValidImem || imem.gpa.valid)

      val htvalValidDmem =
        wbException && tvalDmemAddr && dmem.s2_xcpt.gf.asUInt.orR && !dmem.s2_xcpt.pf.asUInt.orR
      val htvalDmem = Mux(htvalValidDmem, dmem.s2_gpa, 0.U)

      (htvalDmem | htvalImem) >> hypervisorExtraAddrBits
    }
    ptw.ptbr := csr.io.ptbr
    ptw.hgatp := csr.io.hgatp
    ptw.vsatp := csr.io.vsatp
    ptw.customCSRs.csrs.zip(csr.io.customCSRs).foreach { case (lhs, rhs) => lhs <> rhs }
    ptw.status := csr.io.status
    ptw.hstatus := csr.io.hstatus
    ptw.gstatus := csr.io.gstatus
    ptw.pmp := csr.io.pmp
    csr.io.rw.addr := wbRegInstruction(31, 20)
    csr.io.rw.cmd := CSR.maskCmd(wbRegValid, wbRegDecodeOutput(decoder.csr))
    csr.io.rw.wdata := wbRegWdata
    csr.io.vectorCsr.foreach(_ := wbRegDecodeOutput(decoder.vectorCSR))
    csr.io.wbRegRS2.foreach(_ := wbRegRS2)

    bpwatch.zip(wbRegWphit).zip(csr.io.bp)
    bpwatch.lazyZip(wbRegWphit).lazyZip(csr.io.bp).foreach {
      case (iobpw, wphit, bp) =>
        iobpw.valid(0) := wphit
        iobpw.action := bp.control.action
        // tie off bpwatch valids
        iobpw.rvalid.foreach(_ := false.B)
        iobpw.wvalid.foreach(_ := false.B)
        iobpw.ivalid.foreach(_ := false.B)
    }

    val hazardTargets = Seq(
      (idDecodeOutput(decoder.rxs1) && idRaddr1 =/= 0.U, idRaddr1),
      (idDecodeOutput(decoder.rxs2) && idRaddr2 =/= 0.U, idRaddr2),
      (idDecodeOutput(decoder.wxd) && idWaddr =/= 0.U, idWaddr)
    )
    val fpHazardTargets = fpu.map(fpu =>
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
      if (tileParams.dcache.get.dataECC.isEmpty) longLatencyWenable && longlatencyWaddress === r
      else
        muldiv.io.resp.fire && muldiv.io.resp.bits.tag === r || dmemResponseReplay && dmemResponseXpu && dmemResponseWaddr === r
    }
    val idScoreboardHazard: Bool =
      checkHazards(hazardTargets, rd => scoreboard.read(rd) && !idScoreboardClearBypass(rd))
    scoreboard.set(wbSetSboard && wbWen, wbWaddr)

    // stall for RAW/WAW hazards on CSRs, loads, AMOs, and mul/div in execute stage.
    val exCannotBypass: Bool =
      exRegDecodeOutput(decoder.csr) =/= CSR.N ||
        exRegDecodeOutput(decoder.isJalr) ||
        exRegDecodeOutput(decoder.mem) ||
        Option.when(usingMulDiv && pipelinedMul)(exRegDecodeOutput(decoder.mul)).getOrElse(false.B) ||
        Option.when(usingMulDiv)(exRegDecodeOutput(decoder.div)).getOrElse(false.B) ||
        Option.when(usingFPU)(exRegDecodeOutput(decoder.fp)).getOrElse(false.B)
    val dataHazardEx: Bool = exRegDecodeOutput(decoder.wxd) && checkHazards(hazardTargets, _ === exWaddr)
    val fpDataHazardEx: Option[Bool] = fpHazardTargets.map(fpHazardTargets =>
      idDecodeOutput(decoder.fp) && exRegDecodeOutput(decoder.wfd) && checkHazards(fpHazardTargets, _ === exWaddr)
    )
    val idExHazard: Bool = exRegValid && (dataHazardEx && exCannotBypass || fpDataHazardEx.getOrElse(false.B))

    // stall for RAW/WAW hazards on CSRs, LB/LH, and mul/div in memory stage.
    // TODO: what's BH?
    val memMemCmdBh: Bool =
      if (fastLoadWord) (!fastLoadByte).B && memRegSlowBypass
      else true.B
    val memCannotBypass: Bool =
      memRegDecodeOutput(decoder.csr) =/= CSR.N ||
        memRegDecodeOutput(decoder.mem) && memMemCmdBh ||
        Option.when(usingMulDiv && pipelinedMul)(memRegDecodeOutput(decoder.mul)).getOrElse(false.B) ||
        Option.when(usingMulDiv)(memRegDecodeOutput(decoder.div)).getOrElse(false.B) ||
        Option.when(usingFPU)(memRegDecodeOutput(decoder.fp)).getOrElse(false.B)
    val dataHazardMem: Bool = memRegDecodeOutput(decoder.wxd) && checkHazards(hazardTargets, _ === memWaddr)
    val fpDataHazardMem: Option[Bool] = fpHazardTargets.map(fpHazardTargets =>
      idDecodeOutput(decoder.fp) &&
        memRegDecodeOutput(decoder.wfd) &&
        checkHazards(fpHazardTargets, _ === memWaddr)
    )
    val idMemHazard: Bool = memRegValid && (dataHazardMem && memCannotBypass || fpDataHazardMem.getOrElse(false.B))
    idLoadUse := memRegValid && dataHazardMem && memRegDecodeOutput(decoder.mem)
    // stall for RAW/WAW hazards on load/AMO misses and mul/div in writeback.
    val dataHazardWb: Bool = wbRegDecodeOutput(decoder.wxd) && checkHazards(hazardTargets, _ === wbWaddr)
    val fpDataHazardWb: Bool = fpHazardTargets
      .map(fpHazardTargets =>
        idDecodeOutput(decoder.fp) &&
          wbRegDecodeOutput(decoder.wfd) &&
          checkHazards(fpHazardTargets, _ === wbWaddr)
      )
      .getOrElse(false.B)
    val idWbHazard: Bool = wbRegValid && (dataHazardWb && wbSetSboard || fpDataHazardWb)
    val idStallFpu: Bool =
      fpu
        .zip(fpHazardTargets)
        .map {
          case (fpu, fpHazardTargets) =>
            val fpScoreboard = new Scoreboard(32)
            fpScoreboard.set((wbDcacheMiss && wbRegDecodeOutput(decoder.wfd) || fpu.sboard_set) && wbValid, wbWaddr)
            fpScoreboard.clear(dmemResponseReplay && dmemResponseFpu, dmemResponseWaddr)
            fpScoreboard.clear(fpu.sboard_clr, fpu.sboard_clra)
            checkHazards(fpHazardTargets, fpScoreboard.read)
        }
        .getOrElse(false.B)

    val dcacheBlocked: Bool = {
      // speculate that a blocked D$ will unblock the cycle after a Grant
      val blocked = Reg(Bool())
      blocked := !dmem.req.ready && dmem.clock_enabled && !dmem.perf.grant && (blocked || dmem.req.valid || dmem.s2_nack)
      blocked && !dmem.perf.grant
    }

    // vector stall
    val vectorLSUEmpty: Option[Bool] = Option.when(usingVector)(Wire(Bool()))
    val vectorQueueFull: Option[Bool] = Option.when(usingVector)(Wire(Bool()))
    val vectorStall: Option[Bool] = Option.when(usingVector) {
      val vectorLSUNotClear =
        (exRegValid && exRegDecodeOutput(decoder.vectorLSU)) ||
          (memRegValid && memRegDecodeOutput(decoder.vectorLSU)) ||
          (wbRegValid && wbRegDecodeOutput(decoder.vectorLSU)) || !vectorLSUEmpty.get
      (idDecodeOutput(decoder.vector) && vectorQueueFull.get) ||
        (idDecodeOutput(decoder.mem) && !idDecodeOutput(decoder.vector) && vectorLSUNotClear)
    }

    val ctrlStalld: Bool =
      idExHazard || idMemHazard || idWbHazard || idScoreboardHazard || idDoFence || idRegPause ||
        csr.io.csrStall || csr.io.singleStep && (exRegValid || memRegValid || wbRegValid) ||
        idCsrEn && csr.io.decode(0).fpCsr && !fpu.map(_.fcsr_rdy).getOrElse(false.B) || traceStall ||
        !clockEnable ||
        Option.when(usingFPU)(idDecodeOutput(decoder.fp) && idStallFpu).getOrElse(false.B) ||
        idDecodeOutput(decoder.mem) && dcacheBlocked || // reduce activity during D$ misses
        Option
          .when(usingMulDiv)(
            idDecodeOutput(
              decoder.div
            ) && (!(muldiv.io.req.ready || (muldiv.io.resp.valid && !wbWxd)) || muldiv.io.req.valid)
          )
          .getOrElse(false.B) || // reduce odds of replay
        vectorStall.getOrElse(false.B)

    ctrlKilled :=
      !instructionBuffer.io.inst(0).valid ||
        instructionBufferOut.bits.replay ||
        takePcMemWb ||
        ctrlStalld ||
        csr.io.interrupt

    imem.req.valid := takePc
    imem.req.bits.speculative := !takePcWb
    // flush or branch misprediction
    imem.req.bits.pc := Mux(
      wbException || csr.io.eret,
      csr.io.evec, // exception or [m|s]ret
      Mux(
        replayWb,
        wbRegPc, // replay
        memNextPC
      )
    )
    imem.flush_icache := wbRegValid && wbRegDecodeOutput(decoder.fenceI) && !dmem.s2_nack
    imem.might_request := {
      imemMightRequestReg := exPcValid || memPcValid || ptw.customCSRs.disableICacheClockGate
      imemMightRequestReg
    }
    imem.progress := RegNext(wbRegValid && !replayWbCommon)
    imem.sfence.valid := wbRegValid && wbRegSfence
    imem.sfence.bits.rs1 := wbRegMemSize(0)
    imem.sfence.bits.rs2 := wbRegMemSize(1)
    imem.sfence.bits.addr := wbRegWdata
    imem.sfence.bits.asid := wbRegRS2
    imem.sfence.bits.hv := wbRegHfenceV
    imem.sfence.bits.hg := wbRegHfenceG
    ptw.sfence := imem.sfence

    instructionBufferOut.ready := !ctrlStalld

    imem.btb_update.valid := memRegValid && !takePcWb && memWrongNpc && (!memCfi || memCfiTaken)
    imem.btb_update.bits.isValid := memCfi
    imem.btb_update.bits.cfiType :=
      Mux(
        (memRegDecodeOutput(decoder.isJal) || memRegDecodeOutput(decoder.isJalr)) && memWaddr(0),
        CFIType.call,
        Mux(
          memRegDecodeOutput(decoder.isJalr) && (memRegInstruction(19, 15) & regAddrMask.U) === BitPat("b00?01"),
          CFIType.ret,
          Mux(memRegDecodeOutput(decoder.isJal) || memRegDecodeOutput(decoder.isJalr), CFIType.jump, CFIType.branch)
        )
      )
    imem.btb_update.bits.target := imem.req.bits.pc
    imem.btb_update.bits.br_pc := (if (usingCompressed) memRegPc + Mux(memRegRVC, 0.U, 2.U) else memRegPc)
    imem.btb_update.bits.pc := ~(~imem.btb_update.bits.br_pc | (coreInstBytes * fetchWidth - 1).U)
    imem.btb_update.bits.prediction := memRegBTBResponse
    imem.btb_update.bits.taken := DontCare

    imem.bht_update.valid := memRegValid && !takePcWb
    imem.bht_update.bits.pc := imem.btb_update.bits.pc
    imem.bht_update.bits.taken := memBranchTaken
    imem.bht_update.bits.mispredict := memWrongNpc
    imem.bht_update.bits.branch := memRegDecodeOutput(decoder.isBranch)
    imem.bht_update.bits.prediction := memRegBTBResponse.bht

    // Connect RAS in Frontend
    imem.ras_update := DontCare

    fpu.foreach { fpu =>
      fpu.valid := !ctrlKilled && idDecodeOutput(decoder.fp)
      fpu.killx := ctrlKillx
      fpu.killm := killmCommon
      fpu.inst := idInstruction
      fpu.fromint_data := exRs(0)
      fpu.dmem_resp_val := dmemResponseValid && dmemResponseFpu
      fpu.dmem_resp_data := (if (minFLen == 32) dmem.resp.bits.data_word_bypass else dmem.resp.bits.data)
      fpu.dmem_resp_type := dmem.resp.bits.size
      fpu.dmem_resp_tag := dmemResponseWaddr
      fpu.keep_clock_enabled := ptw.customCSRs.disableCoreClockGate
    }

    t1Request.foreach { t1 =>
      t1.valid := wbRegValid && !replayWbCommon && wbRegDecodeOutput(decoder.vector)
      t1.bits.instruction := wbRegInstruction
      t1.bits.rs1Data := wbRegWdata
      t1.bits.rs2Data := wbRegRS2

      val response: DecoupledIO[VectorResponse] = t1Response.get

      // TODO: make it configurable
      val maxCount: Int = 32
      val countWidth = log2Up(maxCount)

      def counterManagement(size: Int, margin: Int = 0)(grant: Bool, release: Bool, flush: Option[Bool]=None) = {
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
      // Maintain lsu counter
      val lsuGrant: Bool = t1.valid && wbRegDecodeOutput(decoder.vectorLSU)
      val lsuRelease: Bool = response.fire && response.bits.mem
      val (lsuEmpty, _) = counterManagement(countWidth)(lsuGrant, lsuRelease)
      // Maintain vector counter
      // There may be 4 instructions in the pipe
      val (vectorEmpty, vectorFull) = counterManagement(countWidth, 4)(t1.valid, t1IssueQueueRelease.get)
      vectorLSUEmpty.foreach(_ := lsuEmpty)
      vectorQueueFull.foreach(_ := vectorFull)
    }
    // todo: vector change csr
    t1Response.foreach { vectorResponse =>
      val vectorTryToWriteRd = vectorResponse.bits.rd.valid
      vectorResponse.ready := !(wbWxd || (dmemResponseReplay && dmemResponseXpu)) || !vectorTryToWriteRd
      when(vectorResponse.fire && vectorTryToWriteRd) {
        longlatencyWdata := vectorResponse.bits.data
        longlatencyWaddress := vectorResponse.bits.rd.bits
        longLatencyWenable := true.B
      }
    }

    dmem.req.valid := exRegValid && exRegDecodeOutput(decoder.mem)
    val ex_dcache_tag = Cat(exWaddr, Option.when(usingFPU)(exRegDecodeOutput(decoder.fp)).getOrElse(false.B))
    require(coreParams.dcacheReqTagBits >= ex_dcache_tag.getWidth)
    dmem.req.bits.tag := ex_dcache_tag
    dmem.req.bits.cmd := exRegDecodeOutput(decoder.memCommand)
    dmem.req.bits.size := exRegMemSize
    dmem.req.bits.signed := !Mux(exRegHLS, exRegInstruction(20), exRegInstruction(14))
    dmem.req.bits.phys := false.B
    dmem.req.bits.addr := encodeVirtualAddress(exRs(0), arithmeticLogicUnit.io.adder_out)
    dmem.req.bits.idx.foreach(_ := dmem.req.bits.addr)
    dmem.req.bits.dprv := Mux(exRegHLS, csr.io.hstatus.spvp, csr.io.status.dprv)
    dmem.req.bits.dv := exRegHLS || csr.io.status.dv
    dmem.req.bits.no_alloc := DontCare
    dmem.req.bits.no_xcpt := DontCare
    dmem.req.bits.data := DontCare
    dmem.req.bits.mask := DontCare
    dmem.s1_data.data := fpu
      .map(fpu => Mux(memRegDecodeOutput(decoder.fp), Fill(xLen.max(fLen) / fLen, fpu.store_data), memRegRS2))
      .getOrElse(memRegRS2)
    dmem.s1_data.mask := DontCare

    dmem.s1_kill := killmCommon || memLoadStoreException || fpuKillMem.getOrElse(false.B)
    dmem.s2_kill := false.B
    // don't let D$ go to sleep if we're probably going to use it soon
    dmem.keep_clock_enabled := instructionBufferOut.valid && idDecodeOutput(decoder.mem) && !csr.io.csrStall

    // gate the clock
    val unpause: Bool =
      csr.io.time(rocketParams.lgPauseCycles - 1, 0) === 0.U || csr.io.inhibitCycle || dmem.perf.release || takePc
    when(unpause) { idRegPause := false.B }
    cease := csr.io.status.cease && !clockEnableReg
    wfi := csr.io.status.wfi
    if (rocketParams.clockGate) {
      longLatencyStall := csr.io.csrStall || dmem.perf.blocked || idRegPause && !unpause
      clockEnable := clockEnableReg || exPcValid || (!longLatencyStall && imem.resp.valid)
      clockEnableReg :=
        exPcValid || memPcValid || wbPcValid || // instruction in flight
        ptw.customCSRs.disableCoreClockGate || // chicken bit
        !muldiv.io.req.ready || // mul/div in flight
        fpu.map(!_.fcsr_rdy).getOrElse(false.B) || // long-latency FPU in flight
        dmem.replay_next || // long-latency load replaying
        (!longLatencyStall && (instructionBufferOut.valid || imem.resp.valid)) // instruction pending

      assert(!(exPcValid || memPcValid || wbPcValid) || clockEnable)
    }

    // evaluate performance counters
    val icacheBlocked = !(imem.resp.valid || RegNext(imem.resp.valid))
    csr.io.counters.foreach { c => c.inc := RegNext(perfEvents.evaluate(c.eventSel)) }
  }

  def checkExceptions(x: Seq[(Bool, UInt)]) =
    (x.map(_._1).reduce(_ || _), PriorityMux(x))

  def coverExceptions(
    exceptionValid:    Bool,
    cause:             UInt,
    labelPrefix:       String,
    coverCausesLabels: Seq[(Int, String)]
  ): Unit = {
    for ((coverCause, label) <- coverCausesLabels) {
      property.cover(exceptionValid && (cause === coverCause.U), s"${labelPrefix}_${label}")
    }
  }

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
  val rf = Mem(n, UInt(w.W))
  private def access(addr: UInt) = rf(~addr(log2Up(n) - 1, 0))
  private val reads = ArrayBuffer[(UInt, UInt)]()
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

object ImmGen {
  def apply(sel: UInt, inst: UInt) = {
    val sign = Mux(sel === IMM_Z, 0.S, inst(31).asSInt)
    val b30_20 = Mux(sel === IMM_U, inst(30, 20).asSInt, sign)
    val b19_12 = Mux(sel =/= IMM_U && sel =/= IMM_UJ, sign, inst(19, 12).asSInt)
    val b11 = Mux(
      sel === IMM_U || sel === IMM_Z,
      0.S,
      Mux(sel === IMM_UJ, inst(20).asSInt, Mux(sel === IMM_SB, inst(7).asSInt, sign))
    )
    val b10_5 = Mux(sel === IMM_U || sel === IMM_Z, 0.U, inst(30, 25))
    val b4_1 = Mux(
      sel === IMM_U,
      0.U,
      Mux(sel === IMM_S || sel === IMM_SB, inst(11, 8), Mux(sel === IMM_Z, inst(19, 16), inst(24, 21)))
    )
    val b0 = Mux(sel === IMM_S, inst(7), Mux(sel === IMM_I, inst(20), Mux(sel === IMM_Z, inst(15), 0.U)))

    Cat(sign, b30_20, b19_12, b11, b10_5, b4_1, b0).asSInt
  }
}
