// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.properties.{AnyClassType, Class, ClassType, Property}
import chisel3.util.random.LFSR
import chisel3.util._
import org.chipsalliance.amba.axi4.bundle.{
  AXI4BundleParameter,
  AXI4ChiselBundle,
  AXI4ROIrrevocable,
  AXI4RWIrrevocable,
  R,
  W
}
import org.chipsalliance.dwbb.stdlib.queue.Queue

case class ICacheParameter(
  useAsyncReset: Boolean,
  prefetch:      Boolean,
  nSets:         Int,
  nWays:         Int,
  blockBytes:    Int,
  usingVM:       Boolean,
  vaddrBits:     Int,
  paddrBits:     Int)
    extends SerializableModuleParameter {
  // static for now
  val latency:                   Int                         = 2
  val itimAXIParameter:          Option[AXI4BundleParameter] = None
  val itimBaseAddr:              Option[BigInt]              = None
  val tagECC:                    Option[String]              = None
  val dataECC:                   Option[String]              = None
  // calculated
  // todo: param?
  val fetchBytes:                Int                         = 4
  val usingITIM:                 Boolean                     = itimAXIParameter.isDefined
  val tagCode:                   Code                        = Code.fromString(tagECC)
  val dataCode:                  Code                        = Code.fromString(dataECC)
  //  (cacheParams.tagCode.canDetect || cacheParams.dataCode.canDetect).option(Valid(UInt(paddrBits.W)))
  val hasCorrectable:            Boolean                     = tagCode.canDetect || dataCode.canDetect
  //  (cacheParams.itimAddr.nonEmpty && cacheParams.dataCode.canDetect).option(Valid(UInt(paddrBits.W)))
  val hasUncorrekoctable:        Boolean                     = itimBaseAddr.nonEmpty && dataCode.canDetect
  val isDM:                      Boolean                     = nWays == 1
  // axi data with
  val rowBits:                   Int                         = fetchBytes * 8
  val refillCycles:              Int                         = blockBytes * 8 / rowBits
  val blockOffBits:              Int                         = log2Up(blockBytes)
  val idxBits:                   Int                         = log2Up(nSets)
  val pgIdxBits:                 Int                         = 12
  val untagBits:                 Int                         = blockOffBits + idxBits
  val pgUntagBits:               Int                         = if (usingVM) untagBits.min(pgIdxBits) else untagBits
  val tagBits:                   Int                         = paddrBits - pgUntagBits
  val instructionFetchParameter: AXI4BundleParameter         = AXI4BundleParameter(
    idWidth = 1,
    dataWidth = rowBits,
    addrWidth = paddrBits,
    userReqWidth = 0,
    userDataWidth = 0,
    userRespWidth = 0,
    hasAW = false,
    hasW = false,
    hasB = false,
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

object ICacheParameter {
  implicit def rwP: upickle.default.ReadWriter[ICacheParameter] = upickle.default.macroRW[ICacheParameter]
}

class ICacheInterface(parameter: ICacheParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())

  /** first cycle requested from CPU. */
  val req = Flipped(Decoupled(new ICacheReq(parameter.vaddrBits)))

  /** from TLB. */
  val s1_paddr = Input(UInt(parameter.paddrBits.W))

  /** from frontend, pipe from s0. */
  val s2_vaddr = Input(UInt(parameter.vaddrBits.W))

  /**   - instruction jmp away(at S2).
    *   - if TLB not valid, kill it.
    *   - S2 replay
    */
  val s1_kill = Input(Bool())

  /** @todo
    *   s2_kill only kill refill?
    *   - S2 speculative access(refill?) cannot access non-cacheable address? why?
    *   - S2 exception (PF, AF)
    */
  val s2_kill      = Input(Bool()) // delayed two cycles; prevents I$ miss emission
  /** should L2 cache line on a miss? */
  val s2_cacheable = Input(Bool()) // should L2 cache line on a miss?
  /** should I$ prefetch next line on a miss? */
  val s2_prefetch  = Input(Bool()) // should I$ prefetch next line on a miss?
  /** response to CPU. */
  val resp         = Valid(new ICacheResp(parameter.fetchBytes))

  /** flush L1 cache from CPU. TODO: IIRC, SFENCE.I
    */
  val invalidate = Input(Bool())

  /** I$ has error, notify to bus. */
  val errors = new ICacheErrors(parameter.hasCorrectable, parameter.hasUncorrekoctable, parameter.paddrBits)

  /** for performance counting. */
  val perf = Output(new ICachePerfEvents)

  /** enable clock. */
  val clock_enabled = Input(Bool())

  /** I$ miss or ITIM access will still enable clock even [[ICache]] is asked to be gated. */
  val keep_clock_enabled = Output(Bool())

  val instructionFetchAXI: AXI4ROIrrevocable =
    org.chipsalliance.amba.axi4.bundle.AXI4ROIrrevocable(parameter.instructionFetchParameter)

  val itimAXI: Option[AXI4RWIrrevocable] =
    parameter.itimAXIParameter.map(p => Flipped(org.chipsalliance.amba.axi4.bundle.AXI4RWIrrevocable(p)))

  val om: Property[ClassType] = Output(Property[AnyClassType]())
}

@instantiable
class ICacheOM extends Class {
  val srams = IO(Output(Property[Seq[AnyClassType]]()))

  @public
  val sramsIn = IO(Input(Property[Seq[AnyClassType]]()))
  srams := sramsIn
}
@instantiable
class ICache(val parameter: ICacheParameter)
    extends FixedIORawModule(new ICacheInterface(parameter))
    with SerializableModule[ICacheParameter]
    with Public
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock              = io.clock
  override protected def implicitReset: Reset              = io.reset
  val omInstance:                       Instance[ICacheOM] = Instantiate(new ICacheOM)
  io.om := omInstance.getPropertyReference.asAnyClassType

  // compatiblity mode
  object Split {
    def apply(x: UInt, n0: Int)                   = {
      val w = x.getWidth
      (x(w - 1, n0), x(n0 - 1, 0))
    }
    def apply(x: UInt, n1: Int, n0: Int)          = {
      val w = x.getWidth
      (x(w - 1, n1), x(n1 - 1, n0), x(n0 - 1, 0))
    }
    def apply(x: UInt, n2: Int, n1: Int, n0: Int) = {
      val w = x.getWidth
      (x(w - 1, n2), x(n2 - 1, n1), x(n1 - 1, n0), x(n0 - 1, 0))
    }
  }

  val usingVM      = parameter.usingVM
  val refillCycles = parameter.refillCycles
  val pgIdxBits    = parameter.pgIdxBits
  val untagBits    = parameter.untagBits
  val nWays        = parameter.nWays
  val nSets        = parameter.nSets
  val blockOffBits = parameter.blockOffBits
  val idxBits      = parameter.idxBits
  val pgUntagBits  = parameter.pgUntagBits
  val tagBits      = parameter.tagBits
  val isDM         = parameter.isDM
  object outer       {
    val size = parameter.nSets * parameter.nWays * parameter.blockBytes
    object icacheParams {
      val fetchBytes = parameter.fetchBytes
      val latency    = parameter.latency
    }
  }
  object cacheParams {
    val prefetch = parameter.prefetch
  }
  // end

  // TODO: move ecc
  val tECC: Code = parameter.tagCode
  val dECC: Code = parameter.dataCode

  require(isPow2(parameter.nSets) && isPow2(parameter.nWays))
  require(
    !usingVM || parameter.usingITIM || pgIdxBits >= untagBits,
    s"When VM and ITIM are enabled, I$$ set size must not exceed ${1 << (pgIdxBits - 10)} KiB; got ${(outer.size / nWays) >> 10} KiB"
  )

  /** register indicates wheather ITIM is enabled. */
  val scratchpadOn = RegInit(false.B)

  /** a cut point to SRAM, indicates which SRAM will be used as SRAM or Cache. */
  val scratchpadMax = Option.when(parameter.usingITIM)(Reg(UInt(log2Ceil(nSets * (nWays - 1)).W)))

  /** Check if a line is in the scratchpad.
    *
    * line is a minimal granularity accessing to SRAM, calculated by [[scratchpadLine]]
    */
  def lineInScratchpad(line: UInt) = scratchpadMax.map(scratchpadOn && line <= _).getOrElse(false.B)

  /** scratchpad base address, if exist [[ICacheParams.itimAddr]], add [[ReplicatedRegion]] to base.
    * @todo
    *   seem [[io_hartid]] is not connected? maybe when implementing itim, LookupByHartId should be changed to [[]]?
    *   should be a Int
    */
  val scratchpadBase: Option[UInt] = None

  /** check an address in the scratchpad address range. */
  def addrMaybeInScratchpad(addr: UInt) =
    scratchpadBase.map(base => addr >= base && addr < base + outer.size.U).getOrElse(false.B)

  /** check property this address(paddr) exists in scratchpad.
    * @todo
    *   seems duplicated in `addrMaybeInScratchpad(addr)` between `lineInScratchpad(addr(untagBits+log2Ceil(nWays)-1,
    *   blockOffBits))`?
    */
  def addrInScratchpad(addr: UInt) =
    addrMaybeInScratchpad(addr) && lineInScratchpad(addr(untagBits + log2Ceil(nWays) - 1, blockOffBits))

  /** return the way which will be used as scratchpad for accessing address
    * {{{
    * │          tag         │    set    │offset│
    *                    └way┘
    * }}}
    * @param addr
    *   address to be found.
    */
  def scratchpadWay(addr: UInt) = addr(untagBits + log2Ceil(nWays) - 1, untagBits)

  /** check if the selected way is legal. note: the last way should be reserved to ICache.
    */
  def scratchpadWayValid(way: UInt) = way < (nWays - 1).U

  /** return the cacheline which will be used as scratchpad for accessing address
    * {{{
    * │          tag         │    set    │offset│
    *                    ├way┘                    → indicate way location
    *                    │    line       │
    * }}}
    * @param addr
    *   address to be found. applied to slave_addr
    */
  def scratchpadLine(addr: UInt) = addr(untagBits + log2Ceil(nWays) - 1, blockOffBits)

  /** scratchpad access valid in stage N */
  val s0_slaveValid      = io.itimAXI.map(axi => axi.w.fire || axi.ar.fire).getOrElse(false.B)
  val s0_slaveWriteValid = io.itimAXI.map(axi => axi.w.fire).getOrElse(false.B)

  val s1_slaveValid      = RegNext(s0_slaveValid, false.B)
  val s1_slaveWriteValid = RegNext(s0_slaveWriteValid, false.B)
  val s2_slaveValid      = RegNext(s1_slaveValid, false.B)
  val s2_slaveWriteValid = RegNext(s1_slaveWriteValid, false.B)
  val s3_slaveValid      = RegNext(false.B)
  val arQueue            = Queue.io(chiselTypeOf(io.instructionFetchAXI.ar.bits), 1, flow = true)

  /** valid signal for CPU accessing cache in stage 0. */
  val s0_valid = io.req.fire

  /** virtual address from CPU in stage 0. */
  val s0_vaddr = io.req.bits.addr

  /** valid signal for stage 1, drived by s0_valid. */
  val s1_valid = RegInit(false.B)

  /** virtual address from CPU in stage 1. */
  val s1_vaddr = RegEnable(s0_vaddr, s0_valid)

  /** tag hit vector to indicate hit which way. */
  val s1_tag_hit = Wire(Vec(nWays, Bool()))

  /** CPU I$ Hit in stage 1.
    *
    * @note
    *   for logic in `Mux(s1_slaveValid, true.B, addrMaybeInScratchpad(io.s1_paddr))`, there are two different types
    *   based on latency:
    *
    * if latency is 1: `s1_slaveValid === false.B` and `addrMaybeInScratchpad(io.s1_paddr) === false.B` , since in this
    * case, ITIM must be empty.
    *
    * if latency is 2: if `s1_slaveValid` is true, this SRAM accessing is coming from [[tl_in]], so it will hit. if
    * `s1_slaveValid` is false, but CPU is accessing memory range in scratchpad address, it will hit by default.
    * Hardware won't guarantee this access will access to a data which have been written in ITIM.
    *
    * @todo
    *   seem CPU access are both processed by `s1_tag_hit` and `Mux(s1_slaveValid, true.B,
    *   addrMaybeInScratchpad(io.s1_paddr))`?
    */
  val s1_hit   = s1_tag_hit.reduce(_ || _) || Mux(s1_slaveValid, true.B, addrMaybeInScratchpad(io.s1_paddr))
  dontTouch(s1_hit)
  val s2_valid = RegNext(s1_valid && !io.s1_kill, false.B)
  val s2_hit   = RegNext(s1_hit)

  /** status register to indicate a cache flush. */
  val invalidated  = Reg(Bool())
  val refill_valid = RegInit(false.B)

  /** register to indicate [[tl_out]] is performing a hint. prefetch only happens after refilling
    */
  val send_hint = RegInit(false.B)

  /** indicate [[tl_out]] is performing a refill. */
  //  val refill_fire = tl_out.a.fire && !send_hint
  val refill_fire = arQueue.enq.fire && !send_hint

  /** register to indicate there is a outstanding hint. */
  val hint_outstanding = RegInit(false.B)

  /** [[io]] access L1 I$ miss. */
  val s2_miss = s2_valid && !s2_hit && !io.s2_kill

  /** forward signal to stage 1, permit stage 1 refill. */
  val s1_can_request_refill = !(s2_miss || refill_valid)

  /** real refill signal, stage 2 miss, and was permit to refill in stage 1. Since a miss will trigger burst. miss under
    * miss won't trigger another burst.
    */
  val s2_request_refill = s2_miss && RegNext(s1_can_request_refill)
  val refill_paddr      = RegEnable(io.s1_paddr, s1_valid && s1_can_request_refill)
  val refill_vaddr      = RegEnable(s1_vaddr, s1_valid && s1_can_request_refill)
  val refill_tag        = refill_paddr >> pgUntagBits
  val refill_idx        = index(refill_vaddr, refill_paddr)

  /** AccessAckData, is refilling I$, it will block request from CPU. */
  //  val refill_one_beat = tl_out.d.fire && edge_out.hasData(tl_out.d.bits)
  // TODO: check hasData?
  val refill_one_beat = io.instructionFetchAXI.r.fire

  /** block request from CPU when refill or scratch pad access. */
  io.req.ready := !(refill_one_beat || s0_slaveValid || s3_slaveValid)
  s1_valid     := s0_valid

  // tod: package
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

  val (_, _, d_done, d_refill_count) = axiHelper(io.instructionFetchAXI.r.bits, io.instructionFetchAXI.r.fire)

  /** at last beat of `tl_out.d.fire`, finish refill. */
  val refill_done = refill_one_beat && d_done

  /** scratchpad is writing data. block refill. */
  io.instructionFetchAXI.r.ready := !s3_slaveValid
  //  require(edge_out.manager.minLatency > 0)

  /** way to be replaced, implemented with a hardcoded random replacement algorithm */
  val repl_way =
    if (isDM) 0.U
    else {
      // pick a way that is not used by the scratchpad
      val v0 = LFSR(16, refill_fire)(log2Up(nWays) - 1, 0)
      var v  = v0
      for (i <- log2Ceil(nWays) - 1 to 0 by -1) {
        val mask = nWays - (BigInt(1) << (i + 1))
        v = v | (lineInScratchpad(Cat(v0 | mask.U, refill_idx)) << i)
      }
      assert(!lineInScratchpad(Cat(v, refill_idx)))
      v
    }

  /** Tag SRAM, indexed with virtual memory, content with `refillError ## tag[19:0]` after ECC
    */
  val icacheTagSRAM: SRAMInterface[Vec[UInt]] = SRAM.masked(
    size = parameter.nSets,
    tpe = Vec(nWays, UInt(tECC.width(1 + tagBits).W)),
    numReadPorts = 0,
    numWritePorts = 0,
    numReadwritePorts = 1
  )

  //  val tag_rdata = tag_array.read(s0_vaddr(untagBits - 1, blockOffBits), !refill_done && s0_valid)
  // todo: read req
  val tag_rdata: Vec[UInt] = icacheTagSRAM.readwritePorts.head.readData

  /** register indicates the ongoing GetAckData transaction is corrupted. */
  val accruedRefillError = Reg(Bool())

  /** wire indicates the ongoing GetAckData transaction is corrupted. */
  //  todo: tl_out.d.bits.corrupt -> false.B
  val refillError: Bool = false.B || (d_refill_count > 0.U && accruedRefillError)
  val enc_tag = tECC.encode(Cat(refillError, refill_tag))
  icacheTagSRAM.readwritePorts.foreach { ramPort =>
    ramPort.enable    := s0_valid || refill_done
    ramPort.isWrite   := refill_done
    ramPort.address   := Mux(refill_done, refill_idx, s0_vaddr(untagBits - 1, blockOffBits))
    ramPort.writeData := VecInit(Seq.fill(nWays) { enc_tag })
    ramPort.mask.foreach(_ := VecInit(Seq.tabulate(nWays)(repl_way === _.U)))
  }
  //    ccover(refillError, "D_CORRUPT", "I$ D-channel corrupt")
  // notify CPU, I$ has corrupt.
  // flase.B ->  (tl_out.d.bits.denied || tl_out.d.bits.corrupt)
  io.errors.bus.valid := io.instructionFetchAXI.r.fire && false.B
  io.errors.bus.bits := (refill_paddr >> blockOffBits) << blockOffBits

  /** true indicate this cacheline is valid, indexed by (wayIndex ## setIndex) after refill_done and not FENCE.I,
    * (repl_way ## refill_idx) set to true.
    */
  val vb_array = RegInit(0.U((nSets * nWays).W))
  when(refill_one_beat) {
    accruedRefillError := refillError
    // clear bit when refill starts so hit-under-miss doesn't fetch bad data
    vb_array           := vb_array.bitSet(Cat(repl_way, refill_idx), refill_done && !invalidated)
  }

  /** flush cache when invalidate is true. */
  val invalidate = WireDefault(io.invalidate)
  when(invalidate) {
    vb_array    := 0.U
    invalidated := true.B
  }

  /** wire indicates that tag is correctable or uncorrectable. will trigger CPU to replay and I$ invalidating, if
    * correctable.
    */
  val s1_tag_disparity = Wire(Vec(nWays, Bool()))

  /** wire indicates that bus has an uncorrectable error. respond to CPU [[io.resp.bits.ae]], cause
    * [[Causes.fetch_access]].
    */
  val s1_tl_error = Wire(Vec(nWays, Bool()))

  /** how many bits will be fetched by CPU for each fetch. */
  val wordBits = outer.icacheParams.fetchBytes * 8

  /** a set of raw data read from [[icacheDataSRAM]]. */
  val s1_dout = Wire(Vec(nWays, UInt(dECC.width(wordBits).W)))
  s1_dout := DontCare

  /** address accessed by [[tl_in]] for ITIM. */
  //  val s0_slaveAddr = tl_in.map(_.a.bits.address).getOrElse(0.U)
  val s0_slaveAddr = io.itimAXI.map(_.aw.bits.addr).getOrElse(0.U)

  /** address used at stage 1 and 3.
    * {{{
    * In stage 1, it caches TileLink data, store in stage 2 if ECC passed.
    * In stage 3, it caches corrected data from stage 2, and store in stage 4.
    * }}}
    */
  val s1s3_slaveAddr = Reg(UInt(log2Ceil(outer.size).W))

  /** data used at stage 1 and 3.
    * {{{
    * In stage 1, it caches TileLink data, store in stage 2.
    * In stage 3, it caches corrected data from data ram, and return to d channel.
    * }}}
    */
  val s1s3_slaveData = Reg(UInt(wordBits.W))

  for (i <- 0 until nWays) {
    val s1_idx = index(s1_vaddr, io.s1_paddr)
    val s1_tag = io.s1_paddr >> pgUntagBits

    /** this way is used by scratchpad. [[icacheTagSRAM]] corrupted.
      */
    val scratchpadHit = scratchpadWayValid(i.U) &&
      Mux(
        s1_slaveValid,
        // scratchpad accessing form [[tl_in]].
        // @todo I think XBar will guarantee there won't be an illegal access on the bus?
        //       so why did have this check `lineInScratchpad(scratchpadLine(s1s3_slaveAddr))`?
        //       I think it will always be true.
        lineInScratchpad(scratchpadLine(s1s3_slaveAddr)) && scratchpadWay(s1s3_slaveAddr) === i.U,
        // scratchpad accessing from [[io]].
        // @todo Accessing ITIM correspond address will be able to read cacheline?
        //       is this desired behavior?
        addrInScratchpad(io.s1_paddr) && scratchpadWay(io.s1_paddr) === i.U
      )
    val s1_vb         = vb_array(Cat(i.U, s1_idx)) && !s1_slaveValid
    val enc_tag       = tECC.decode(tag_rdata(i))

    /** [[tl_error]] ECC error bit. [[tag]] of [[icacheTagSRAM]] access.
      */

    val (tl_error, tag) = Split(enc_tag.uncorrected, tagBits)
    val tagMatch        = s1_vb && tag === s1_tag

    /** tag error happens. */
    s1_tag_disparity(i) := s1_vb && enc_tag.error

    /** if tag matched but ecc checking failed, this access will trigger [[Causes.fetch_access]] exception. */
    s1_tl_error(i) := tagMatch && tl_error.asBool
    s1_tag_hit(i)  := tagMatch || scratchpadHit
  }
  assert(
    !(s1_valid || s1_slaveValid) || PopCount(s1_tag_hit.zip(s1_tag_disparity).map { case (h, d) => h && !d }) <= 1.U
  )

  require(io.instructionFetchAXI.r.bits.data.getWidth % wordBits == 0)

  /** Data SRAM
    *
    * banked with TileLink beat bytes / CPU fetch bytes, indexed with [[index]] and multi-beats cycle, content with
    * `eccError ## wordBits` after ECC.
    * {{{
    * │                          │xx│xxxxxx│xxx│x│xx│
    *                                            ↑word
    *                                          ↑bank
    *                            ↑way
    *                               └─set──┴─offset─┘
    *                               └────row───┘
    * }}}
    * Note: Data SRAM is indexed with virtual memory(vaddr[11:2]),
    *   - vaddr[11:3]->row,
    *   - vaddr[2]->bank=i
    *   - Cache line size = refillCycels(8) * bank(2) * datasize(4 bytes) = 64 bytes
    *   - data width = 32
    *
    * read: read happens in stage 0
    *
    * write: It takes 8 beats to refill 16 instruction in each refilling cycle. Data_array receives data[63:0](2
    * instructions) at once,they will be allocated in deferent bank according to vaddr[2]
    */
  val icacheDataSRAM: Seq[SRAMInterface[Vec[UInt]]] =
    Seq.tabulate(io.instructionFetchAXI.r.bits.data.getWidth / wordBits) { i =>
      SRAM.masked(
        size = nSets * refillCycles,
        tpe = Vec(nWays, UInt(dECC.width(wordBits).W)),
        numReadPorts = 0,
        numWritePorts = 0,
        numReadwritePorts = 1
      )
    }
  omInstance.sramsIn := Property((icacheDataSRAM ++ Some(icacheTagSRAM)).map(_.description.get.asAnyClassType))

  for ((data_array, i) <- icacheDataSRAM.zipWithIndex) {

    /** bank match (vaddr[2]) */
    def wordMatch(addr: UInt): Bool = {
      if (io.instructionFetchAXI.r.bits.data.getWidth == wordBits) { true.B }
      else {
        addr(log2Ceil(io.instructionFetchAXI.r.bits.data.getWidth / 8) - 1, log2Ceil(wordBits / 8)) === i.U
      }
    }

    // TODO: if we have last? do we need refillCycles?
    def row(addr: UInt) = addr(untagBits - 1, blockOffBits - log2Ceil(refillCycles))

    /** read_enable signal */
    val s0_ren = (s0_valid && wordMatch(s0_vaddr)) || (s0_slaveValid && wordMatch(s0_slaveAddr))

    /** write_enable signal refill from [[tl_out]] or ITIM write.
      */
    val wen = (refill_one_beat && !invalidated) || (s3_slaveValid && wordMatch(s1s3_slaveAddr))

    /** index to access [[data_array]]. */
    val mem_idx =
      // I$ refill. refill_idx[2:0] is the beats
      Mux(
        refill_one_beat,
        (refill_idx << log2Ceil(refillCycles)) | d_refill_count,
        // ITIM write.
        Mux(
          s3_slaveValid,
          row(s1s3_slaveAddr),
          // ITIM read.
          Mux(
            s0_slaveValid,
            row(s0_slaveAddr),
            // CPU read.
            row(s0_vaddr)
          )
        )
      )
    val data: UInt =
      Mux(s3_slaveValid, s1s3_slaveData, io.instructionFetchAXI.r.bits.data(wordBits * (i + 1) - 1, wordBits * i))
    // the way to be replaced/written
    val way = Mux(s3_slaveValid, scratchpadWay(s1s3_slaveAddr), repl_way)
    data_array.readwritePorts.foreach { dataPort =>
      dataPort.enable    := wen || s0_ren
      dataPort.isWrite   := wen
      dataPort.address   := mem_idx
      dataPort.writeData := VecInit(Seq.fill(nWays) { dECC.encode(data) })
      dataPort.mask.foreach(_ := VecInit((0 until nWays).map(way === _.U)))
    }

    // write access
    /** data read from [[data_array]]. */
    val dout: Vec[UInt] = data_array.readwritePorts.head.readData
    // Mux to select a way to [[s1_dout]]
    when(wordMatch(Mux(s1_slaveValid, s1s3_slaveAddr, io.s1_paddr))) {
      s1_dout := dout
    }
  }

  /** When writing full words to ITIM, ECC errors are correctable. When writing a full scratchpad word, suppress the
    * read so Xs don't leak out
    */
  val s1s2_full_word_write = WireDefault(false.B)
  val s1_dont_read         = s1_slaveValid && s1s2_full_word_write

  /** clock gate signal for [[s2_tag_hit]], [[s2_dout]], [[s2_tag_disparity]], [[s2_tl_error]], [[s2_scratchpad_hit]].
    */
  val s1_clk_en  = s1_valid || s1_slaveValid
  val s2_tag_hit = RegEnable(Mux(s1_dont_read, 0.U.asTypeOf(s1_tag_hit), s1_tag_hit), s1_clk_en)

  /** way index to access [[icacheDataSRAM]]. */
  val s2_hit_way = OHToUInt(s2_tag_hit)

  /** ITIM index to access [[icacheDataSRAM]]. replace tag with way, word set to 0.
    */
  val s2_scratchpad_word_addr = Cat(
    s2_hit_way,
    Mux(s2_slaveValid, s1s3_slaveAddr, io.s2_vaddr)(untagBits - 1, log2Ceil(wordBits / 8)),
    0.U(log2Ceil(wordBits / 8).W)
  )
  val s2_dout                 = RegEnable(s1_dout, s1_clk_en)
  val s2_way_mux              = Mux1H(s2_tag_hit, s2_dout)
  val s2_tag_disparity        = RegEnable(s1_tag_disparity, s1_clk_en).asUInt.orR
  val s2_tl_error             = RegEnable(s1_tl_error.asUInt.orR, s1_clk_en)

  /** ECC decode result for [[icacheDataSRAM]]. */
  val s2_data_decoded = dECC.decode(s2_way_mux)

  /** ECC error happened, correctable or uncorrectable, ask CPU to replay. */
  val s2_disparity = s2_tag_disparity || s2_data_decoded.error

  /** access hit in ITIM, if [[s1_slaveValid]], this access is from [[tl_in]], else from CPU [[io]]. */
  val s1_scratchpad_hit =
    Mux(s1_slaveValid, lineInScratchpad(scratchpadLine(s1s3_slaveAddr)), addrInScratchpad(io.s1_paddr))

  /** stage 2 of [[s1_scratchpad_hit]]. */
  val s2_scratchpad_hit = RegEnable(s1_scratchpad_hit, s1_clk_en)

  /** ITIM uncorrectable read. `s2_scratchpad_hit`: processing a scratchpad read(from [[tl_in]] or [[io]])
    * `s2_data_decoded.uncorrectable`: read a uncorrectable data. `s2_valid`: [[io]] non-canceled read. `(s2_slaveValid
    * && !s2_full_word_write)`: [[tl_in]] read or write a word with wormhole. if write a full word, even stage 2 read
    * uncorrectable. stage 3 full word write will recovery this.
    */
  val s2_report_uncorrectable_error =
    s2_scratchpad_hit && s2_data_decoded.uncorrectable && (s2_valid || (s2_slaveValid && !s1s2_full_word_write))

  /** ECC uncorrectable address, send to Bus Error Unit. */
  val s2_error_addr =
    scratchpadBase.map(base => Mux(s2_scratchpad_hit, base + s2_scratchpad_word_addr, 0.U)).getOrElse(0.U)

  // output signals
  outer.icacheParams.latency match {
    // if I$ latency is 1, no ITIM, no ECC.
    case 1 =>
      require(tECC.isInstanceOf[IdentityCode])
      require(dECC.isInstanceOf[IdentityCode])
      require(parameter.itimAXIParameter.isEmpty)
      // reply data to CPU at stage 2. no replay.
      io.resp.bits.data   := Mux1H(s1_tag_hit, s1_dout)
      io.resp.bits.ae     := s1_tl_error.asUInt.orR
      io.resp.valid       := s1_valid && s1_hit
      io.resp.bits.replay := false.B

    // if I$ latency is 2, can have ITIM and ECC.
    case 2 =>
      // when some sort of memory bit error have occurred
      // @todo why so aggressive to invalidate all when ecc corrupted.
      when(s2_valid && s2_disparity) { invalidate := true.B }

      // reply data to CPU at stage 2.
      io.resp.bits.data   := s2_data_decoded.uncorrected
      io.resp.bits.ae     := s2_tl_error
      io.resp.bits.replay := s2_disparity
      io.resp.valid       := s2_valid && s2_hit

      // report correctable error to BEU at stage 2.
      io.errors.correctable.foreach { c =>
        c.valid := (s2_valid || s2_slaveValid) && s2_disparity && !s2_report_uncorrectable_error
        c.bits  := s2_error_addr
      }
      // report uncorrectable error to BEU at stage 2.
      io.errors.uncorrectable.foreach { u =>
        u.valid := s2_report_uncorrectable_error
        u.bits  := s2_error_addr
      }

      // ITIM access
      io.itimAXI.foreach { axi =>
        /** valid signal for D channel. */
        val respValid = RegInit(false.B)
        // ITIM access is unpipelined
        axi.ar.ready := !(io.instructionFetchAXI.r.valid || s1_slaveValid || s2_slaveValid || s3_slaveValid || respValid || !io.clock_enabled)

        /** register used to latch TileLink request for one cycle. */
        val s1_a  = RegEnable(axi.ar.bits, s0_slaveValid)
        val s1_aw = RegEnable(axi.aw.bits, axi.aw.fire)
        val s1_w  = RegEnable(axi.w.bits, axi.w.fire)
        // Write Data(Put / PutPartial all mask is 1)
        s1s2_full_word_write := axi.w.bits.strb.andR
        // (de)allocate ITIM
        when(axi.w.fire) {
          // address
          s1s3_slaveAddr := s1_aw.addr
          // store Put/PutP data
          s1s3_slaveData := axi.w.bits.data
          // S0
          // access data in 0 -> way - 2 allocate and enable, access data in way - 1(last way), deallocate.
          val enable = scratchpadWayValid(scratchpadWay(s1_aw.addr))
          // The address isn't in range,
          when(!lineInScratchpad(scratchpadLine(s1_aw.addr))) {
            scratchpadMax.get := scratchpadLine(s1_aw.addr)
            invalidate        := true.B
          }
          scratchpadOn := enable
          //            val itim_allocated = !scratchpadOn && enable
          //            val itim_deallocated = scratchpadOn && !enable
          //            val itim_increase = scratchpadOn && enable && scratchpadLine(a.address) > scratchpadMax.get
          //            val refilling = refill_valid && refill_cnt > 0.U
          //            ccover(itim_allocated, "ITIM_ALLOCATE", "ITIM allocated")
          //            ccover(itim_allocated && refilling, "ITIM_ALLOCATE_WHILE_REFILL", "ITIM allocated while I$ refill")
          //            ccover(itim_deallocated, "ITIM_DEALLOCATE", "ITIM deallocated")
          //            ccover(itim_deallocated && refilling, "ITIM_DEALLOCATE_WHILE_REFILL", "ITIM deallocated while I$ refill")
          //            ccover(itim_increase, "ITIM_SIZE_INCREASE", "ITIM size increased")
          //            ccover(itim_increase && refilling, "ITIM_SIZE_INCREASE_WHILE_REFILL", "ITIM size increased while I$ refill")
        }

        assert(!s2_valid || RegNext(RegNext(s0_vaddr)) === io.s2_vaddr)
        when(
          !(axi.w.valid || s1_slaveValid || s2_slaveValid || respValid)
            && s2_valid && s2_data_decoded.error && !s2_tag_disparity
        ) {
          // handle correctable errors on CPU accesses to the scratchpad.
          // if there is an in-flight slave-port access to the scratchpad,
          // report the miss but don't correct the error (as there is
          // a structural hazard on s1s3_slaveData/s1s3_slaveAddress).
          s3_slaveValid  := true.B
          s1s3_slaveData := s2_data_decoded.corrected
          s1s3_slaveAddr := s2_scratchpad_word_addr | s1s3_slaveAddr(log2Ceil(wordBits / 8) - 1, 0)
        }

        // back pressure is allowed on the [[tl]]
        // pull up [[respValid]] when [[s2_slaveValid]] until [[tl.d.fire]]
        respValid := s2_slaveValid || (respValid && !axi.r.ready)
        // if [[s2_full_word_write]] will overwrite data, and [[s2_data_decoded.uncorrectable]] can be ignored.
        val respError =
          RegEnable(s2_scratchpad_hit && s2_data_decoded.uncorrectable && !s1s2_full_word_write, s2_slaveValid)
        when(s2_slaveValid) {
          // need stage 3 if Put or correct decoding.
          // @todo if uncorrectable [[s2_data_decoded]]?
          when(s2_slaveWriteValid || s2_data_decoded.error) { s3_slaveValid := true.B }

          /** data not masked by the TileLink PutData/PutPartialData. means data is stored at [[s1s3_slaveData]] which
            * was read at stage 1.
            */
          def byteEn(i: Int) = !axi.w.bits.strb(i)
          // write [[s1s3_slaveData]] based on index of wordBits.
          // @todo seems a problem here?
          //       granularity of CPU fetch is `wordBits/8`,
          //       granularity of TileLink access is `TLBundleParameters.dataBits/8`
          //       these two granularity can be different.
          // store data read from RAM
          s1s3_slaveData := VecInit(
            (0 until wordBits / 8)
              .map(i => Mux(byteEn(i), s2_data_decoded.corrected, s1s3_slaveData)(8 * (i + 1) - 1, 8 * i))
          ).asUInt
        }

        axi.r.valid     := respValid
        //        tl.d.bits := Mux(
        //          edge_in.get.hasData(s1_a),
        //          // PutData/PutPartialData -> AccessAck
        //          edge_in.get.AccessAck(s1_a),
        //          // Get -> AccessAckData
        //          edge_in.get.AccessAck(s1_a, 0.U, denied = false.B, corrupt = respError)
        //        )
        axi.r.bits      := DontCare
        axi.r.bits.data := s1s3_slaveData
        axi.r.bits.last := true.B
        // Tie off unused channels
        axi.b.valid     := false.B

        //        ccover(s0_valid && s1_slaveValid, "CONCURRENT_ITIM_ACCESS_1", "ITIM accessed, then I$ accessed next cycle")
        //        ccover(
        //          s0_valid && s2_slaveValid,
        //          "CONCURRENT_ITIM_ACCESS_2",
        //          "ITIM accessed, then I$ accessed two cycles later"
        //        )
        //        ccover(tl.d.valid && !tl.d.ready, "ITIM_D_STALL", "ITIM response blocked by D-channel")
        //        ccover(tl_out.d.valid && !tl_out.d.ready, "ITIM_BLOCK_D", "D-channel blocked by ITIM access")
      }
  }

  arQueue.enq.valid      := s2_request_refill
  arQueue.enq.bits       := DontCare
  arQueue.enq.bits.id    := 0.U
  arQueue.enq.bits.addr  := (refill_paddr >> blockOffBits) << blockOffBits
  arQueue.enq.bits.size  := log2Up(parameter.instructionFetchParameter.dataWidth / 8).U
  arQueue.enq.bits.len   := (parameter.blockBytes * 8 / parameter.instructionFetchParameter.dataWidth - 1).U
  arQueue.enq.bits.burst := 1.U
  io.instructionFetchAXI.ar <> arQueue.deq

  // prefetch when next-line access does not cross a page
  if (cacheParams.prefetch) {

    /** [[crosses_page]] indicate if there is a crosses page access [[next_block]] : the address to be prefetched.
      */
    val (crosses_page, next_block) = Split(refill_paddr(pgIdxBits - 1, blockOffBits) +& 1.U, pgIdxBits - blockOffBits)
    // AXI Hint via AxCache ?

    //    when(tl_out.a.fire) {
    //      send_hint := !hint_outstanding && io.s2_prefetch && !crosses_page
    //      when(send_hint) {
    //        send_hint := false.B
    //        hint_outstanding := true.B
    //      }
    //    }
    //
    //    // @todo why refill_done will kill hint at this cycle?
    //    when(refill_done) {
    //      send_hint := false.B
    //    }

    // D channel reply with HintAck.
    //    when(tl_out.d.fire && !refill_one_beat) {
    //      hint_outstanding := false.B
    //    }

    //    when(send_hint) {
    //      tl_out.a.valid := true.B
    //      tl_out.a.bits := edge_out
    //        .Hint(
    //          fromSource = 1.U,
    //          toAddress = Cat(refill_paddr >> pgIdxBits, next_block) << blockOffBits,
    //          lgSize = lgCacheBlockBytes.U,
    //          param = TLHints.PREFETCH_READ
    //        )
    //        ._2
    //    }

    //    ccover(send_hint && !tl_out.a.ready, "PREFETCH_A_STALL", "I$ prefetch blocked by A-channel")
    //    ccover(
    //      refill_valid && (tl_out.d.fire && !refill_one_beat),
    //      "PREFETCH_D_BEFORE_MISS_D",
    //      "I$ prefetch resolves before miss"
    //    )
    //    ccover(
    //      !refill_valid && (tl_out.d.fire && !refill_one_beat),
    //      "PREFETCH_D_AFTER_MISS_D",
    //      "I$ prefetch resolves after miss"
    //    )
    //    ccover(tl_out.a.fire && hint_outstanding, "PREFETCH_D_AFTER_MISS_A", "I$ prefetch resolves after second miss")
  }
  // Drive APROT information
  // bufferable ## modifiable ## readalloc ## writealloc ## privileged ## secure ## fetch
  arQueue.enq.bits.user := true.B ## true.B ## io.s2_cacheable ## io.s2_cacheable ##
    true.B ## true.B ## true.B
  // tl_out.a.bits.user.lift(AMBAProt).foreach { x =>
  //   // Rocket caches all fetch requests, and it's difficult to differentiate privileged/unprivileged on
  //   // cached data, so mark as privileged
  //   x.fetch := true.B
  //   x.secure := true.B
  //   x.privileged := true.B
  //   x.bufferable := true.B
  //   x.modifiable := true.B
  //   x.readalloc := io.s2_cacheable
  //   x.writealloc := io.s2_cacheable
  // }
  // tl_out.b.ready := true.B
  // tl_out.c.valid := false.B
  // tl_out.e.valid := false.B
  assert(!(arQueue.enq.valid && addrMaybeInScratchpad(arQueue.enq.bits.addr)))

  // if there is an outstanding refill, cannot flush I$.
  when(!refill_valid) { invalidated := false.B }
  when(refill_fire) { refill_valid := true.B }
  when(refill_done) { refill_valid := false.B }

  io.perf.acquire       := refill_fire
  // don't gate I$ clock since there are outstanding transcations.
  io.keep_clock_enabled :=
    io.itimAXI
      .map(axi =>
        axi.ar.valid || axi.aw.valid || axi.w.valid // tl.a.valid
          || axi.r.valid                            // tl.d.valid
          || s1_slaveValid || s2_slaveValid || s3_slaveValid
      )
      .getOrElse(false.B) || // ITIM
      s1_valid || s2_valid || refill_valid || send_hint || hint_outstanding // I$

  /** index to access [[icacheDataSRAM]] and [[icacheTagSRAM]].
    *
    * @note
    *   if [[untagBits]] > [[pgIdxBits]] in
    *   {{{
    *                        ┌──idxBits──┐
    *                        ↓           ↓
    * │          tag         │    set    │offset│
    * │              pageTag     │     pageIndex│
    *                        ↑   ↑       ↑      │
    *                   untagBits│  blockOffBits│
    *                       pgIdxBits    │
    *                        └msb┴──lsb──┘
    *                        vaddr paddr
    *   }}}
    *
    * else use paddr directly. Note: if [[untagBits]] > [[pgIdxBits]], there will be a alias issue which isn't
    * addressend by the icache yet.
    */
  def index(vaddr: UInt, paddr: UInt) = {

    /** [[paddr]] as LSB to be used for VIPT. */
    val lsbs = paddr(pgUntagBits - 1, blockOffBits)

    /** if [[untagBits]] > [[pgIdxBits]], append [[vaddr]] to higher bits of index as [[msbs]]. */
    val msbs = Option.when(idxBits + blockOffBits > pgUntagBits)(vaddr(idxBits + blockOffBits - 1, pgUntagBits))
    msbs.map(_ ## lsbs).getOrElse(lsbs)
  }

  //  ccover(!send_hint && (tl_out.a.valid && !tl_out.a.ready), "MISS_A_STALL", "I$ miss blocked by A-channel")
  //  ccover(invalidate && refill_valid, "FLUSH_DURING_MISS", "I$ flushed during miss")

  //  def ccover(cond: Bool, label: String, desc: String)(implicit sourceInfo: SourceInfo) =
  //    property.cover(cond, s"ICACHE_$label", "MemorySystem;;" + desc)
  //
  //  val mem_active_valid = Seq(property.CoverBoolean(s2_valid, Seq("mem_active")))
  //  val data_error = Seq(
  //    property.CoverBoolean(!s2_data_decoded.correctable && !s2_data_decoded.uncorrectable, Seq("no_data_error")),
  //    property.CoverBoolean(s2_data_decoded.correctable, Seq("data_correctable_error")),
  //    property.CoverBoolean(s2_data_decoded.uncorrectable, Seq("data_uncorrectable_error"))
  //  )
  //  val request_source = Seq(
  //    property.CoverBoolean(!s2_slaveValid, Seq("from_CPU")),
  //    property.CoverBoolean(s2_slaveValid, Seq("from_TL"))
  //  )
  //  val tag_error = Seq(
  //    property.CoverBoolean(!s2_tag_disparity, Seq("no_tag_error")),
  //    property.CoverBoolean(s2_tag_disparity, Seq("tag_error"))
  //  )
  //  val mem_mode = Seq(
  //    property.CoverBoolean(s2_scratchpad_hit, Seq("ITIM_mode")),
  //    property.CoverBoolean(!s2_scratchpad_hit, Seq("cache_mode"))
  //  )

  //  val error_cross_covers = new property.CrossProperty(
  //    Seq(mem_active_valid, data_error, tag_error, request_source, mem_mode),
  //    Seq(
  //      // tag error cannot occur in ITIM mode
  //      Seq("tag_error", "ITIM_mode"),
  //      // Can only respond to TL in ITIM mode
  //      Seq("from_TL", "cache_mode")
  //    ),
  //    "MemorySystem;;Memory Bit Flip Cross Covers"
  //  )
  //
  //  property.cover(error_cross_covers)
}
