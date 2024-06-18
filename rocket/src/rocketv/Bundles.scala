// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.util._


class NMI(val w: Int) extends Bundle {
  val rnmi = Bool()
  val rnmi_interrupt_vector = UInt(w.W)
  val rnmi_exception_vector = UInt(w.W)
}

class TileInterrupts(usingSupervisor: Boolean, nLocalInterrupts: Int, usingNMI: Boolean, resetVectorLen: Int) extends Bundle {
  val debug: Bool = Bool()
  val mtip:  Bool = Bool()
  val msip:  Bool = Bool()
  val meip:  Bool = Bool()
  val seip:  Option[Bool] = Option.when(usingSupervisor)(Bool())
  val lip:   Vec[Bool] = Vec(nLocalInterrupts, Bool())
  val nmi = Option.when(usingNMI)(new NMI(resetVectorLen))
}

// TODO: remove BEU
class CoreInterrupts(usingSupervisor: Boolean, nLocalInterrupts: Int, hasBeu: Boolean, usingNMI: Boolean, resetVectorLen: Int) extends TileInterrupts(usingSupervisor, nLocalInterrupts, usingNMI, resetVectorLen) {
  val buserror = Option.when(hasBeu)(Bool())
}

// CSR Interface with decode stage, basically check illegal
class CSRDecodeIO(iLen: Int) extends Bundle {
  val inst = Input(UInt(iLen.W))
  val fpIllegal = Output(Bool())
  val fpCsr = Output(Bool())
  val readIllegal = Output(Bool())
  val writeIllegal = Output(Bool())
  val writeFlush = Output(Bool())
  val systemIllegal = Output(Bool())
  val virtualAccessIllegal = Output(Bool())
  val virtualSystemIllegal = Output(Bool())
}

class PerfCounterIO(xLen: Int, retireWidth: Int) extends Bundle {
  val eventSel = Output(UInt(xLen.W))
  val inc = Input(UInt(log2Ceil(1 + retireWidth).W))
}

class FrontendReq(vaddrBitsExtended: Int) extends Bundle {
  val pc = UInt(vaddrBitsExtended.W)
  val speculative = Bool()
}

class SFenceReq(vaddrBits: Int, asidBits: Int) extends Bundle {
  val rs1 = Bool()
  val rs2 = Bool()
  val addr = UInt(vaddrBits.W)
  val asid = UInt(asidBits.W)
  val hv = Bool()
  val hg = Bool()
}

// TODO: enum
object CFIType {
  def SZ = 2
  def apply() = UInt(SZ.W)
  def branch = 0.U
  def jump = 1.U
  def call = 2.U
  def ret = 3.U
}

// TODO: fetchWidth = 1
class BTBResp(
  fetchWidth:       Int,
  vaddrBits:        Int,
  entries:          Int,
  bhtHistoryLength: Option[Int],
  bhtCounterLength: Option[Int])
    extends Bundle {
  val cfiType = CFIType()
  val taken = Bool()
  val mask = Bits(fetchWidth.W)
  val bridx = Bits(log2Up(fetchWidth).W)
  val target = UInt(vaddrBits.W)
  val entry = UInt(log2Up(entries + 1).W)
  val bht = new BHTResp(bhtHistoryLength, bhtCounterLength)
}

class BHTResp(bhtHistoryLength: Option[Int], bhtCounterLength: Option[Int]) extends Bundle {
  val history = UInt(bhtHistoryLength.getOrElse(1).W)
  val value = UInt(bhtCounterLength.getOrElse(1).W)

  // @todo: change to:
  //  val history = bhtHistoryLength.map(i => UInt(i.W))
  //  val value = bhtCounterLength.map(i => UInt(i.W))
}

class BTBUpdate(
  fetchWidth:       Int,
  vaddrBits:        Int,
  entries:          Int,
  bhtHistoryLength: Option[Int],
  bhtCounterLength: Option[Int])
    extends Bundle {
  val prediction = new BTBResp(fetchWidth, vaddrBits, entries, bhtHistoryLength, bhtCounterLength)
  val pc = UInt(vaddrBits.W)
  val target = UInt(vaddrBits.W)
  val taken = Bool()
  val isValid = Bool()
  val br_pc = UInt(vaddrBits.W)
  val cfiType = CFIType()
}

