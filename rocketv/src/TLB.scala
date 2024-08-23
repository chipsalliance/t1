// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.{Instance, Instantiate, instantiable}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util.{Cat, Decoupled, Enum, Fill, Mux1H, OHToUInt, PopCount, PriorityEncoder, UIntToOH, Valid, log2Ceil}
import chisel3.ltl._
import chisel3.ltl.Sequence._

object TLBParameter {
  implicit def rwP: upickle.default.ReadWriter[TLBParameter] = upickle.default.macroRW[TLBParameter]
}

case class TLBParameter(
                         useAsyncReset: Boolean,
                         xLen: Int,
                         nSets: Int,
                         nWays: Int,
                         nSectors: Int,
                         nSuperpageEntries: Int,
                         asidBits: Int,
                         pgLevels: Int,
                         usingHypervisor: Boolean,
                         usingAtomics: Boolean,
                         usingDataScratchpad: Boolean,
                         usingAtomicsOnlyForIO: Boolean,
                         usingVM: Boolean,
                         usingAtomicsInCache: Boolean,
                         nPMPs: Int,
                         pmaCheckerParameter: PMACheckerParameter,
                         paddrBits: Int,
                         isITLB: Boolean
                       ) extends SerializableModuleParameter {
  require(nWays > nSectors, s"nWays: ${nWays} > nSectors: ${nSectors}")
  // D$: log2Ceil(coreDataBytes), I$: log2Ceil(fetchBytes)
  def lgMaxSize = log2Ceil(xLen / 8)

  def pmpCheckerParameter: PMPCheckerParameter = PMPCheckerParameter(nPMPs, paddrBits, lgMaxSize, pmpGranularity)

  def vpnBits: Int = vaddrBits - pgIdxBits

  def ppnBits: Int = paddrBits - pgIdxBits

  private def vpnBitsExtended: Int = vpnBits + (if (vaddrBits < xLen) 1 + (if (usingHypervisor) 1 else 0) else 0)

  def vaddrBitsExtended: Int = vpnBitsExtended + pgIdxBits

  def maxSVAddrBits: Int = pgIdxBits + pgLevels * pgLevelBits

  def maxHVAddrBits: Int = maxSVAddrBits + hypervisorExtraAddrBits

  def vaddrBits: Int = if (usingVM) {
    val v = maxHVAddrBits
    require(v == xLen || xLen > v && v > paddrBits)
    v
  } else {
    // since virtual addresses sign-extend but physical addresses
    // zero-extend, make room for a zero sign bit for physical addresses
    (paddrBits + 1).min(xLen)
  }

  def minPgLevels: Int = {
    val res = xLen match {
      case 32 => 2
      case 64 => 3
    }
    require(pgLevels >= res)
    res
  }

  def pgLevelBits: Int = 10 - log2Ceil(xLen / 32)

  def maxHypervisorExtraAddrBits: Int = 2

  def hypervisorExtraAddrBits: Int = {
    if (usingHypervisor) maxHypervisorExtraAddrBits
    else 0
  }

  def maxPAddrBits: Int = xLen match {
    case 32 => 34
    case 64 => 56
  }

  def pgIdxBits: Int = 12

  def pmpGranularity: Int = if (usingHypervisor) 4096 else 4
}

class TLBInterface(parameter: TLBParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())

  /** request from Core */
  val req = Flipped(Decoupled(new TLBReq(parameter.lgMaxSize, parameter.vaddrBitsExtended)))

  /** response to Core */
  val resp = Output(new TLBResp(parameter.paddrBits, parameter.vaddrBitsExtended))

  /** SFence Input */
  val sfence = Flipped(Valid(new SFenceReq(parameter.vaddrBits, parameter.asidBits)))

  /** IO to PTW */
  val ptw = new TLBPTWIO(
    parameter.nPMPs,
    parameter.vpnBits,
    parameter.paddrBits,
    parameter.vaddrBits,
    parameter.pgLevels,
    parameter.xLen,
    parameter.maxPAddrBits,
    parameter.pgIdxBits
  )

  /** suppress a TLB refill, one cycle after a miss */
  val kill = Input(Bool())
}

