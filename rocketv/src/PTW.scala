// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.instantiable
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util.circt.ClockGate
import chisel3.util.{Arbiter, Cat, Enum, Mux1H, OHToUInt, PopCount, PriorityEncoder, PriorityEncoderOH, RegEnable, SRAM, SRAMInterface, UIntToOH, Valid, is, isPow2, log2Ceil, switch}
import chisel3.ltl._
import chisel3.ltl.Sequence._

object PTWParameter {
  implicit def rwP: upickle.default.ReadWriter[PTWParameter] = upickle.default.macroRW[PTWParameter]
}

case class PTWParameter(useAsyncReset: Boolean,
                        hasClockGate: Boolean,
                        usingVM: Boolean,
                        usingHypervisor: Boolean,
                        xLen: Int,
                        fLen: Int,
                        paddrBits: Int,
                        asidBits: Int,
                        pgLevels: Int,
                        nPTECacheEntries: Int,
                        nL2TLBWays: Int,
                        nL2TLBEntries: Int,
                        nPMPs: Int,
                       ) extends SerializableModuleParameter {
  def pmpGranularity: Int = if (usingHypervisor) 4096 else 4
  // TODO: configurable
  def cacheBlockBytes = 64
  def lgCacheBlockBytes = log2Ceil(cacheBlockBytes)
  def blockOffBits = lgCacheBlockBytes
  def nL2TLBSets = nL2TLBEntries / nL2TLBWays
  def idxBits: Int = if (nL2TLBSets == 0) 0 else log2Ceil(nL2TLBSets)
  def untagBits: Int = blockOffBits + idxBits
  def coreMaxAddrBits: Int = paddrBits max vaddrBitsExtended
  def maxHypervisorExtraAddrBits: Int = 2
  def pgIdxBits: Int = 12
  def maxSVAddrBits: Int = pgIdxBits + pgLevels * pgLevelBits
  def dcacheReqTagBits: Int = 6
  def separateUncachedResp: Boolean = false

  // @todo make it true in the future.
  def usingDTIM: Boolean = false
  def dcacheArbPorts: Int = 1 + (if (usingVM) 1 else 0) + (if (usingDTIM) 1 else 0)
  def coreDataBytes: Int = xLen max fLen
  def vpnBitsExtended: Int = vpnBits + (if (vaddrBits < xLen) 1 + ( if(usingHypervisor) 1 else 0) else 0)
  def vaddrBitsExtended: Int = vpnBitsExtended + pgIdxBits
  def vpnBits: Int = vaddrBits - pgIdxBits
  def ppnBits: Int = paddrBits - pgIdxBits
  def hypervisorExtraAddrBits: Int = {
    if (usingHypervisor) maxHypervisorExtraAddrBits
    else 0
  }
  def maxHVAddrBits: Int = maxSVAddrBits + hypervisorExtraAddrBits
  def vaddrBits: Int =
    if (usingVM) {
      val v = maxHVAddrBits
      require(v == xLen || xLen > v && v > paddrBits)
      v
    } else {
      // since virtual addresses sign-extend but physical addresses
      // zero-extend, make room for a zero sign bit for physical addresses
      (paddrBits + 1) min xLen
    }
  def pgLevelBits: Int = 10 - log2Ceil(xLen / 32)
  def minPgLevels: Int = {
    val res = xLen match { case 32 => 2; case 64 => 3 }
    require(pgLevels >= res)
    res
  }
  def maxPAddrBits: Int = xLen match {
    case 32 => 34
    case 64 => 56
  }
  // I$ + D$
  def nPTWPorts: Int = 2
}

class PTWInterface(parameter: PTWParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())

  /** to n TLB */
  val requestor = Flipped(
    Vec(
      parameter.nPTWPorts,
      new TLBPTWIO(
        parameter.nPMPs,
        parameter.vpnBits,
        parameter.paddrBits,
        parameter.vaddrBits,
        parameter.pgLevels,
        parameter.xLen,
        parameter.maxPAddrBits,
        parameter.pgIdxBits
      )
    )
  )

  /** to HellaCache */
  val mem = new HellaCacheIO(
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

  /** to Core
    *
    * contains CSRs info and performance statistics
    */
  val dpath = new DatapathPTWIO(
    parameter.xLen,
    parameter.maxPAddrBits,
    parameter.pgIdxBits,
    parameter.vaddrBits,
    parameter.asidBits,
    parameter.nPMPs,
    parameter.paddrBits
  )
}

/** PTW contains L2TLB, and performs page table walk for high level TLB, and cache queries from L1 TLBs(I$, D$, RoCC)
  *
  * It performs hierarchy page table query to mem for the desired leaf PTE and cache them in l2tlb.
  * Besides leaf PTEs, it also caches non-leaf PTEs in pte_cache to accerlerate the process.
  *
  * ==Structure==
  *  - l2tlb : for leaf PTEs
  *   - set-associative (configurable with [[CoreParams.nL2TLBEntries]]and [[CoreParams.nL2TLBWays]]))
  *   - PLRU
  *  - pte_cache: for non-leaf PTEs
  *   - set-associative
  *   - LRU
  *  - s2_pte_cache: for non-leaf PTEs in 2-stage translation
  *   - set-associative
  *   - PLRU
  *
  * l2tlb Pipeline: 3 stage
  * {{{
  * stage 0 : read
  * stage 1 : decode
  * stage 2 : hit check
  * }}}
  * ==State Machine==
  * s_ready: ready to reveive request from TLB
  * s_req: request mem; pte_cache hit judge
  * s_wait1: deal with l2tlb error
  * s_wait2: final hit judge
  * s_wait3: receive mem response
  * s_fragment_superpage: for superpage PTE
  *
  * @note l2tlb hit happens in s_req or s_wait1
  * @see RV-priv spec 4.3-4.6 for Virtual-Memory System
  * @see RV-priv spec 8.5 for Two-Stage Address Translation
  * @todo details in two-stage translation
  */