class FrontendResp(
  // TODO: remove this fetchWidth = 1
  fetchWidth:        Int,
  vaddrBits:         Int,
  entries:           Int,
  bhtHistoryLength:  Option[Int],
  bhtCounterLength:  Option[Int],
  vaddrBitsExtended: Int,
  coreInstBits:      Int)
    extends Bundle {
  val btb = new BTBResp(fetchWidth, vaddrBits, entries, bhtHistoryLength: Option[Int], bhtCounterLength: Option[Int])
  val pc = UInt(vaddrBitsExtended.W) // ID stage PC
  val data = UInt((fetchWidth * coreInstBits).W)
  val mask = Bits(fetchWidth.W)
  val xcpt = new FrontendExceptions
  val replay = Bool()
}

class FrontendExceptions extends Bundle {
  val pf = Bool()
  val gf = Bool()
  val ae = Bool()
}

class FrontendPerfEvents extends Bundle {
  val acquire = Bool()
  val tlbMiss = Bool()
}

class BHTUpdate(bhtHistoryLength: Option[Int], bhtCounterLength: Option[Int], vaddrBits: Int) extends Bundle {
  val prediction = new BHTResp(bhtHistoryLength, bhtCounterLength)
  val pc = UInt(vaddrBits.W)
  val branch = Bool()
  val taken = Bool()
  val mispredict = Bool()
}

class RASUpdate(vaddrBits: Int) extends Bundle {
  val cfiType = CFIType()
  val returnAddr = UInt(vaddrBits.W)
}

class FrontendIO(
  vaddrBitsExtended: Int,
  vaddrBits:         Int,
  asidBits:          Int,
  fetchWidth:        Int,
  entries:           Int,
  bhtHistoryLength:  Option[Int],
  bhtCounterLength:  Option[Int],
  coreInstBits:      Int)
    extends Bundle {
  val might_request = Output(Bool())
  val clock_enabled = Input(Bool())
  // 1. Request Instruction -> I$(pc)
  val req = Valid(new FrontendReq(vaddrBitsExtended))
  val sfence = Valid(new SFenceReq(vaddrBits, asidBits))
  // 2. I$ response fetched [[FrontendResp.data]]
  val resp = Flipped(
    Decoupled(
      new FrontendResp(
        fetchWidth,
        vaddrBits,
        entries,
        bhtHistoryLength,
        bhtCounterLength,
        vaddrBitsExtended,
        coreInstBits
      )
    )
  )
  val gpa = Flipped(Valid(UInt(vaddrBitsExtended.W)))
  val btb_update = Valid(new BTBUpdate(fetchWidth, vaddrBits, entries, bhtHistoryLength, bhtCounterLength))
  val bht_update = Valid(new BHTUpdate(bhtHistoryLength, bhtCounterLength, vaddrBits))
  val ras_update = Valid(new RASUpdate(vaddrBits))
  val flush_icache = Output(Bool())
  val npc = Input(UInt(vaddrBitsExtended.W))
  val perf = Input(new FrontendPerfEvents())
  val progress = Output(Bool())
}

object PRV {
  val SZ = 2
  val U = 0
  val S = 1
  val H = 2
  val M = 3
}

class HellaCacheReq(
  coreMaxAddrBits:  Int,
  usingVM:          Boolean,
  untagBits:        Int,
  pgIdxBits:        Int,
  dcacheReqTagBits: Int,
  dcacheArbPorts:   Int,
  coreDataBytes:    Int)
    extends Bundle {
  require(isPow2(coreDataBytes))
  val coreDataBits: Int = coreDataBytes * 8

  val phys = Bool()
  val no_alloc = Bool()
  val no_xcpt = Bool()

  val addr = UInt(coreMaxAddrBits.W)
  val idx = Option.when(usingVM && untagBits > pgIdxBits)(UInt(coreMaxAddrBits.W))
  val tag = UInt((dcacheReqTagBits + log2Ceil(dcacheArbPorts)).W)
  // TODO: handle this uop
  val cmd = UInt(MemoryOpConstants.M_SZ.W)
  val size = UInt(log2Ceil(log2Ceil(coreDataBytes) + 1).W)
  val signed = Bool()
  // TODO: handle this uop
  val dprv = UInt(PRV.SZ.W)
  val dv = Bool()

  val data = UInt(coreDataBits.W)
  val mask = UInt(coreDataBytes.W)
}

class HellaCacheWriteData(coreDataBytes: Int) extends Bundle {
  require(isPow2(coreDataBytes))
  val coreDataBits: Int = coreDataBytes * 8

