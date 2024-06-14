// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.util._
import org.chipsalliance.t1.rockettile.{VectorRequest, VectorResponse}

class TileInterrupts(usingSupervisor: Boolean, nLocalInterrupts: Int) extends Bundle {
  val debug: Bool = Bool()
  val mtip:  Bool = Bool()
  val msip:  Bool = Bool()
  val meip:  Bool = Bool()
  val seip:  Option[Bool] = Option.when(usingSupervisor)(Bool())
  val lip:   Vec[Bool] = Vec(nLocalInterrupts, Bool())
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

class PTBR(pgLevels: Int, minPgLevels: Int, xLen: Int, maxPAddrBits: Int, pgIdxBits: Int) extends Bundle {
  def additionalPgLevels = mode(log2Ceil(pgLevels - minPgLevels + 1) - 1, 0)
  def pgLevelsToMode(i: Int): Int = (xLen, i) match {
    case (32, 2)                     => 1
    case (64, x) if x >= 3 && x <= 6 => x + 5
  }
  val (modeBits, maxASIdBits) = xLen match {
    case 32 => (1, 9)
    case 64 => (4, 16)
  }
  require(modeBits + maxASIdBits + maxPAddrBits - pgIdxBits == xLen)

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
  val ptbr = Input(new PTBR(pgLevels, minPgLevels, xLen, maxPAddrBits, pgIdxBits))
  val hgatp = Input(new PTBR(pgLevels, minPgLevels, xLen, maxPAddrBits, pgIdxBits))
  val vsatp = Input(new PTBR(pgLevels, minPgLevels, xLen, maxPAddrBits, pgIdxBits))
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