@instantiable
class TLB(val parameter: TLBParameter)
  extends FixedIORawModule(new TLBInterface(parameter))
    with SerializableModule[TLBParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val pmpGranularity = parameter.pmpGranularity
  val vaddrBits = parameter.vaddrBits
  val vaddrBitsExtended = parameter.vaddrBitsExtended
  val pgIdxBits = parameter.pgIdxBits
  val pgLevels = parameter.pgLevels
  val minPgLevels = parameter.minPgLevels
  val pgLevelBits = parameter.pgLevelBits
  val hypervisorExtraAddrBits = parameter.hypervisorExtraAddrBits
  val vpnBits = parameter.vpnBits
  val ppnBits = parameter.ppnBits
  val usingHypervisor = parameter.usingHypervisor
  val usingAtomics = parameter.usingAtomics
  val usingVM = parameter.usingVM
  val usingDataScratchpad = parameter.usingDataScratchpad
  val usingAtomicsOnlyForIO = parameter.usingAtomicsOnlyForIO
  val instruction = parameter.isITLB
  val usingAtomicsInCache = parameter.usingAtomicsInCache
  val lgMaxSize = parameter.lgMaxSize
  def M_XLR = "b00110".U
  def M_XSC = "b00111".U

  def M_XA_SWAP = "b00100".U
  def M_XA_XOR = "b01001".U
  def M_XA_OR = "b01010".U
  def M_XA_AND = "b01011".U
  def M_XA_ADD = "b01000".U
  def M_XA_MIN = "b01100".U
  def M_XA_MAX = "b01101".U
  def M_XA_MINU = "b01110".U
  def M_XA_MAXU = "b01111".U
  def M_PWR = "b10001".U // partial (masked) store
  def M_XRD = "b00000".U; // int load
  def M_HLVX = "b10000".U // HLVX instruction
  def M_XWR = "b00001".U; // int store
  def M_FLUSH_ALL = "b00101".U
  def M_WOK = "b10111".U // check write permissions but don't perform a write

  // compatibility mode
  object cfg {
    val nSets:             Int = parameter.nSets
    val nWays:             Int = parameter.nWays
    val nSectors:          Int = parameter.nSectors
    val nSuperpageEntries: Int = parameter.nSuperpageEntries
  }
  object PopCountAtLeast {
    private def two(x: UInt): (Bool, Bool) = x.getWidth match {
      case 1 => (x.asBool, false.B)
      case n =>
        val half = x.getWidth / 2
        val (leftOne, leftTwo) = two(x(half - 1, 0))
        val (rightOne, rightTwo) = two(x(x.getWidth - 1, half))
        (leftOne || rightOne, leftTwo || rightTwo || (leftOne && rightOne))
    }
    def apply(x: UInt, n: Int): Bool = n match {
      case 0 => true.B
      case 1 => x.orR
      case 2 => two(x)._2
      case 3 => PopCount(x) >= n.U
    }
  }

  // end

  val pmp: Instance[PMPChecker] = Instantiate(new PMPChecker(parameter.pmpCheckerParameter))

  //  io.ptw.customCSRs := DontCare

  val pageGranularityPMPs = pmpGranularity >= (1 << parameter.pgIdxBits)
  val vpn = io.req.bits.vaddr(vaddrBits - 1, pgIdxBits)

  /** index for sectored_Entry */
  val memIdx = if (log2Ceil(cfg.nSets) == 0) 0.U else vpn(log2Ceil(cfg.nSectors) + log2Ceil(cfg.nSets) - 1, log2Ceil(cfg.nSectors))

  /** TLB Entry */
  // val superpage: Boolean = false, val superpageOnly: Boolean = false
  val sectored_entries = Reg(
    Vec(cfg.nSets, Vec(cfg.nWays / cfg.nSectors, new TLBEntry(cfg.nSectors, pgLevels, vpnBits, ppnBits)))
  )

  /** Superpage Entry */
  // val superpage: Boolean = true, val superpageOnly: Boolean = true
  val superpage_entries = Reg(Vec(cfg.nSuperpageEntries, new TLBEntry(1, pgLevels, vpnBits, ppnBits)))

  /** Special Entry
    *
    * If PMP granularity is less than page size, thus need additional "special" entry manage PMP.
    */
  // val superpage: Boolean = true, val superpageOnly: Boolean = false
  val special_entry = Option.when(!pageGranularityPMPs)(Reg(new TLBEntry(1, pgLevels, vpnBits, ppnBits)))
  def ordinary_entries = sectored_entries(memIdx) ++ superpage_entries
  def all_entries = ordinary_entries ++ special_entry
  def allEntries =
    sectored_entries(memIdx).map(tlb => (tlb, false, false)) ++
    superpage_entries.map(tlb => (tlb, true, true)) ++
    special_entry.map(tlb => (tlb, true, false))

  def all_real_entries = sectored_entries.flatten ++ superpage_entries ++ special_entry

  val s_ready :: s_request :: s_wait :: s_wait_invalidate :: Nil = Enum(4)
  val state = RegInit(s_ready)
  // use vpn as refill_tag
  val r_refill_tag = Reg(UInt(vpnBits.W))
  val r_superpage_repl_addr = Reg(UInt(log2Ceil(superpage_entries.size).W))
  val r_sectored_repl_addr = Reg(UInt(log2Ceil(sectored_entries.head.size).W))
  val r_sectored_hit = Reg(Valid(UInt(log2Ceil(sectored_entries.head.size).W)))
  val r_superpage_hit = Reg(Valid(UInt(log2Ceil(superpage_entries.size).W)))
  val r_vstage1_en = Reg(Bool())
  val r_stage2_en = Reg(Bool())
  val r_need_gpa = Reg(Bool())
  val r_gpa_valid = Reg(Bool())
  val r_gpa = Reg(UInt(vaddrBits.W))
  val r_gpa_vpn = Reg(UInt(vpnBits.W))
  val r_gpa_is_pte = Reg(Bool())

  /** privilege mode */
  val priv = io.req.bits.prv
  val priv_v = usingHypervisor.B && io.req.bits.v
  val priv_s = priv(0)
  // user mode and supervisor mode
  val priv_uses_vm = priv <= PRV.S.U
  val satp = Mux(priv_v, io.ptw.vsatp, io.ptw.ptbr)
  val stage1_en = usingVM.B && satp.mode(satp.mode.getWidth - 1)

  /** VS-stage translation enable */
  val vstage1_en = usingHypervisor.B && priv_v && io.ptw.vsatp.mode(io.ptw.vsatp.mode.getWidth - 1)

  /** G-stage translation enable */
  val stage2_en = usingHypervisor.B && priv_v && io.ptw.hgatp.mode(io.ptw.hgatp.mode.getWidth - 1)

  /** Enable Virtual Memory when:
    *  1. statically configured
    *  1. satp highest bits enabled
    *   i. RV32:
    *     - 0 -> Bare
    *     - 1 -> SV32
    *   i. RV64:
    *     - 0000 -> Bare
    *     - 1000 -> SV39
    *     - 1001 -> SV48
    *     - 1010 -> SV57
    *     - 1011 -> SV64
    *  1. In virtualization mode, vsatp highest bits enabled
    *  1. priv mode in U and S.
    *  1. in H & M mode, disable VM.
    *  1. no passthrough(micro-arch defined.)
    *
    * @see RV-priv spec 4.1.11 Supervisor Address Translation and Protection (satp) Register
    * @see RV-priv spec 8.2.18 Virtual Supervisor Address Translation and Protection Register (vsatp)
    */
  val vm_enabled = (stage1_en || stage2_en) && priv_uses_vm && !io.req.bits.passthrough

  // flush guest entries on vsatp.MODE Bare <-> SvXX transitions
  val v_entries_use_stage1 = RegInit(false.B)
  val vsatp_mode_mismatch = priv_v && (vstage1_en =/= v_entries_use_stage1) && !io.req.bits.passthrough

  // share a single physical memory attribute checker (unshare if critical path)
  val refill_ppn = io.ptw.resp.bits.pte.ppn(ppnBits - 1, 0)

  /** refill signal */
  val do_refill = usingVM.B && io.ptw.resp.valid

  def isOneOf(x: UInt, s: Seq[UInt]): Bool = VecInit(s.map(x === _)).asUInt.orR

  /** sfence invalidate refill */
  val invalidate_refill = isOneOf(state, Seq(s_request /* don't care */, s_wait_invalidate)) || io.sfence.valid
  // PMP
  val mpu_ppn = Mux[UInt](
    do_refill,
    refill_ppn,
    Mux(
      vm_enabled && special_entry.nonEmpty.B,
      special_entry.map(e => TLBEntry.ppn(e, vpn, TLBEntry.getData(e, vpn), usingVM, pgLevelBits, true, false)).getOrElse(0.U),
      io.req.bits.vaddr >> pgIdxBits
    )
  )
  val mpu_physaddr = Cat(mpu_ppn, io.req.bits.vaddr(pgIdxBits - 1, 0))
  val mpu_priv =
    Mux[UInt](usingVM.B && (do_refill || io.req.bits.passthrough /* PTW */ ), PRV.S.U, Cat(io.ptw.status.debug, priv))
  pmp.io.addr := mpu_physaddr
  pmp.io.size := io.req.bits.size
  pmp.io.pmp := (io.ptw.pmp: Seq[PMP])
  pmp.io.prv := mpu_priv
  // PMA
  val pma = Instantiate(new PMAChecker(parameter.pmaCheckerParameter))
  // check exist a slave can consume this address.
  pma.io.paddr := mpu_physaddr
  // todo: using DataScratchpad doesn't support cacheable.
  def checkCacheable: Bool = pma.io.resp.cacheable
  def checkR: Bool = pma.io.resp.r
  def checkW: Bool = pma.io.resp.w
  def checkPP: Bool = pma.io.resp.pp
  def checkAL: Bool = pma.io.resp.al
  def checkAA: Bool = pma.io.resp.aa
  def checkX: Bool = pma.io.resp.x
  def checkEFF: Bool = pma.io.resp.eff

  // In M mode, if access DM address(debug module program buffer)
  //  @todo val homogeneous = TLBPageLookup(edge.manager.managers, xLen, p(CacheBlockBytes), BigInt(1) << pgIdxBits)(mpu_physaddr).homogeneous
  val homogeneous = true.B
  //  val deny_access_to_debug = mpu_priv <= PRV.M.U && p(DebugModuleKey).map(dmp => dmp.address.contains(mpu_physaddr)).getOrElse(false.B)
  val deny_access_to_debug: Bool = false.B
  val cacheable: Bool = checkCacheable && (instruction || !usingDataScratchpad).B
  val prot_r: Bool = checkR && !deny_access_to_debug && pmp.io.r
  val prot_w: Bool = checkW && !deny_access_to_debug && pmp.io.w
  val prot_pp: Bool = checkPP
  val prot_al: Bool = checkAL
  val prot_aa: Bool = checkAA
  val prot_x: Bool = checkX && !deny_access_to_debug && pmp.io.x
  val prot_eff: Bool = checkEFF

  // hit check
  val sector_hits = sectored_entries(memIdx).map(tlbEntry => TLBEntry.sectorHit(tlbEntry, vpn, priv_v))
  val superpage_hits = superpage_entries.map(tlbEntry => TLBEntry.hit(tlbEntry, vpn, priv_v, usingVM, pgLevelBits, hypervisorExtraAddrBits, superpage = true, superpageOnly = true))
  val hitsVec = VecInit(allEntries.map{case (tlbEntry, superpage, superpageOnly) => vm_enabled && TLBEntry.hit(tlbEntry, vpn, priv_v, usingVM: Boolean, pgLevelBits: Int, hypervisorExtraAddrBits: Int, superpage, superpageOnly)})
  val real_hits = hitsVec.asUInt
  val hits = Cat(!vm_enabled, real_hits)

  // use ptw response to refill
  // permission bit arrays
  when(do_refill) {
    val pte = io.ptw.resp.bits.pte
    val refill_v = r_vstage1_en || r_stage2_en
    val newEntry = Wire(new TLBEntryData(ppnBits))
    newEntry.ppn := pte.ppn
    newEntry.c := cacheable
    newEntry.u := pte.u
    newEntry.g := pte.g && pte.v
    newEntry.ae_ptw := io.ptw.resp.bits.ae_ptw
    newEntry.ae_final := io.ptw.resp.bits.ae_final
    newEntry.ae_stage2 := io.ptw.resp.bits.ae_final && io.ptw.resp.bits.gpa_is_pte && r_stage2_en
    newEntry.pf := io.ptw.resp.bits.pf
    newEntry.gf := io.ptw.resp.bits.gf
    newEntry.hr := io.ptw.resp.bits.hr
    newEntry.hw := io.ptw.resp.bits.hw
    newEntry.hx := io.ptw.resp.bits.hx
    newEntry.sr := PTE.sr(pte)
    newEntry.sw := PTE.sw(pte)
    newEntry.sx := PTE.sx(pte)
    newEntry.pr := prot_r
    newEntry.pw := prot_w
    newEntry.px := prot_x
    newEntry.ppp := prot_pp
    newEntry.pal := prot_al
    newEntry.paa := prot_aa
    newEntry.eff := prot_eff
    newEntry.fragmented_superpage := io.ptw.resp.bits.fragmented_superpage
    // refill special_entry
    when(special_entry.nonEmpty.B && !io.ptw.resp.bits.homogeneous) {
      special_entry.foreach(tlbEntry => TLBEntry.insert(tlbEntry, r_refill_tag, refill_v, io.ptw.resp.bits.level, newEntry, superpageOnly = false))
    }.elsewhen(io.ptw.resp.bits.level < (pgLevels - 1).U) {
      val waddr = Mux(r_superpage_hit.valid && usingHypervisor.B, r_superpage_hit.bits, r_superpage_repl_addr)
      for ((e, i) <- superpage_entries.zipWithIndex) when(r_superpage_repl_addr === i.U) {
        TLBEntry.insert(e, r_refill_tag, refill_v, io.ptw.resp.bits.level, newEntry, superpageOnly = true)
        when(invalidate_refill) {
          TLBEntry.invalidate(e)
        }
      }
      // refill sectored_hit
    }.otherwise {
      val r_memIdx = if(log2Ceil(cfg.nSets) == 0) 0.U else (r_refill_tag(log2Ceil(cfg.nSectors) + log2Ceil(cfg.nSets) - 1, log2Ceil(cfg.nSectors)))
      val waddr = Mux(r_sectored_hit.valid, r_sectored_hit.bits, r_sectored_repl_addr)
      for ((e, i) <- sectored_entries(r_memIdx).zipWithIndex) when(waddr === i.U) {
        when(!r_sectored_hit.valid) { TLBEntry.invalidate(e) }
        TLBEntry.insert(e, r_refill_tag, refill_v, 0.U, newEntry, superpageOnly = false)
        when(invalidate_refill) { TLBEntry.invalidate(e) }
      }
    }

    r_gpa_valid := io.ptw.resp.bits.gpa.valid
    r_gpa := io.ptw.resp.bits.gpa.bits
    r_gpa_is_pte := io.ptw.resp.bits.gpa_is_pte
  }

  // get all entries data.
  val entries = all_entries.map(tlbEntry => TLBEntry.getData(tlbEntry, vpn))
  val normal_entries = entries.take(ordinary_entries.size)
  // parallel query PPN from [[all_entries]], if VM not enabled return VPN instead
  val ppn = Mux1H(
    hitsVec :+ !vm_enabled,
    allEntries.zip(entries).map { case ((entry, superpage, superpageOnly), data) => TLBEntry.ppn(entry, vpn, data, usingVM, pgLevelBits: Int, superpage, superpageOnly) } :+ vpn(ppnBits - 1, 0)
  )

  val nPhysicalEntries = 1 + special_entry.size
  // generally PTW misaligned load exception.
  val ptw_ae_array = Cat(false.B, VecInit(entries.map(_.ae_ptw)).asUInt)
  val final_ae_array = Cat(false.B, VecInit(entries.map(_.ae_final)).asUInt)
  val ptw_pf_array = Cat(false.B, VecInit(entries.map(_.pf)).asUInt)
  val ptw_gf_array = Cat(false.B, VecInit(entries.map(_.gf)).asUInt)
  val sum = Mux(priv_v, io.ptw.gstatus.sum, io.ptw.status.sum)
  // if in hypervisor/machine mode, cannot read/write user entries.
  // if in superviosr/user mode, "If the SUM bit in the sstatus register is set, supervisor mode software may also access pages with U=1.(from spec)"
  val priv_rw_ok = Mux(!priv_s || sum, VecInit(entries.map(_.u)).asUInt, 0.U) | Mux(priv_s, ~VecInit(entries.map(_.u)).asUInt, 0.U)
  // if in hypervisor/machine mode, other than user pages, all pages are executable.
  // if in superviosr/user mode, only user page can execute.
  val priv_x_ok = Mux(priv_s, ~VecInit(entries.map(_.u)).asUInt, VecInit(entries.map(_.u)).asUInt)
  val stage1_bypass =
    Fill(entries.size, usingHypervisor.B) & (Fill(entries.size, !stage1_en) | VecInit(entries.map(_.ae_stage2)).asUInt)
  val mxr = io.ptw.status.mxr | Mux(priv_v, io.ptw.gstatus.mxr, false.B)
  // "The vsstatus field MXR, which makes execute-only pages readable, only overrides VS-stage page protection.(from spec)"
  val r_array =
    Cat(true.B, (priv_rw_ok & (VecInit(entries.map(_.sr)).asUInt | Mux(mxr, VecInit(entries.map(_.sx)).asUInt, 0.U))) | stage1_bypass)
  val w_array = Cat(true.B, (priv_rw_ok & VecInit(entries.map(_.sw)).asUInt) | stage1_bypass)
  val x_array = Cat(true.B, (priv_x_ok & VecInit(entries.map(_.sx)).asUInt) | stage1_bypass)
  val stage2_bypass = Fill(entries.size, !stage2_en)
  val hr_array =
    Cat(true.B, VecInit(entries.map(_.hr)).asUInt | Mux(io.ptw.status.mxr, VecInit(entries.map(_.hx)).asUInt, 0.U) | stage2_bypass)
  val hw_array = Cat(true.B, VecInit(entries.map(_.hw)).asUInt | stage2_bypass)
  val hx_array = Cat(true.B, VecInit(entries.map(_.hx)).asUInt | stage2_bypass)
  // These array is for each TLB entries.
  // user mode can read: PMA OK, TLB OK, AE OK
  val pr_array = Cat(Fill(nPhysicalEntries, prot_r), VecInit(normal_entries.map(_.pr)).asUInt) & ~(ptw_ae_array | final_ae_array)
  // user mode can write: PMA OK, TLB OK, AE OK
  val pw_array = Cat(Fill(nPhysicalEntries, prot_w), VecInit(normal_entries.map(_.pw)).asUInt) & ~(ptw_ae_array | final_ae_array)
  // user mode can write: PMA OK, TLB OK, AE OK
  val px_array = Cat(Fill(nPhysicalEntries, prot_x), VecInit(normal_entries.map(_.px)).asUInt) & ~(ptw_ae_array | final_ae_array)
  // put effect
  val eff_array = Cat(Fill(nPhysicalEntries, prot_eff), VecInit(normal_entries.map(_.eff)).asUInt)
  // cacheable
  val c_array = Cat(Fill(nPhysicalEntries, cacheable), VecInit(normal_entries.map(_.c)).asUInt)
  // put partial
  val ppp_array = Cat(Fill(nPhysicalEntries, prot_pp), VecInit(normal_entries.map(_.ppp)).asUInt)
  // atomic arithmetic
  val paa_array = Cat(Fill(nPhysicalEntries, prot_aa), VecInit(normal_entries.map(_.paa)).asUInt)
  // atomic logic
  val pal_array = Cat(Fill(nPhysicalEntries, prot_al), VecInit(normal_entries.map(_.pal)).asUInt)
  val ppp_array_if_cached = ppp_array | c_array
  val paa_array_if_cached = paa_array | (if (usingAtomicsInCache) c_array else 0.U)
  val pal_array_if_cached = pal_array | (if (usingAtomicsInCache) c_array else 0.U)
  val prefetchable_array = Cat((cacheable && homogeneous) << (nPhysicalEntries - 1), VecInit(normal_entries.map(_.c)).asUInt)

  // vaddr misaligned: vaddr[1:0]=b00
  val misaligned = (io.req.bits.vaddr & (UIntToOH(io.req.bits.size) - 1.U)).orR
  def badVA(guestPA: Boolean): Bool = {
    val additionalPgLevels = PTBR.additionalPgLevels(if (guestPA) io.ptw.hgatp else satp, pgLevels, minPgLevels)
    val extraBits = if (guestPA) hypervisorExtraAddrBits else 0
    val signed = !guestPA
    val nPgLevelChoices = pgLevels - minPgLevels + 1
    val minVAddrBits = pgIdxBits + minPgLevels * pgLevelBits + extraBits
    VecInit((for (i <- 0 until nPgLevelChoices) yield {
      val mask = ((BigInt(1) << vaddrBitsExtended) - (BigInt(1) << (minVAddrBits + i * pgLevelBits - (if(signed) 1 else 0)))).U
      val maskedVAddr = io.req.bits.vaddr & mask
      additionalPgLevels === i.U && !(maskedVAddr === 0.U || signed.B && maskedVAddr === mask)
    })).asUInt.orR
  }
  val bad_gpa =
    if (!usingHypervisor) false.B
    else vm_enabled && !stage1_en && badVA(true)
  val bad_va =
    if (!usingVM || (minPgLevels == pgLevels && vaddrBits == vaddrBitsExtended)) false.B
    else vm_enabled && stage1_en && badVA(false)

  val cmd_lrsc = usingAtomics.B && isOneOf(io.req.bits.cmd, Seq(M_XLR, M_XSC))
  def isAMOLogical(cmd:    UInt) = isOneOf(cmd, Seq(M_XA_SWAP, M_XA_XOR, M_XA_OR, M_XA_AND))
  val cmd_amo_logical = usingAtomics.B && isAMOLogical(io.req.bits.cmd)
  def isAMOArithmetic(cmd: UInt) = isOneOf(cmd, Seq(M_XA_ADD, M_XA_MIN, M_XA_MAX, M_XA_MINU, M_XA_MAXU))
  val cmd_amo_arithmetic = usingAtomics.B && isAMOArithmetic(io.req.bits.cmd)
  val cmd_put_partial = io.req.bits.cmd === M_PWR
  def isAMO(cmd:           UInt) = isAMOLogical(cmd) || isAMOArithmetic(cmd)
  def isRead(cmd:          UInt) = isOneOf(cmd, Seq(M_XRD, M_HLVX, M_XLR, M_XSC)) || isAMO(cmd)
  val cmd_read = isRead(io.req.bits.cmd)
  val cmd_readx = usingHypervisor.B && io.req.bits.cmd === M_HLVX
  def isWrite(cmd:         UInt) = cmd === M_XWR || cmd === M_PWR || cmd === M_XSC || isAMO(cmd)
  val cmd_write = isWrite(io.req.bits.cmd)
  val cmd_write_perms = cmd_write ||
    isOneOf(io.req.bits.cmd, Seq(M_FLUSH_ALL, M_WOK)) // not a write, but needs write permissions

  val lrscAllowed = Mux((usingDataScratchpad || usingAtomicsOnlyForIO).B, 0.U, c_array)
  val ae_array =
    Mux(misaligned, eff_array, 0.U) |
      Mux(cmd_lrsc, ~lrscAllowed, 0.U)

  // access exception needs SoC information from PMA
  val ae_ld_array = Mux(cmd_read, ae_array | ~pr_array, 0.U)
  val ae_st_array =
    Mux(cmd_write_perms, ae_array | ~pw_array, 0.U) |
      Mux(cmd_put_partial, ~ppp_array_if_cached, 0.U) |
      Mux(cmd_amo_logical, ~pal_array_if_cached, 0.U) |
      Mux(cmd_amo_arithmetic, ~paa_array_if_cached, 0.U)
  val must_alloc_array =
    Mux(cmd_put_partial, ~ppp_array, 0.U) |
      Mux(cmd_amo_logical, ~pal_array, 0.U) |
      Mux(cmd_amo_arithmetic, ~paa_array, 0.U) |
      Mux(cmd_lrsc, ~0.U(pal_array.getWidth.W), 0.U)
  val pf_ld_array =
    Mux(cmd_read, ((~Mux(cmd_readx, x_array, r_array) & ~ptw_ae_array) | ptw_pf_array) & ~ptw_gf_array, 0.U)
  val pf_st_array = Mux(cmd_write_perms, ((~w_array & ~ptw_ae_array) | ptw_pf_array) & ~ptw_gf_array, 0.U)
  val pf_inst_array = ((~x_array & ~ptw_ae_array) | ptw_pf_array) & ~ptw_gf_array
  val gf_ld_array = Mux(priv_v && cmd_read, ~Mux(cmd_readx, hx_array, hr_array) & ~ptw_ae_array, 0.U)
  val gf_st_array = Mux(priv_v && cmd_write_perms, ~hw_array & ~ptw_ae_array, 0.U)
  val gf_inst_array = Mux(priv_v, ~hx_array & ~ptw_ae_array, 0.U)

  val gpa_hits = {
    val need_gpa_mask = if (instruction) gf_inst_array else gf_ld_array | gf_st_array
    val hit_mask = Fill(ordinary_entries.size, r_gpa_valid && r_gpa_vpn === vpn) | Fill(all_entries.size, !vstage1_en)
    hit_mask | ~need_gpa_mask(all_entries.size - 1, 0)
  }

  val tlb_hit_if_not_gpa_miss = real_hits.orR
  val tlb_hit = (real_hits & gpa_hits).orR
  // leads to s_request
  val tlb_miss = vm_enabled && !vsatp_mode_mismatch && !bad_va && !tlb_hit

  val sectored_plru = new SetAssocLRU(cfg.nSets, sectored_entries.head.size, "plru")
  val superpage_plru = new PseudoLRU(superpage_entries.size)
  when(io.req.valid && vm_enabled) {
    // replace
    when(VecInit(sector_hits).asUInt.orR) { sectored_plru.access(memIdx, OHToUInt(sector_hits)) }
    when(VecInit(superpage_hits).asUInt.orR) { superpage_plru.access(OHToUInt(superpage_hits)) }
  }

  // Superpages create the possibility that two entries in the TLB may match.
  // This corresponds to a software bug, but we can't return complete garbage;
  // we must return either the old translation or the new translation.  This
  // isn't compatible with the Mux1H approach.  So, flush the TLB and report
  // a miss on duplicate entries.
  val multipleHits = PopCountAtLeast(real_hits, 2)

  // only pull up req.ready when this is s_ready state.
  io.req.ready := state === s_ready
  // page fault
  io.resp.pf.ld := (bad_va && cmd_read) || (pf_ld_array & hits).orR
  io.resp.pf.st := (bad_va && cmd_write_perms) || (pf_st_array & hits).orR
  io.resp.pf.inst := bad_va || (pf_inst_array & hits).orR
  // guest page fault
  io.resp.gf.ld := (bad_gpa && cmd_read) || (gf_ld_array & hits).orR
  io.resp.gf.st := (bad_gpa && cmd_write_perms) || (gf_st_array & hits).orR
  io.resp.gf.inst := bad_gpa || (gf_inst_array & hits).orR
  // access exception
  io.resp.ae.ld := (ae_ld_array & hits).orR
  io.resp.ae.st := (ae_st_array & hits).orR
  io.resp.ae.inst := (~px_array & hits).orR
  // misaligned
  io.resp.ma.ld := misaligned && cmd_read
  io.resp.ma.st := misaligned && cmd_write
  io.resp.ma.inst := false.B // this is up to the pipeline to figure out
  io.resp.cacheable := (c_array & hits).orR
  io.resp.must_alloc := (must_alloc_array & hits).orR

  // io.resp.prefetchable := (prefetchable_array & hits).orR && edge.manager.managers
  //   .forall(m => !m.supportsAcquireB || m.supportsHint)
  //   .B
  // prefetch range
  io.resp.prefetchable := (prefetchable_array & hits).orR
  io.resp.miss := do_refill || vsatp_mode_mismatch || tlb_miss || multipleHits
  io.resp.paddr := Cat(ppn, io.req.bits.vaddr(pgIdxBits - 1, 0))
  io.resp.gpa_is_pte := vstage1_en && r_gpa_is_pte
  io.resp.gpa := {
    val page = Mux(!vstage1_en, Cat(bad_gpa, vpn), r_gpa >> pgIdxBits)
    val offset = Mux(io.resp.gpa_is_pte, r_gpa(pgIdxBits - 1, 0), io.req.bits.vaddr(pgIdxBits - 1, 0))
    Cat(page, offset)
  }

  io.ptw.req.valid := state === s_request
  io.ptw.req.bits.valid := !io.kill
  io.ptw.req.bits.bits.addr := r_refill_tag
  io.ptw.req.bits.bits.vstage1 := r_vstage1_en
  io.ptw.req.bits.bits.stage2 := r_stage2_en
  io.ptw.req.bits.bits.need_gpa := r_need_gpa

  if (usingVM) {
    when(io.ptw.req.fire && io.ptw.req.bits.valid) {
      r_gpa_valid := false.B
      r_gpa_vpn := r_refill_tag
    }

    val sfence = io.sfence.valid
    // this is [[s_ready]]
    // handle miss/hit at the first cycle.
    // if miss, request PTW(L2TLB).
    when(io.req.fire && tlb_miss) {
      state := s_request
      r_refill_tag := vpn
      r_need_gpa := tlb_hit_if_not_gpa_miss
      r_vstage1_en := vstage1_en
      r_stage2_en := stage2_en
      r_superpage_repl_addr := replacementEntry(superpage_entries, superpage_plru.way)
      r_sectored_repl_addr := replacementEntry(sectored_entries(memIdx), sectored_plru.way(memIdx))
      r_sectored_hit.valid := VecInit(sector_hits).asUInt.orR
      r_sectored_hit.bits := OHToUInt(sector_hits)
      r_superpage_hit.valid := VecInit(superpage_hits).asUInt.orR
      r_superpage_hit.bits := OHToUInt(superpage_hits)
    }
    // Handle SFENCE.VMA when send request to PTW.
    // SFENCE.VMA    io.ptw.req.ready     kill
    //       ?                 ?            1
    //       0                 0            0
    //       0                 1            0 -> s_wait
    //       1                 0            0 -> s_wait_invalidate
    //       1                 0            0 -> s_ready
    when(state === s_request) {
      // SFENCE.VMA will kill TLB entries based on rs1 and rs2. It will take 1 cycle.
      when(sfence) { state := s_ready }
      // here should be io.ptw.req.fire, but AssertProperty(BoolSequence(io.ptw.req.ready === true.B))
      // fire -> s_wait
      when(io.ptw.req.ready) { state := Mux(sfence, s_wait_invalidate, s_wait) }
      // If CPU kills request(frontend.s2_redirect)
      when(io.kill) { state := s_ready }
    }
    // sfence in refill will results in invalidate
    when(state === s_wait && sfence) {
      state := s_wait_invalidate
    }
    // after CPU acquire response, go back to s_ready.
    when(io.ptw.resp.valid) {
      state := s_ready
    }

    // SFENCE processing logic.
    when(sfence) {
      AssertProperty(BoolSequence(!io.sfence.bits.rs1 || (io.sfence.bits.addr >> pgIdxBits) === vpn))
      val hv = usingHypervisor.B && io.sfence.bits.hv
      val hg = usingHypervisor.B && io.sfence.bits.hg
      sectored_entries.flatten.foreach{ e =>
        when(!hg && io.sfence.bits.rs1) { TLBEntry.invalidateVPN(e, vpn, hv, usingVM, pgLevelBits, hypervisorExtraAddrBits, superpage = false, superpageOnly = false) }
          .elsewhen(!hg && io.sfence.bits.rs2) { TLBEntry.invalidateNonGlobal(e, hv) }
          .otherwise { TLBEntry.invalidateNonGlobal(e, hv || hg) }
      }
      superpage_entries.foreach { e =>
        when(!hg && io.sfence.bits.rs1) { TLBEntry.invalidateVPN(e, vpn, hv, usingVM, pgLevelBits, hypervisorExtraAddrBits, superpage = true, superpageOnly = true) }
          .elsewhen(!hg && io.sfence.bits.rs2) { TLBEntry.invalidateNonGlobal(e, hv) }
          .otherwise { TLBEntry.invalidateNonGlobal(e, hv || hg) }
      }
      special_entry.foreach { e =>
        when(!hg && io.sfence.bits.rs1) { TLBEntry.invalidateVPN(e, vpn, hv, usingVM, pgLevelBits, hypervisorExtraAddrBits, superpage = true, superpageOnly = false) }
          .elsewhen(!hg && io.sfence.bits.rs2) { TLBEntry.invalidateNonGlobal(e, hv) }
          .otherwise { TLBEntry.invalidateNonGlobal(e, hv || hg) }
      }
    }
    when(io.req.fire && vsatp_mode_mismatch) {
      all_real_entries.foreach(tlbEntry => TLBEntry.invalidate(tlbEntry, true.B))
      v_entries_use_stage1 := vstage1_en
    }
    when(multipleHits || io.reset.asBool) {
      all_real_entries.foreach(tlbEntry => TLBEntry.invalidate(tlbEntry))
    }
  }

  /** Decides which entry to be replaced
    *
    * If there is a invalid entry, replace it with priorityencoder;
    * if not, replace the alt entry
    *
    * @return mask for TLBEntry replacement
    */
  def replacementEntry(set: Seq[TLBEntry], alt: UInt) = {
    val valids = VecInit(set.map(_.valid.asUInt.orR)).asUInt
    Mux(valids.andR, alt, PriorityEncoder(~valids))
  }
}