  val data = UInt(coreDataBits.W)
  val mask = UInt(coreDataBytes.W)
}

// TODO: we should have a global uop definition for enum.
object MemoryOpConstants {
  val M_SZ = 5
}

class HellaCacheResp(
  coreMaxAddrBits:  Int,
  usingVM:          Boolean,
  untagBits:        Int,
  pgIdxBits:        Int,
  dcacheReqTagBits: Int,
  dcacheArbPorts:   Int,
  coreDataBytes:    Int)
    extends Bundle {
  require(isPow2(coreDataBytes))
  val coreDataBits: Int = coreDataBytes * 8

  val replay = Bool()
  val has_data = Bool()
  val data_word_bypass = UInt(coreDataBits.W)
  val data_raw = UInt(coreDataBits.W)
  val store_data = UInt(coreDataBits.W)

  val addr = UInt(coreMaxAddrBits.W)
  val idx = Option.when(usingVM && untagBits > pgIdxBits)(UInt(coreMaxAddrBits.W))
  val tag = UInt((dcacheReqTagBits + log2Ceil(dcacheArbPorts)).W)
  val cmd = UInt(MemoryOpConstants.M_SZ.W)
  val size = UInt(log2Ceil(log2Ceil(coreDataBytes) + 1).W)
  val signed = Bool()
  val dprv = UInt(PRV.SZ.W)
  val dv = Bool()

  val data = UInt(coreDataBits.W)
  val mask = UInt(coreDataBytes.W)
}

class AlignmentExceptions extends Bundle {
  val ld = Bool()
  val st = Bool()
}

class HellaCacheExceptions extends Bundle {
  val ma = new AlignmentExceptions
  val pf = new AlignmentExceptions
  val gf = new AlignmentExceptions
  val ae = new AlignmentExceptions
}

class HellaCachePerfEvents extends Bundle {
  val acquire = Bool()
  val release = Bool()
  val grant = Bool()
  val tlbMiss = Bool()
  val blocked = Bool()
  val canAcceptStoreThenLoad = Bool()
  val canAcceptStoreThenRMW = Bool()
  val canAcceptLoadThenLoad = Bool()
  val storeBufferEmptyAfterLoad = Bool()
  val storeBufferEmptyAfterStore = Bool()
}