@instantiable
class PTW(val parameter: PTWParameter)
    extends FixedIORawModule(new PTWInterface(parameter))
    with SerializableModule[PTWParameter]
      with ImplicitClock
      with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val vpnBits:                    Int = parameter.vpnBits
  val ppnBits:                    Int = parameter.ppnBits
  val vaddrBits:                  Int = parameter.vaddrBits
  val paddrBits:                  Int = parameter.paddrBits
  val n:                          Int = parameter.nPTWPorts
  val pgLevels:                   Int = parameter.pgLevels
  val pgLevelBits:                Int = parameter.pgLevelBits
  val minPgLevels:                Int = parameter.minPgLevels
  val hypervisorExtraAddrBits:    Int = parameter.hypervisorExtraAddrBits
  val usingHypervisor:            Boolean = parameter.usingHypervisor
  val xLen:                       Int = parameter.xLen
  val maxPAddrBits:               Int = parameter.maxPAddrBits
  val pgIdxBits:                  Int = parameter.pgIdxBits
  val maxSVAddrBits:                  Int = parameter.maxSVAddrBits
  val pmpGranularity:                  Int = parameter.pmpGranularity
  val usingVM:                    Boolean = parameter.usingVM
  val hasClockGate:               Boolean = parameter.hasClockGate
  val maxHypervisorExtraAddrBits: Int = parameter.maxHypervisorExtraAddrBits
  def M_XRD = "b00000".U

  object coreParams {
    val nPTECacheEntries: Int = parameter.nPTECacheEntries
    val nL2TLBWays:       Int = parameter.nL2TLBWays
    val nL2TLBEntries:       Int = parameter.nL2TLBEntries
  }

  def OptimizationBarrier[T <: Data](in: T): T = {
    val barrier = Module(new Module {
      val io = IO(new Bundle {
        val x = Input(chiselTypeOf(in))
        val y = Output(chiselTypeOf(in))
      })
      io.y := io.x
      override def desiredName = "OptimizationBarrier"
    })
    barrier.io.x := in
    barrier.io.y
  }

  def additionalPgLevels(ptbr: PTBR) = ptbr.mode(log2Ceil(pgLevels - minPgLevels + 1) - 1, 0)

  def padTo(x: UInt, n: Int): UInt = {
    require(x.getWidth <= n)
    if (x.getWidth == n) x
    else Cat(0.U((n - x.getWidth).W), x)
  }


  class PMPHomogeneityChecker(pmps: Seq[PMP]) {
    def apply(addr: UInt, pgLevel: UInt): Bool = {
      pmps
        .foldLeft((true.B, 0.U.asTypeOf(chiselTypeOf(pmps.head)))) {
          case ((h, prev), pmp) =>
            (h && PMP.homogeneous(pmp, addr, pgLevel, prev, paddrBits, pmpGranularity, pgLevels, pgIdxBits, pgLevelBits), pmp)
        }
        ._1
    }
  }

  // compatiblity mode
  object Split {
    def apply(x: UInt, n0: Int) = {
      val w = x.getWidth
      (x(w-1,n0), x(n0-1,0))
    }
    def apply(x: UInt, n1: Int, n0: Int) = {
      val w = x.getWidth
      (x(w-1,n1), x(n1-1,n0), x(n0-1,0))
    }
    def apply(x: UInt, n2: Int, n1: Int, n0: Int) = {
      val w = x.getWidth
      (x(w-1,n2), x(n2-1,n1), x(n1-1,n0), x(n0-1,0))
    }
  }

  val s_ready :: s_req :: s_wait1 :: s_dummy1 :: s_wait2 :: s_wait3 :: s_dummy2 :: s_fragment_superpage :: Nil = Enum(8)
  val state = RegInit(s_ready)
  val l2_refill_wire = Wire(Bool())

  /** Arbiter to arbite request from n TLB */
  val arb = Module(new Arbiter(Valid(new PTWReq(vpnBits)), n))
  // use TLB req as arbitor's input
  arb.io.in <> io.requestor.map(_.req)
  // receive req only when s_ready and not in refill
  arb.io.out.ready := (state === s_ready) && !l2_refill_wire

  val resp_valid = RegNext(VecInit(Seq.fill(io.requestor.size)(false.B)))

  //  val clock_en =
  //    state =/= s_ready || l2_refill_wire || arb.io.out.valid || io.dpath.sfence.valid || io.dpath.customCSRs.disableDCacheClockGate
  val clock_en = state =/= s_ready || l2_refill_wire || arb.io.out.valid || io.dpath.sfence.valid
  io.dpath.clock_enabled := usingVM.B && clock_en
  val gated_clock: Clock =
    if (!usingVM || !hasClockGate) io.clock
    else ClockGate(io.clock, clock_en)
  withClock(gated_clock) { // entering gated-clock domain

    val invalidated = Reg(Bool())

    /** current PTE level
      * {{{
      * 0 <= count <= pgLevel-1
      * count = pgLevel - 1 : leaf PTE
      * count < pgLevel - 1 : non-leaf PTE
      * }}}
      */
    val count = Reg(UInt(log2Ceil(pgLevels).W))
    val resp_ae_ptw = Reg(Bool())
    val resp_ae_final = Reg(Bool())
    val resp_pf = Reg(Bool())
    val resp_gf = Reg(Bool())
    val resp_hr = Reg(Bool())
    val resp_hw = Reg(Bool())
    val resp_hx = Reg(Bool())
    val resp_fragmented_superpage = Reg(Bool())

    /** tlb request */
    val r_req = Reg(new PTWReq(vpnBits))

    /** current selected way in arbitor */
    val r_req_dest = Reg(Bits())
    // to respond to L1TLB : l2_hit
    // to construct mem.req.addr
    val r_pte = Reg(new PTE)
    val r_hgatp = Reg(new PTBR(xLen, maxPAddrBits, pgIdxBits))
    // 2-stage pageLevel
    val aux_count = Reg(UInt(log2Ceil(pgLevels).W))

    /** pte for 2-stage translation */
    val aux_pte = Reg(new PTE)
    val aux_ppn_hi = Option.when(pgLevels > 4 && r_req.addr.getWidth > aux_pte.ppn.getWidth)(
      Reg(UInt((r_req.addr.getWidth - aux_pte.ppn.getWidth).W))
    )
    val gpa_pgoff = Reg(UInt(pgIdxBits.W)) // only valid in resp_gf case
    val stage2 = Reg(Bool())
    val stage2_final = Reg(Bool())

    val satp = Mux(arb.io.out.bits.bits.vstage1, io.dpath.vsatp, io.dpath.ptbr)

    val r_hgatp_initial_count = pgLevels.U - minPgLevels.U - additionalPgLevels(r_hgatp)

    /** 2-stage translation both enable */
    val do_both_stages = r_req.vstage1 && r_req.stage2
    val max_count = count.max(aux_count)
    val vpn = Mux(r_req.vstage1 && stage2, aux_pte.ppn, r_req.addr)

    val mem_resp_valid = RegNext(io.mem.resp.valid)
    val mem_resp_data = RegNext(io.mem.resp.bits.data)
    io.mem.uncached_resp.map { resp =>
      AssertProperty(BoolSequence(!(resp.valid && io.mem.resp.valid)), clock = gated_clock)
      resp.ready := true.B
      when(resp.valid) {
        mem_resp_valid := true.B
        mem_resp_data := resp.bits.data
      }
    }
    // construct pte from mem.resp
    val (pte: PTE, invalid_paddr: Bool) = {
      val tmp = mem_resp_data.asTypeOf(new PTE())
      val res = WireDefault(tmp)
      res.ppn := Mux(do_both_stages && !stage2, tmp.ppn(vpnBits.min(tmp.ppn.getWidth) - 1, 0), tmp.ppn(ppnBits - 1, 0))
      when(tmp.r || tmp.w || tmp.x) {
        // for superpage mappings, make sure PPN LSBs are zero
        for (i <- 0 until pgLevels - 1)
          when(
            count <= i.U && tmp.ppn((pgLevels - 1 - i) * pgLevelBits - 1, (pgLevels - 2 - i) * pgLevelBits) =/= 0.U
          ) { res.v := false.B }
      }
      (res, Mux(do_both_stages && !stage2, (tmp.ppn >> vpnBits) =/= 0.U, (tmp.ppn >> ppnBits) =/= 0.U))
    }
    // find non-leaf PTE, need traverse
    def table(pte: PTE) =
      pte.v && !pte.r && !pte.w && !pte.x && !pte.d && !pte.a && !pte.u && pte.reserved_for_future === 0.U
    val traverse = table(pte) && !invalid_paddr && count < (pgLevels - 1).U

    /** address send to mem for enquerry */
    val pte_addr =
      if (!usingVM) 0.U
      else {
        val vpn_idxs = VecInit((0 until pgLevels).map { i =>
          val width = pgLevelBits + (if (i <= pgLevels - minPgLevels) hypervisorExtraAddrBits else 0)
          (vpn >> (pgLevels - i - 1) * pgLevelBits)(width - 1, 0)
        })
        val mask = Mux(
          stage2 && count === r_hgatp_initial_count,
          ((1 << (hypervisorExtraAddrBits + pgLevelBits)) - 1).U,
          ((1 << pgLevelBits) - 1).U
        )
        val vpn_idx = vpn_idxs(count) & mask
        val raw_pte_addr: UInt = ((r_pte.ppn << pgLevelBits) | vpn_idx) << log2Ceil(xLen / 8)
        val size = if (usingHypervisor) vaddrBits else paddrBits
        //use r_pte.ppn as page table base address
        //use vpn slice as offset
        raw_pte_addr.apply(size.min(raw_pte_addr.getWidth) - 1, 0)
      }

    /** pte_cache input addr */
    val pte_cache_addr =
      if (!usingHypervisor) pte_addr
      else {
        val vpn_idxs = VecInit((0 until pgLevels - 1).map { i =>
          val ext_aux_pte_ppn = aux_ppn_hi match {
            case None     => aux_pte.ppn
            case Some(hi) => Cat(hi, aux_pte.ppn)
          }
          (ext_aux_pte_ppn >> (pgLevels - i - 1) * pgLevelBits)(pgLevelBits - 1, 0)
        })
        val vpn_idx = vpn_idxs(count)
        val raw_pte_cache_addr = Cat(r_pte.ppn, vpn_idx) << log2Ceil(xLen / 8)
        raw_pte_cache_addr(vaddrBits.min(raw_pte_cache_addr.getWidth) - 1, 0)
      }

    /** stage2_pte_cache input addr */
    val stage2_pte_cache_addr =
      if (!usingHypervisor) 0.U
      else {
        val vpn_idxs = VecInit((0 until pgLevels - 1).map { i =>
          (r_req.addr >> (pgLevels - i - 1) * pgLevelBits)(pgLevelBits - 1, 0)
        })
        val vpn_idx = vpn_idxs(aux_count)
        val raw_s2_pte_cache_addr = Cat(aux_pte.ppn, vpn_idx) << log2Ceil(xLen / 8)
        raw_s2_pte_cache_addr(vaddrBits.min(raw_s2_pte_cache_addr.getWidth) - 1, 0)
      }

    def makeFragmentedSuperpagePPN(ppn: UInt): Seq[UInt] = {
      def padTo(x: UInt, n: Int): UInt = {
        require(x.getWidth <= n)
        if (x.getWidth == n) x
        else Cat(0.U((n - x.getWidth).W), x)
      }
      (pgLevels - 1 until 0 by -1).map(i =>
        Cat(ppn >> (pgLevelBits * i), padTo(r_req.addr(((pgLevelBits * i).min(vpnBits)) - 1, 0), (pgLevelBits * i)))
      )
    }

    /** PTECache caches non-leaf PTE
      * @param s2 true: 2-stage address translation
      */
    def makePTECache(s2: Boolean): (Bool, UInt) = if (coreParams.nPTECacheEntries == 0) {
      (false.B, 0.U)
    } else {
      val plru = new PseudoLRU(coreParams.nPTECacheEntries)
      val valid = RegInit(0.U(coreParams.nPTECacheEntries.W))
      val tags = Reg(Vec(coreParams.nPTECacheEntries, UInt((if (usingHypervisor) 1 + vaddrBits else paddrBits).W)))
      // not include full pte, only ppn
      val data = Reg(Vec(coreParams.nPTECacheEntries, UInt((if (usingHypervisor && s2) vpnBits else ppnBits).W)))
      val can_hit =
        if (s2)
          count === r_hgatp_initial_count && aux_count < (pgLevels - 1).U && r_req.vstage1 && stage2 && !stage2_final
        else count < (pgLevels - 1).U && Mux(r_req.vstage1, stage2, !r_req.stage2)
      val can_refill =
        if (s2) do_both_stages && !stage2 && !stage2_final
        else can_hit

      val tag =
        if (s2) Cat(true.B, padTo(stage2_pte_cache_addr, vaddrBits))
        else Cat(r_req.vstage1, padTo(pte_cache_addr, if (usingHypervisor) vaddrBits else paddrBits))

      val hits = VecInit(tags.map(_ === tag)).asUInt & valid
      val hit = hits.orR && can_hit
      // refill with mem response
      when(mem_resp_valid && traverse && can_refill && !hits.orR && !invalidated) {
        val r = Mux(valid.andR, plru.way, PriorityEncoder(~valid))
        valid := valid | UIntToOH(r)
        tags(r) := tag
        data(r) := pte.ppn
        plru.access(r)
      }
      // replace
      when(hit && state === s_req) { plru.access(OHToUInt(hits)) }
      when(io.dpath.sfence.valid && (!io.dpath.sfence.bits.rs1 || usingHypervisor.B && io.dpath.sfence.bits.hg)) {
        valid := 0.U
      }

      val lcount = if (s2) aux_count else count
      // for (i <- 0 until pgLevels - 1) {
      //   ccover(hit && state === s_req && lcount === i.U, s"PTE_CACHE_HIT_L$i", s"PTE cache hit, level $i")
      // }

      (hit, Mux1H(hits, data))
    }
    // generate pte_cache
    val (pte_cache_hit, pte_cache_data) = makePTECache(false)
    // generate pte_cache with 2-stage translation
    val (stage2_pte_cache_hit, stage2_pte_cache_data) = makePTECache(true)
    // pte_cache hit or 2-stage pte_cache hit
    val pte_hit = RegNext(false.B)
    io.dpath.perf.pte_miss := false.B
    io.dpath.perf.pte_hit := pte_hit && (state === s_req) && !io.dpath.perf.l2hit
    assert(
      !(io.dpath.perf.l2hit && (io.dpath.perf.pte_miss || io.dpath.perf.pte_hit)),
      "PTE Cache Hit/Miss Performance Monitor Events are lower priority than L2TLB Hit event"
    )
    // l2_refill happens when find the leaf pte
    val l2_refill = RegNext(false.B)
    l2_refill_wire := l2_refill
    io.dpath.perf.l2miss := false.B
    io.dpath.perf.l2hit := false.B
    // l2tlb
    val (l2_hit, l2_error, l2_pte, l2_tlb_ram) =
      if (coreParams.nL2TLBEntries == 0) (false.B, false.B, WireDefault(0.U.asTypeOf(new PTE)), None)
      else {
        val code = new ParityCode
        require(isPow2(coreParams.nL2TLBEntries))
        require(isPow2(coreParams.nL2TLBWays))
        require(coreParams.nL2TLBEntries >= coreParams.nL2TLBWays)
        val nL2TLBSets = coreParams.nL2TLBEntries / coreParams.nL2TLBWays
        require(isPow2(nL2TLBSets))
        val idxBits = log2Ceil(nL2TLBSets)

        val l2_plru = new SetAssocLRU(nL2TLBSets, coreParams.nL2TLBWays, "plru")
        val ram: SRAMInterface[Vec[UInt]] = SRAM.masked(
          size = nL2TLBSets,
          tpe = Vec(coreParams.nL2TLBWays, UInt(code.width(new L2TLBEntry(nL2TLBSets, ppnBits, maxSVAddrBits, pgIdxBits, usingHypervisor).getWidth).W)),
          numReadPorts = 0,
          numWritePorts = 0,
          numReadwritePorts = 1
        )

        val g = Reg(Vec(coreParams.nL2TLBWays, UInt(nL2TLBSets.W)))
        val valid = RegInit(VecInit(Seq.fill(coreParams.nL2TLBWays)(0.U(nL2TLBSets.W))))
        // use r_req to construct tag
        val (r_tag, r_idx) = Split(Cat(r_req.vstage1, r_req.addr(maxSVAddrBits - pgIdxBits - 1, 0)), idxBits)

        /** the valid vec for the selected set(including n ways) */
        val r_valid_vec = VecInit(valid.map(_(r_idx))).asUInt
        val r_valid_vec_q = Reg(UInt(coreParams.nL2TLBWays.W))
        val r_l2_plru_way = Reg(UInt(log2Ceil(coreParams.nL2TLBWays.max(1)).W))
        r_valid_vec_q := r_valid_vec
        // replacement way
        r_l2_plru_way := (if (coreParams.nL2TLBWays > 1) l2_plru.way(r_idx) else 0.U)
        // refill with r_pte(leaf pte)
        val entry = Wire(new L2TLBEntry(nL2TLBSets, ppnBits, maxSVAddrBits, pgIdxBits, usingHypervisor))
        entry.ppn := r_pte.ppn
        entry.d := r_pte.d
        entry.a := r_pte.a
        entry.u := r_pte.u
        entry.x := r_pte.x
        entry.w := r_pte.w
        entry.r := r_pte.r
        entry.tag := r_tag
        // if all the way are valid, use plru to select one way to be replaced,
        // otherwise use PriorityEncoderOH to select one
        val wmask =
          if (coreParams.nL2TLBWays > 1)
            Mux(r_valid_vec_q.andR, UIntToOH(r_l2_plru_way, coreParams.nL2TLBWays), PriorityEncoderOH(~r_valid_vec_q))
          else 1.U(1.W)

        val mask = UIntToOH(r_idx)
        for (way <- 0 until coreParams.nL2TLBWays) {
          when(wmask(way)) {
            valid(way) := valid(way) | mask
            g(way) := Mux(r_pte.g, g(way) | mask, g(way) & ~mask)
          }
        }
        // sfence happens
        when(io.dpath.sfence.valid) {
          val hg = usingHypervisor.B && io.dpath.sfence.bits.hg
          for (way <- 0 until coreParams.nL2TLBWays) {
            valid(way) :=
              Mux(
                !hg && io.dpath.sfence.bits.rs1,
                valid(way) & ~UIntToOH(io.dpath.sfence.bits.addr(idxBits + pgIdxBits - 1, pgIdxBits)),
                Mux(!hg && io.dpath.sfence.bits.rs2, valid(way) & g(way), 0.U)
              )
          }
        }

        val s0_valid = !l2_refill && arb.io.out.fire
        val s0_suitable = arb.io.out.bits.bits.vstage1 === arb.io.out.bits.bits.stage2 && !arb.io.out.bits.bits.need_gpa
        val s1_valid = RegNext(s0_valid && s0_suitable && arb.io.out.bits.valid)
        val s2_valid = RegNext(s1_valid)
        // read from tlb idx
        val s1_rdata = ram.readwritePorts.head.readData
        val s2_rdata = s1_rdata.map(s1_rdway => code.decode(RegEnable(s1_rdway, s1_valid)))
        val s2_valid_vec = RegEnable(r_valid_vec, s1_valid)
        val s2_g_vec = RegEnable(VecInit(g.map(_(r_idx))), s1_valid)
        val s2_error = VecInit((0 until coreParams.nL2TLBWays).map(way => s2_valid_vec(way) && s2_rdata(way).error)).asUInt.orR
        when(s2_valid && s2_error) { valid.foreach { _ := 0.U } }
        // ram connect
        ram.readwritePorts.foreach { ramPort =>
          ramPort.enable := (l2_refill && !invalidated) || s0_valid
          ramPort.isWrite := (l2_refill && !invalidated)
          ramPort.address := Mux(l2_refill && !invalidated, r_idx, arb.io.out.bits.bits.addr(idxBits - 1, 0))
          ramPort.writeData := VecInit(Seq.fill(coreParams.nL2TLBWays)(code.encode(entry.asUInt)))
          ramPort.mask.foreach(_ := VecInit(wmask.asBools))
        }
        // decode
        val s2_entry_vec = s2_rdata.map(_.uncorrected.asTypeOf(new L2TLBEntry(nL2TLBSets, ppnBits, maxSVAddrBits, pgIdxBits, usingHypervisor)))
        val s2_hit_vec =
          (0 until coreParams.nL2TLBWays).map(way => s2_valid_vec(way) && (r_tag === s2_entry_vec(way).tag))
        val s2_hit = s2_valid && VecInit(s2_hit_vec).asUInt.orR
        io.dpath.perf.l2miss := s2_valid && !(VecInit(s2_hit_vec).asUInt.orR)
        io.dpath.perf.l2hit := s2_hit
        when(s2_hit) {
          l2_plru.access(r_idx, OHToUInt(s2_hit_vec))
          AssertProperty(BoolSequence((PopCount(s2_hit_vec) === 1.U) || s2_error), clock = gated_clock)
        }

        val s2_pte = Wire(new PTE)
        val s2_hit_entry = Mux1H(s2_hit_vec, s2_entry_vec)
        s2_pte.ppn := s2_hit_entry.ppn
        s2_pte.d := s2_hit_entry.d
        s2_pte.a := s2_hit_entry.a
        s2_pte.g := Mux1H(s2_hit_vec, s2_g_vec)
        s2_pte.u := s2_hit_entry.u
        s2_pte.x := s2_hit_entry.x
        s2_pte.w := s2_hit_entry.w
        s2_pte.r := s2_hit_entry.r
        s2_pte.v := true.B
        s2_pte.reserved_for_future := 0.U
        s2_pte.reserved_for_software := 0.U

//        for (way <- 0 until coreParams.nL2TLBWays) {
//          ccover(s2_hit && s2_hit_vec(way), s"L2_TLB_HIT_WAY$way", s"L2 TLB hit way$way")
//        }

        (s2_hit, s2_error, s2_pte, Some(ram))
      }

    // if SFENCE occurs during walk, don't refill PTE cache or L2 TLB until next walk
    invalidated := io.dpath.sfence.valid || (invalidated && state =/= s_ready)
    // mem request
    io.mem.keep_clock_enabled := false.B

    io.mem.req.valid := state === s_req || state === s_dummy1
    io.mem.req.bits.phys := true.B
    io.mem.req.bits.cmd := M_XRD
    io.mem.req.bits.size := log2Ceil(xLen / 8).U
    io.mem.req.bits.signed := false.B
    io.mem.req.bits.addr := pte_addr
    io.mem.req.bits.idx.foreach(_ := pte_addr)
    io.mem.req.bits.dprv := PRV.S.U // PTW accesses are S-mode by definition
    io.mem.req.bits.dv := do_both_stages && !stage2
    io.mem.req.bits.tag := DontCare
    io.mem.req.bits.no_alloc := DontCare
    io.mem.req.bits.no_xcpt := DontCare
    io.mem.req.bits.data := DontCare
    io.mem.req.bits.mask := DontCare

    io.mem.s1_kill := l2_hit || state =/= s_wait1
    io.mem.s1_data := DontCare
    io.mem.s2_kill := false.B

    val pageGranularityPMPs = pmpGranularity >= (1 << pgIdxBits)
    require(!usingHypervisor || pageGranularityPMPs, s"hypervisor requires pmpGranularity >= ${1 << pgIdxBits}")

    val pmaPgLevelHomogeneous: Seq[Bool] = (0 until pgLevels).map { i =>
      val pgSize = BigInt(1) << (pgIdxBits + ((pgLevels - 1 - i) * pgLevelBits))
      if (pageGranularityPMPs && i == pgLevels - 1) {
        require(
//          TLBPageLookup.homogeneous(edge.manager.managers, pgSize),
          true,
          s"All memory regions must be $pgSize-byte aligned"
        )
        true.B
      } else {
//        TLBPageLookup(edge.manager.managers, xLen, p(CacheBlockBytes), pgSize)(r_pte.ppn << pgIdxBits).homogeneous
        true.B
      }
    }
    val pmaHomogeneous = VecInit(pmaPgLevelHomogeneous)(count)
    val pmpHomogeneous = new PMPHomogeneityChecker(io.dpath.pmp).apply(r_pte.ppn << pgIdxBits, count)
    val homogeneous = pmaHomogeneous && pmpHomogeneous
    // response to tlb
    for (i <- 0 until io.requestor.size) {
      io.requestor(i).resp.valid := resp_valid(i)
      io.requestor(i).resp.bits.ae_ptw := resp_ae_ptw
      io.requestor(i).resp.bits.ae_final := resp_ae_final
      io.requestor(i).resp.bits.pf := resp_pf
      io.requestor(i).resp.bits.gf := resp_gf
      io.requestor(i).resp.bits.hr := resp_hr
      io.requestor(i).resp.bits.hw := resp_hw
      io.requestor(i).resp.bits.hx := resp_hx
      io.requestor(i).resp.bits.pte := r_pte
      io.requestor(i).resp.bits.level := max_count
      io.requestor(i).resp.bits.homogeneous := homogeneous || pageGranularityPMPs.B
      io.requestor(i).resp.bits.fragmented_superpage := resp_fragmented_superpage && pageGranularityPMPs.B
      io.requestor(i).resp.bits.gpa.valid := r_req.need_gpa
      io.requestor(i).resp.bits.gpa.bits :=
        Cat(
          Mux(
            !stage2_final || !r_req.vstage1 || aux_count === (pgLevels - 1).U,
            aux_pte.ppn,
            VecInit(makeFragmentedSuperpagePPN(aux_pte.ppn))(aux_count)
          ),
          gpa_pgoff
        )
      io.requestor(i).resp.bits.gpa_is_pte := !stage2_final
      io.requestor(i).ptbr := io.dpath.ptbr
      io.requestor(i).hgatp := io.dpath.hgatp
      io.requestor(i).vsatp := io.dpath.vsatp
//      io.requestor(i).customCSRs <> io.dpath.customCSRs
      io.requestor(i).status := io.dpath.status
      io.requestor(i).hstatus := io.dpath.hstatus
      io.requestor(i).gstatus := io.dpath.gstatus
      io.requestor(i).pmp := io.dpath.pmp
    }

    // control state machine
    val next_state = WireDefault(state)
    state := OptimizationBarrier(next_state)
    val do_switch = WireDefault(false.B)

    switch(state) {
      is(s_ready) {
        when(arb.io.out.fire) {
          val satp_initial_count = pgLevels.U - minPgLevels.U - additionalPgLevels(satp)
          val vsatp_initial_count = pgLevels.U - minPgLevels.U - additionalPgLevels(io.dpath.vsatp)
          val hgatp_initial_count = pgLevels.U - minPgLevels.U - additionalPgLevels(io.dpath.hgatp)
          val aux_ppn = Mux(arb.io.out.bits.bits.vstage1, io.dpath.vsatp.ppn, arb.io.out.bits.bits.addr)

          r_req := arb.io.out.bits.bits
          r_req_dest := arb.io.chosen
          next_state := Mux(arb.io.out.bits.valid, s_req, s_ready)
          stage2 := arb.io.out.bits.bits.stage2
          stage2_final := arb.io.out.bits.bits.stage2 && !arb.io.out.bits.bits.vstage1
          count := Mux(arb.io.out.bits.bits.stage2, hgatp_initial_count, satp_initial_count)
          aux_count := Mux(arb.io.out.bits.bits.vstage1, vsatp_initial_count, 0.U)
          aux_pte.ppn := aux_ppn
          aux_ppn_hi.foreach { _ := aux_ppn >> aux_pte.ppn.getWidth }
          aux_pte.reserved_for_future := 0.U
          resp_ae_ptw := false.B
          resp_ae_final := false.B
          resp_pf := false.B
          resp_gf := false.B
          resp_hr := true.B
          resp_hw := true.B
          resp_hx := true.B
          resp_fragmented_superpage := false.B
          r_hgatp := io.dpath.hgatp

          AssertProperty(BoolSequence(!arb.io.out.bits.bits.need_gpa || arb.io.out.bits.bits.stage2), clock = gated_clock)
        }
      }
      is(s_req) {
        when(stage2 && count === r_hgatp_initial_count) {
          gpa_pgoff := Mux(aux_count === (pgLevels - 1).U, r_req.addr << log2Ceil(xLen / 8), stage2_pte_cache_addr)
        }
        // pte_cache hit
        when(stage2_pte_cache_hit) {
          aux_count := aux_count + 1.U
          aux_pte.ppn := stage2_pte_cache_data
          aux_ppn_hi.foreach { _ := 0.U }
          aux_pte.reserved_for_future := 0.U
          pte_hit := true.B
        }.elsewhen(pte_cache_hit) {
          count := count + 1.U
          pte_hit := true.B
        }.otherwise {
          next_state := Mux(io.mem.req.ready, s_wait1, s_req)
        }
      }
      is(s_wait1) {
        // This Mux is for the l2_error case; the l2_hit && !l2_error case is overriden below
        next_state := Mux(l2_hit, s_req, s_wait2)
      }
      is(s_wait2) {
        next_state := s_wait3
        io.dpath.perf.pte_miss := count < (pgLevels - 1).U
        when(io.mem.s2_xcpt.ae.ld) {
          resp_ae_ptw := true.B
          next_state := s_ready
          resp_valid(r_req_dest) := true.B
        }
      }
      is(s_fragment_superpage) {
        next_state := s_ready
        resp_valid(r_req_dest) := true.B
        when(!homogeneous) {
          count := (pgLevels - 1).U
          resp_fragmented_superpage := true.B
        }
        when(do_both_stages) {
          resp_fragmented_superpage := true.B
        }
      }
    }

    val merged_pte = {
      val superpage_masks = (0 until pgLevels).map(i =>
        ((BigInt(1) << pte.ppn.getWidth) - (BigInt(1) << (pgLevels - 1 - i) * pgLevelBits)).U
      )
      val superpage_mask = VecInit(superpage_masks)(Mux(stage2_final, max_count, (pgLevels - 1).U))
      val stage1_ppns = (0 until pgLevels - 1).map(i =>
        Cat(
          pte.ppn(pte.ppn.getWidth - 1, (pgLevels - i - 1) * pgLevelBits),
          aux_pte.ppn((pgLevels - i - 1) * pgLevelBits - 1, 0)
        )
      ) :+ pte.ppn
      val stage1_ppn = VecInit(stage1_ppns)(count)
      makePTE(stage1_ppn & superpage_mask, aux_pte)
    }

    r_pte := OptimizationBarrier(
      // l2tlb hit->find a leaf PTE(l2_pte), respond to L1TLB
      Mux(
        l2_hit && !l2_error,
        l2_pte,
        // S2 PTE cache hit -> proceed to the next level of walking, update the r_pte with hgatp
        Mux(
          state === s_req && stage2_pte_cache_hit,
          makeHypervisorRootPTE(r_hgatp, stage2_pte_cache_data, l2_pte),
          // pte cache hit->find a non-leaf PTE(pte_cache),continue to request mem
          Mux(
            state === s_req && pte_cache_hit,
            makePTE(pte_cache_data, l2_pte),
            // 2-stage translation
            Mux(
              do_switch,
              makeHypervisorRootPTE(r_hgatp, pte.ppn, r_pte),
              // when mem respond, store mem.resp.pte
              Mux(
                mem_resp_valid,
                Mux(!traverse && r_req.vstage1 && stage2, merged_pte, pte),
                // fragment_superpage
                Mux(
                  state === s_fragment_superpage && !homogeneous && count =/= (pgLevels - 1).U,
                  makePTE(VecInit(makeFragmentedSuperpagePPN(r_pte.ppn))(count), r_pte),
                  // when tlb request come->request mem, use root address in satp(or vsatp,hgatp)
                  Mux(
                    arb.io.out.fire,
                    Mux(
                      arb.io.out.bits.bits.stage2,
                      makeHypervisorRootPTE(io.dpath.hgatp, io.dpath.vsatp.ppn, r_pte),
                      makePTE(satp.ppn, r_pte)
                    ),
                    r_pte
                  )
                )
              )
            )
          )
        )
      )
    )

    when(l2_hit && !l2_error) {
      AssertProperty(BoolSequence(state === s_req || state === s_wait1), clock = gated_clock)
      next_state := s_ready
      resp_valid(r_req_dest) := true.B
      count := (pgLevels - 1).U
    }
    when(mem_resp_valid) {
      AssertProperty(BoolSequence(state === s_wait3), clock = gated_clock)
      next_state := s_req
      when(traverse) {
        when(do_both_stages && !stage2) { do_switch := true.B }
        count := count + 1.U
      }.otherwise {
        val gf = stage2 && !stage2_final && !PTE.ur(pte)
        val ae = pte.v && invalid_paddr
        val pf = pte.v && pte.reserved_for_future =/= 0.U
        val success = pte.v && !ae && !pf && !gf

        when(do_both_stages && !stage2_final && success) {
          when(stage2) {
            stage2 := false.B
            count := aux_count
          }.otherwise {
            stage2_final := true.B
            do_switch := true.B
          }
        }.otherwise {
          // find a leaf pte, start l2 refill
          l2_refill := success && count === (pgLevels - 1).U && !r_req.need_gpa &&
          (!r_req.vstage1 && !r_req.stage2 ||
          do_both_stages && aux_count === (pgLevels - 1).U && PTE.isFullPerm(pte))
          count := max_count

          when(
            pageGranularityPMPs.B && !(count === (pgLevels - 1).U && (!do_both_stages || aux_count === (pgLevels - 1).U))
          ) {
            next_state := s_fragment_superpage
          }.otherwise {
            next_state := s_ready
            resp_valid(r_req_dest) := true.B
          }

          resp_ae_ptw := ae && count < (pgLevels - 1).U && PTE.table(pte)
          resp_ae_final := ae
          resp_pf := pf && !stage2
          resp_gf := gf || (pf && stage2)
          resp_hr := !stage2 || (!pf && !gf && PTE.ur(pte))
          resp_hw := !stage2 || (!pf && !gf && PTE.uw(pte))
          resp_hx := !stage2 || (!pf && !gf && PTE.ux(pte))
        }
      }
    }
    when(io.mem.s2_nack) {
      AssertProperty(BoolSequence(state === s_wait2), clock = gated_clock)
      next_state := s_req
    }

    when(do_switch) {
      aux_count := Mux(traverse, count + 1.U, count)
      count := r_hgatp_initial_count
      aux_pte := Mux(
        traverse,
        pte, {
          val s1_ppns = VecInit(
            (0 until pgLevels - 1).map(i =>
              Cat(
                pte.ppn(pte.ppn.getWidth - 1, (pgLevels - i - 1) * pgLevelBits),
                padTo(
                  r_req
                    .addr((((pgLevels - i - 1) * pgLevelBits).min(vpnBits)) - 1, 0),
                  ((pgLevels - i - 1) * pgLevelBits)
                )
              )
            ) :+ pte.ppn
          )
          makePTE(s1_ppns(count), pte)
        }
      )
      aux_ppn_hi.foreach { _ := 0.U }
      stage2 := true.B
    }
  } // leaving gated-clock domain

  /** Relace PTE.ppn with ppn */
  private def makePTE(ppn: UInt, default: PTE) = {
    val pte = WireDefault(default)
    pte.ppn := ppn
    pte
  }

  /** use hgatp and vpn to construct a new ppn */
  private def makeHypervisorRootPTE(hgatp: PTBR, vpn: UInt, default: PTE) = {
    val count = pgLevels.U - minPgLevels.U - additionalPgLevels(hgatp)
    val idxs = VecInit((0 to pgLevels - minPgLevels).map(i => (vpn >> (pgLevels - i) * pgLevelBits)))
    val lsbs = WireDefault(UInt(maxHypervisorExtraAddrBits.W), idxs(count))
    val pte = WireDefault(default)
    pte.ppn := Cat(hgatp.ppn >> maxHypervisorExtraAddrBits, lsbs)
    pte
  }

}