class HellaCacheIO(
  coreMaxAddrBits:      Int,
  usingVM:              Boolean,
  untagBits:            Int,
  pgIdxBits:            Int,
  dcacheReqTagBits:     Int,
  dcacheArbPorts:       Int,
  coreDataBytes:        Int,
  paddrBits:            Int,
  vaddrBitsExtended:    Int,
  separateUncachedResp: Boolean)
    extends Bundle {
  val req = Decoupled(
    new HellaCacheReq(coreMaxAddrBits, usingVM, untagBits, pgIdxBits, dcacheReqTagBits, dcacheArbPorts, coreDataBytes)
  )
  val s1_kill = Output(Bool()) // kill previous cycle's req
  val s1_data = Output(new HellaCacheWriteData(coreDataBytes)) // data for previous cycle's req
  val s2_nack = Input(Bool()) // req from two cycles ago is rejected
  val s2_nack_cause_raw = Input(Bool()) // reason for nack is store-load RAW hazard (performance hint)
  val s2_kill = Output(Bool()) // kill req from two cycles ago
  val s2_uncached = Input(Bool()) // advisory signal that the access is MMIO
  val s2_paddr = Input(UInt(paddrBits.W)) // translated address

  val resp = Flipped(
    Valid(
      new HellaCacheResp(
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
  val replay_next = Input(Bool())
  val s2_xcpt = Input(new HellaCacheExceptions)
  val s2_gpa = Input(UInt(vaddrBitsExtended.W))
  val s2_gpa_is_pte = Input(Bool())
  val uncached_resp = Option.when(separateUncachedResp)(
    Flipped(
      Decoupled(
        new HellaCacheResp(
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
  )
  val ordered = Input(Bool())
  val perf = Input(new HellaCachePerfEvents())

  val keep_clock_enabled = Output(Bool()) // should D$ avoid clock-gating itself?
  val clock_enabled = Input(Bool()) // is D$ currently being clocked?
}

class PTBR(xLen: Int, maxPAddrBits: Int, pgIdxBits: Int) extends Bundle {
  // @todo move it out.
  val (modeBits, maxASIdBits) = xLen match {
    case 32 => (1, 9)
    case 64 => (4, 16)
  }

  val mode: UInt = UInt(modeBits.W)
  val asid = UInt(maxASIdBits.W)
  val ppn = UInt((maxPAddrBits - pgIdxBits).W)
}

class MStatus extends Bundle {
  // not truly part of mstatus, but convenient
  val debug = Bool()
  val cease = Bool()
  val wfi = Bool()
  val isa = UInt(32.W)

  val dprv = UInt(PRV.SZ.W) // effective prv for data accesses
  val dv = Bool() // effective v for data accesses
  val prv = UInt(PRV.SZ.W)
  val v = Bool()

  val sd = Bool()
  val zero2 = UInt(23.W)
  val mpv = Bool()
  val gva = Bool()
  val mbe = Bool()
  val sbe = Bool()
  val sxl = UInt(2.W)
  val uxl = UInt(2.W)
  val sd_rv32 = Bool()
  val zero1 = UInt(8.W)
  val tsr = Bool()
  val tw = Bool()
  val tvm = Bool()
  val mxr = Bool()
  val sum = Bool()
  val mprv = Bool()
  val xs = UInt(2.W)
  val fs = UInt(2.W)
  val mpp = UInt(2.W)
  val vs = UInt(2.W)
  val spp = UInt(1.W)
  val mpie = Bool()
  val ube = Bool()
  val spie = Bool()
  val upie = Bool()
  val mie = Bool()
  val hie = Bool()
  val sie = Bool()
  val uie = Bool()
}

class HStatus extends Bundle {
  val zero6 = UInt(30.W)
  val vsxl = UInt(2.W)
  val zero5 = UInt(9.W)
  val vtsr = Bool()
  val vtw = Bool()
  val vtvm = Bool()
  val zero3 = UInt(2.W)
  val vgein = UInt(6.W)
  val zero2 = UInt(2.W)
  val hu = Bool()
  val spvp = Bool()
  val spv = Bool()
  val gva = Bool()
  val vsbe = Bool()
  val zero1 = UInt(5.W)
}

class DCSR extends Bundle {
  val xdebugver = UInt(2.W)
  val zero4 = UInt(2.W)
  val zero3 = UInt(12.W)
  val ebreakm = Bool()
  val ebreakh = Bool()
  val ebreaks = Bool()
  val ebreaku = Bool()
  val zero2 = Bool()
  val stopcycle = Bool()
  val stoptime = Bool()
  val cause = UInt(3.W)
  val v = Bool()
  val zero1 = UInt(2.W)
  val step = Bool()
  val prv = UInt(PRV.SZ.W)
}

class MIP(nLocalInterrupts: Int) extends Bundle {
  val lip = Vec(nLocalInterrupts, Bool())
  val zero1 = Bool()
  val debug = Bool() // keep in sync with CSR.debugIntCause
  val sgeip = Bool()
  val meip = Bool()
  val vseip = Bool()
  val seip = Bool()
  val ueip = Bool()
  val mtip = Bool()
  val vstip = Bool()
  val stip = Bool()
  val utip = Bool()
  val msip = Bool()
  val vssip = Bool()
  val ssip = Bool()
  val usip = Bool()
}

class MNStatus extends Bundle {
  val mpp = UInt(2.W)
  val zero3 = UInt(3.W)
  val mpv = Bool()
  val zero2 = UInt(3.W)
  val mie = Bool()
  val zero1 = UInt(3.W)
}

class Envcfg extends Bundle {
  val stce = Bool() // only for menvcfg/henvcfg
  val pbmte = Bool() // only for menvcfg/henvcfg
  val zero54 = UInt(54.W)
  val cbze = Bool()
  val cbcfe = Bool()
  val cbie = UInt(2.W)
  val zero3 = UInt(3.W)
  val fiom = Bool()
}

object PMP {
  def lgAlign = 2
}

class PMP(paddrBits: Int) extends Bundle {
  val mask = UInt(paddrBits.W)
  val cfg = new PMPConfig
  val addr = UInt((paddrBits - PMP.lgAlign).W)
}

class PMPConfig extends Bundle {
  val l = Bool()
  val res = UInt(2.W)
  val a = UInt(2.W)
  val x = Bool()
  val w = Bool()
  val r = Bool()
}

class PMPReg(paddrBits: Int) extends Bundle {
  val cfg = new PMPConfig
  val addr = UInt((paddrBits - PMP.lgAlign).W)
}


class PTWPerfEvents extends Bundle {
  val l2miss = Bool()
  val l2hit = Bool()
  val pte_miss = Bool()
  val pte_hit = Bool()
}

class DatapathPTWIO(
  pgLevels:     Int,
  minPgLevels:  Int,
  xLen:         Int,
  maxPAddrBits: Int,
  pgIdxBits:    Int,
  vaddrBits:    Int,
  asidBits:     Int,
  nPMPs:        Int,
  paddrBits:    Int)
    extends Bundle {
  val ptbr = Input(new PTBR(xLen, maxPAddrBits, pgIdxBits))
  val hgatp = Input(new PTBR(xLen, maxPAddrBits, pgIdxBits))
  val vsatp = Input(new PTBR(xLen, maxPAddrBits, pgIdxBits))
  val sfence = Flipped(Valid(new SFenceReq(vaddrBits, asidBits)))
  val status = Input(new MStatus())
  val hstatus = Input(new HStatus())
  val gstatus = Input(new MStatus())
  val pmp = Input(Vec(nPMPs, new PMP(paddrBits)))
  val perf = Output(new PTWPerfEvents())
  // No customCSR for the first time refactor.
  // val customCSRs = Flipped(coreParams.customCSRs)

  /** enable clock generated by ptw */
  val clock_enabled = Output(Bool())
}

// TODO: remove me.
object FPConstants {
  val RM_SZ = 3
  val FLAGS_SZ = 5
}

class FPUCtrlSigs extends Bundle {
  val ldst = Bool()
  val wen = Bool()
  val ren1 = Bool()
  val ren2 = Bool()
  val ren3 = Bool()
  val swap12 = Bool()
  val swap23 = Bool()
  val typeTagIn = UInt(2.W)
  val typeTagOut = UInt(2.W)
  val fromint = Bool()
  val toint = Bool()
  val fastpipe = Bool()
  val fma = Bool()
  val div = Bool()
  val sqrt = Bool()
  val wflags = Bool()
}

class FPUCoreIO(hartIdLen: Int, xLen: Int, fLen: Int) extends Bundle {
  val hartid = Input(UInt(hartIdLen.W))
  val time = Input(UInt(xLen.W))

  val inst = Input(UInt(32.W))
  val fromint_data = Input(UInt(xLen.W))

  val fcsr_rm = Input(Bits(FPConstants.RM_SZ.W))
  val fcsr_flags = Valid(Bits(FPConstants.FLAGS_SZ.W))

  val store_data = Output(Bits(fLen.W))
  val toint_data = Output(Bits(xLen.W))

  val dmem_resp_val = Input(Bool())
  val dmem_resp_type = Input(Bits(3.W))
  val dmem_resp_tag = Input(UInt(5.W))
  val dmem_resp_data = Input(Bits(fLen.W))

  val valid = Input(Bool())
  val fcsr_rdy = Output(Bool())
  val nack_mem = Output(Bool())
  val illegal_rm = Output(Bool())
  val killx = Input(Bool())
  val killm = Input(Bool())
  val dec = Output(new FPUCtrlSigs())
  val sboard_set = Output(Bool())
  val sboard_clr = Output(Bool())
  val sboard_clra = Output(UInt(5.W))

  val keep_clock_enabled = Input(Bool())
}

class BPWatch extends Bundle() {
  val valid = Bool()
  val rvalid = Bool()
  val wvalid = Bool()
  val ivalid = Bool()
  val action = UInt(3.W)
}
class BPControl(xLen: Int, useBPWatch: Boolean) extends Bundle {
  val ttype = UInt(4.W)
  val dmode = Bool()
  val maskmax = UInt(6.W)
  val reserved = UInt((xLen - (if (useBPWatch) 26 else 24)).W)
  val action = UInt((if (useBPWatch) 3 else 1).W)
  val chain = Bool()
  val zero = UInt(2.W)
  val tmatch = UInt(2.W)
  val m = Bool()
  val h = Bool()
  val s = Bool()
  val u = Bool()
  val x = Bool()
  val w = Bool()
  val r = Bool()
}

class TExtra(xLen: Int, mcontextWidth: Int, scontextWidth: Int) extends Bundle {
  // TODO: pass it from parameter
  def mvalueBits: Int = if (xLen == 32) mcontextWidth.min(6) else mcontextWidth.min(13)
  def svalueBits: Int = if (xLen == 32) scontextWidth.min(16) else scontextWidth.min(34)
  def mselectPos: Int = if (xLen == 32) 25 else 50
  def mvaluePos:  Int = mselectPos + 1
  def sselectPos: Int = 0
  def svaluePos:  Int = 2

  val mvalue = UInt(mvalueBits.W)
  val mselect = Bool()
  val pad2 = UInt((mselectPos - svalueBits - 2).W)
  val svalue = UInt(svalueBits.W)
  val pad1 = UInt(1.W)
  val sselect = Bool()
}


class BP(xLen: Int, useBPWatch: Boolean, vaddrBits: Int, mcontextWidth: Int, scontextWidth: Int) extends Bundle {
  val control = new BPControl(xLen, useBPWatch)
  val address = UInt(vaddrBits.W)
  val textra = new TExtra(xLen, mcontextWidth, scontextWidth)
}


class BTBReq(vaddrBits: Int) extends Bundle {
  val addr = UInt(vaddrBits.W)
}

class ICacheReq(vaddrBits: Int) extends Bundle {
  val addr = UInt(vaddrBits.W)
}


class ICacheResp(fetchBytes: Int) extends Bundle {

  /** data to CPU.
    * @todo why 4 instructions?
    */
  val data = UInt((fetchBytes * 8).W)

  /** ask CPU to replay fetch when tag or data ECC error happened. */
  val replay = Bool()

  /** access exception:
    * indicate CPU an tag ECC error happened.
    * if [[outer.icacheParams.latency]] is 1, tie 0.
    */
  val ae = Bool()

}

class ICacheErrors(hasCorrectable: Boolean, hasUncorrectable: Boolean, paddrBits: Int) extends Bundle {
  val correctable = Option.when(hasCorrectable)(Valid(UInt(paddrBits.W)))
  val uncorrectable = Option.when(hasUncorrectable)(Valid(UInt(paddrBits.W)))
  val bus = Valid(UInt(paddrBits.W))
}

class ICachePerfEvents extends Bundle {
  val acquire = Bool()
}


/** IO between TLB and PTW
  *
  * PTW receives :
  *   - PTE request
  *   - CSRs info
  *   - pmp results from PMP(in TLB)
  */
class TLBPTWIO(nPMPs: Int, vpnBits: Int, paddrBits: Int, vaddrBits: Int, pgLevels: Int, xLen: Int, maxPAddrBits: Int, pgIdxBits: Int) extends Bundle {
  val req = Decoupled(Valid(new PTWReq(vpnBits)))
  val resp = Flipped(Valid(new PTWResp(vaddrBits, pgLevels)))
  val ptbr = Input(new PTBR(xLen, maxPAddrBits, pgIdxBits))
  val hgatp = Input(new PTBR(xLen, maxPAddrBits, pgIdxBits))
  val vsatp = Input(new PTBR(xLen, maxPAddrBits, pgIdxBits))
  val status = Input(new MStatus)
  val hstatus = Input(new HStatus)
  val gstatus = Input(new MStatus)
  val pmp = Input(Vec(nPMPs, new PMP(paddrBits)))
  // No customCSR for the first time refactor.
  //  val customCSRs = Flipped(coreParams.customCSRs)
}

class PTWReq(vpnBits: Int) extends Bundle {
  val addr = UInt(vpnBits.W)
  val need_gpa = Bool()
  val vstage1 = Bool()
  val stage2 = Bool()
}

/** PTE info from L2TLB to TLB
  *
  * containing: target PTE, exceptions, two-satge tanslation info
  */
class PTWResp(vaddrBits: Int, pgLevels: Int) extends Bundle {

  /** ptw access exception */
  val ae_ptw = Bool()

  /** final access exception */
  val ae_final = Bool()

  /** page fault */
  val pf = Bool()

  /** guest page fault */
  val gf = Bool()

  /** hypervisor read */
  val hr = Bool()

  /** hypervisor write */
  val hw = Bool()

  /** hypervisor execute */
  val hx = Bool()

  /** PTE to refill L1TLB
    *
    * source: L2TLB
    */
  val pte = new PTE

  /** pte pglevel */
  val level = UInt(log2Ceil(pgLevels).W)

  /** fragmented_superpage support */
  val fragmented_superpage = Bool()

  /** homogeneous for both pma and pmp */
  val homogeneous = Bool()
  val gpa = Valid(UInt(vaddrBits.W))
  val gpa_is_pte = Bool()
}


/** PTE template for transmission
  *
  * contains useful methods to check PTE attributes
  * @see RV-priv spec 4.3.1 for pgae table entry format
  */
class PTE extends Bundle {
  val reserved_for_future = UInt(10.W)
  val ppn = UInt(44.W)
  val reserved_for_software = Bits(2.W)

  /** dirty bit */
  val d = Bool()

  /** access bit */
  val a = Bool()

  /** global mapping */
  val g = Bool()

  /** user mode accessible */
  val u = Bool()

  /** whether the page is executable */
  val x = Bool()

  /** whether the page is writable */
  val w = Bool()

  /** whether the page is readable */
  val r = Bool()

  /** valid bit */
  val v = Bool()
}

class DCacheErrors(hasCorrectable: Boolean, hasUncorrectable: Boolean, paddrBits: Int) extends Bundle {
  val correctable = Option.when(hasCorrectable)(Valid(UInt(paddrBits.W)))
  val uncorrectable = Option.when(hasUncorrectable)(Valid(UInt(paddrBits.W)))
  val bus = Valid(UInt(paddrBits.W))
}

class DCacheTLBPort(paddrBits: Int, vaddrBitsExtended: Int, coreDataBytes: Int) extends Bundle {
  val req = Flipped(Decoupled(new TLBReq(log2Ceil(coreDataBytes), vaddrBitsExtended)))
  val s1_resp = Output(new TLBResp(paddrBits, vaddrBitsExtended))
  val s2_kill = Input(Bool())
}

class TLBReq(lgMaxSize: Int, vaddrBitsExtended: Int)() extends Bundle {
  // TODO: remove it.
  val M_SZ = 5

  /** request address from CPU. */
  val vaddr = UInt(vaddrBitsExtended.W)

  /** don't lookup TLB, bypass vaddr as paddr */
  val passthrough = Bool()

  /** granularity */
  val size = UInt(log2Ceil(lgMaxSize + 1).W)

  /** memory command. */
  val cmd = Bits(M_SZ.W)
  val prv = UInt(PRV.SZ.W)

  /** virtualization mode */
  val v = Bool()

}


class TLBResp(paddrBits: Int, vaddrBitsExtended: Int) extends Bundle {
  // lookup responses
  val miss = Bool()

  /** physical address */
  val paddr = UInt(paddrBits.W)
  val gpa = UInt(vaddrBitsExtended.W)
  val gpa_is_pte = Bool()

  /** page fault exception */
  val pf = new TLBExceptions

  /** guest page fault exception */
  val gf = new TLBExceptions

  /** access exception */
  val ae = new TLBExceptions

  /** misaligned access exception */
  val ma = new TLBExceptions

  /** if this address is cacheable */
  val cacheable = Bool()

  /** if caches must allocate this address */
  val must_alloc = Bool()

  /** if this address is prefetchable for caches */
  val prefetchable = Bool()
}

class TLBExceptions extends Bundle {
  val ld = Bool()
  val st = Bool()
  val inst = Bool()
}

class DCacheMetadataReq(vaddrBitsExtended: Int, idxBits: Int, nWays: Int, dataWidth: Int) extends Bundle {
  val write = Bool()
  val addr = UInt(vaddrBitsExtended.W)
  val idx = UInt(idxBits.W)
  val way_en = UInt(nWays.W)
  val data = UInt(dataWidth.W)
}

class L1Metadata(tagBits:Int) extends Bundle {
  val coh = new ClientMetadata
  val tag = UInt(tagBits.W)
}

class ClientMetadata extends Bundle {
//  val state = UInt(ClientStates.width.W)
  /** L1 non-coherent: MI bits */
  val state = UInt(2.W)
}

class DCacheDataReq(untagBits: Int, encBits: Int, rowBytes: Int, eccBytes: Int, subWordBytes: Int, wordBytes: Int, nWays: Int) extends Bundle {
  val addr = UInt(untagBits.W)
  val write = Bool()
  val wdata = UInt((encBits * rowBytes / eccBytes).W)
  val wordMask = UInt((rowBytes / subWordBytes).W)
  val eccMask = UInt((wordBytes / eccBytes).W)
  val way_en = UInt(nWays.W)
}
