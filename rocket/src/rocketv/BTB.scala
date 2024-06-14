// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._

// TODO: rethink about the hierarchy of entire core.
case class BHTParameter(nEntries: Int, counterLength: Int, historyLength: Int, historyBits: Int)

case class BTBParameter(
  fetchBytes:        Int,
  fetchWidth:        Int,
  vaddrBits:         Int,
  entries:           Int,
  nMatchBits:        Int,
  nPages:            Int,
  nRAS:              Int,
  cacheBlockBytes:   Int,
  iCacheSet:         Int,
  useCompressed:     Boolean,
  updatesOutOfOrder: Boolean,
  // below is for BHT, notice, the BHT is not a actually module:(
  bhtParameter: Option[BHTParameter])
    extends SerializableModuleParameter {
  val nEntries:        Int = entries
  val CacheBlockBytes: Int = cacheBlockBytes
}

class BTBInterface(parameter: BTBParameter) extends Bundle {
  val req = Flipped(Valid(new BTBReq(parameter.vaddrBits)))
  val resp = Valid(
    new BTBResp(
      parameter.fetchWidth,
      parameter.vaddrBits,
      parameter.entries,
      parameter.bhtParameter.map(_.historyLength),
      parameter.bhtParameter.map(_.counterLength)
    )
  )
  val btb_update = Flipped(
    Valid(
      new BTBUpdate(
        parameter.fetchWidth,
        parameter.vaddrBits,
        parameter.entries,
        parameter.bhtParameter.map(_.historyLength),
        parameter.bhtParameter.map(_.counterLength)
      )
    )
  )
  val bht_update = Flipped(
    Valid(
      new BHTUpdate(
        parameter.bhtParameter.map(_.historyLength),
        parameter.bhtParameter.map(_.counterLength),
        parameter.vaddrBits
      )
    )
  )
  val bht_advance = Flipped(
    Valid(
      new BTBResp(
        parameter.fetchWidth,
        parameter.vaddrBits,
        parameter.entries,
        parameter.bhtParameter.map(_.historyLength),
        parameter.bhtParameter.map(_.counterLength)
      )
    )
  )
  val ras_update = Flipped(Valid(new RASUpdate(parameter.vaddrBits)))
  val ras_head = Valid(UInt(parameter.vaddrBits.W))
  val flush = Input(Bool())
}

class BTB(val parameter: BTBParameter)
    extends FixedIORawModule(new BTBInterface(parameter))
    with SerializableModule[BTBParameter] {
  // compatibility layer
  val entries = parameter.entries
  val nMatchBits = parameter.nMatchBits
  val matchBits = parameter.nMatchBits.max(log2Ceil(parameter.CacheBlockBytes * parameter.iCacheSet))
  val coreInstBytes = (if (parameter.useCompressed) 16 else 32) / 8
  val nPages = (parameter.nPages + 1) / 2 * 2 // control logic assumes 2 divides pages
  val vaddrBits = parameter.vaddrBits
  val fetchWidth = 1
  val updatesOutOfOrder = parameter.updatesOutOfOrder
  // original implementation.

  val idxs = Reg(Vec(entries, UInt((matchBits - log2Up(coreInstBytes)).W)))
  val idxPages = Reg(Vec(entries, UInt(log2Up(nPages).W)))
  val tgts = Reg(Vec(entries, UInt((matchBits - log2Up(coreInstBytes)).W)))
  val tgtPages = Reg(Vec(entries, UInt(log2Up(nPages).W)))
  val pages = Reg(Vec(nPages, UInt((vaddrBits - matchBits).W)))
  val pageValid = RegInit(0.U(nPages.W))
  val pagesMasked = (pageValid.asBools.zip(pages)).map { case (v, p) => Mux(v, p, 0.U) }

  val isValid = RegInit(0.U(entries.W))
  val cfiType = Reg(Vec(entries, CFIType()))
  val brIdx = Reg(Vec(entries, UInt(log2Up(fetchWidth).W)))

  private def page(addr: UInt) = addr >> matchBits
  private def pageMatch(addr: UInt) = {
    val p = page(addr).asUInt
    pageValid & VecInit(pages.map(_ === p)).asUInt
  }
  private def idxMatch(addr: UInt) = {
    val idx = addr(matchBits - 1, log2Up(coreInstBytes))
    VecInit(idxs.map(_ === idx)).asUInt & isValid
  }

  val r_btb_update = Pipe(io.btb_update)
  val update_target = io.req.bits.addr

  val pageHit = pageMatch(io.req.bits.addr)
  val idxHit = idxMatch(io.req.bits.addr)

  val updatePageHit = pageMatch(r_btb_update.bits.pc)
  val (updateHit, updateHitAddr) =
    if (updatesOutOfOrder) {
      val updateHits = (pageHit << 1)(Mux1H(idxMatch(r_btb_update.bits.pc), idxPages))
      (updateHits.orR, OHToUInt(updateHits))
    } else (r_btb_update.bits.prediction.entry < entries.U, r_btb_update.bits.prediction.entry)

  val useUpdatePageHit = updatePageHit.orR
  val usePageHit = pageHit.orR
  val doIdxPageRepl = !useUpdatePageHit
  val nextPageRepl = RegInit(0.U(log2Ceil(nPages).W))
  val idxPageRepl = Cat(pageHit(nPages - 2, 0), pageHit(nPages - 1)) | Mux(usePageHit, 0.U, UIntToOH(nextPageRepl))
  val idxPageUpdateOH = Mux(useUpdatePageHit, updatePageHit, idxPageRepl)
  val idxPageUpdate = OHToUInt(idxPageUpdateOH)
  val idxPageReplEn = Mux(doIdxPageRepl, idxPageRepl, 0.U)

  val samePage = page(r_btb_update.bits.pc) === page(update_target)
  val doTgtPageRepl = !samePage && !usePageHit
  val tgtPageRepl = Mux(samePage, idxPageUpdateOH, Cat(idxPageUpdateOH(nPages - 2, 0), idxPageUpdateOH(nPages - 1)))
  val tgtPageUpdate = OHToUInt(pageHit | Mux(usePageHit, 0.U, tgtPageRepl))
  val tgtPageReplEn = Mux(doTgtPageRepl, tgtPageRepl, 0.U)

  when(r_btb_update.valid && (doIdxPageRepl || doTgtPageRepl)) {
    val both = doIdxPageRepl && doTgtPageRepl
    val next = nextPageRepl + Mux[UInt](both, 2.U, 1.U)
    nextPageRepl := Mux(next >= nPages.U, next(0), next)
  }

  class PseudoLRU(n_ways: Int) {
    // Pseudo-LRU tree algorithm: https://en.wikipedia.org/wiki/Pseudo-LRU#Tree-PLRU
    //
    //
    // - bits storage example for 4-way PLRU binary tree:
    //                  bit[2]: ways 3+2 older than ways 1+0
    //                  /                                  \
    //     bit[1]: way 3 older than way 2    bit[0]: way 1 older than way 0
    //
    //
    // - bits storage example for 3-way PLRU binary tree:
    //                  bit[1]: way 2 older than ways 1+0
    //                                                  \
    //                                       bit[0]: way 1 older than way 0
    //
    //
    // - bits storage example for 8-way PLRU binary tree:
    //                      bit[6]: ways 7-4 older than ways 3-0
    //                      /                                  \
    //            bit[5]: ways 7+6 > 5+4                bit[2]: ways 3+2 > 1+0
    //            /                    \                /                    \
    //     bit[4]: way 7>6    bit[3]: way 5>4    bit[1]: way 3>2    bit[0]: way 1>0

    def nBits = n_ways - 1
    def perSet = true
    private val state_reg = if (nBits == 0) Reg(UInt(0.W)) else RegInit(0.U(nBits.W))
    def state_read = WireDefault(state_reg)

    def access(touch_way: UInt): Unit = {
      state_reg := get_next_state(state_reg, touch_way)
    }
    def access(touch_ways: Seq[Valid[UInt]]): Unit = {
      when(VecInit(touch_ways.map(_.valid)).asUInt.orR) {
        state_reg := get_next_state(state_reg, touch_ways)
      }
      // for (i <- 1 until touch_ways.size) {
      //   cover(PopCount(touch_ways.map(_.valid)) === i.U, s"PLRU_UpdateCount$i", s"PLRU Update $i simultaneous")
      // }
    }

    def get_next_state(state: UInt, touch_ways: Seq[Valid[UInt]]): UInt = {
      touch_ways.foldLeft(state)((prev, touch_way) => Mux(touch_way.valid, get_next_state(prev, touch_way.bits), prev))
    }

    /** @param state state_reg bits for this sub-tree
      * @param touch_way touched way encoded value bits for this sub-tree
      * @param tree_nways number of ways in this sub-tree
      */
    def get_next_state(state: UInt, touch_way: UInt, tree_nways: Int): UInt = {
      require(state.getWidth == (tree_nways - 1), s"wrong state bits width ${state.getWidth} for $tree_nways ways")
      require(
        touch_way.getWidth == (log2Ceil(tree_nways).max(1)),
        s"wrong encoded way width ${touch_way.getWidth} for $tree_nways ways"
      )

      if (tree_nways > 2) {
        // we are at a branching node in the tree, so recurse
        val right_nways: Int = 1 << (log2Ceil(tree_nways) - 1) // number of ways in the right sub-tree
        val left_nways:  Int = tree_nways - right_nways // number of ways in the left sub-tree
        val set_left_older = !touch_way(log2Ceil(tree_nways) - 1)
        val left_subtree_state = state(tree_nways - 3, right_nways - 1)
        val right_subtree_state = state(right_nways - 2, 0)

        if (left_nways > 1) {
          // we are at a branching node in the tree with both left and right sub-trees, so recurse both sub-trees
          Cat(
            set_left_older,
            Mux(
              set_left_older,
              left_subtree_state, // if setting left sub-tree as older, do NOT recurse into left sub-tree
              get_next_state(left_subtree_state, touch_way(log2Ceil(left_nways) - 1, 0), left_nways)
            ), // recurse left if newer
            Mux(
              set_left_older,
              get_next_state(
                right_subtree_state,
                touch_way(log2Ceil(right_nways) - 1, 0),
                right_nways
              ), // recurse right if newer
              right_subtree_state
            )
          ) // if setting right sub-tree as older, do NOT recurse into right sub-tree
        } else {
          // we are at a branching node in the tree with only a right sub-tree, so recurse only right sub-tree
          Cat(
            set_left_older,
            Mux(
              set_left_older,
              get_next_state(
                right_subtree_state,
                touch_way(log2Ceil(right_nways) - 1, 0),
                right_nways
              ), // recurse right if newer
              right_subtree_state
            )
          ) // if setting right sub-tree as older, do NOT recurse into right sub-tree
        }
      } else if (tree_nways == 2) {
        // we are at a leaf node at the end of the tree, so set the single state bit opposite of the lsb of the touched way encoded value
        !touch_way(0)
      } else { // tree_nways <= 1
        // we are at an empty node in an empty tree for 1 way, so return single zero bit for Chisel (no zero-width wires)
        0.U(1.W)
      }
    }

    def get_next_state(state: UInt, touch_way: UInt): UInt = {
      def padTo(x: UInt, n: Int): UInt = {
        require(x.getWidth <= n)
        if (x.getWidth == n) x
        else Cat(0.U((n - x.getWidth).W), x)
      }

      val touch_way_sized =
        if (touch_way.getWidth < log2Ceil(n_ways)) padTo(touch_way, log2Ceil(n_ways))
        else touch_way(log2Ceil(n_ways) - 1, 0)
      get_next_state(state, touch_way_sized, n_ways)
    }

    /** @param state state_reg bits for this sub-tree
      * @param tree_nways number of ways in this sub-tree
      */
    def get_replace_way(state: UInt, tree_nways: Int): UInt = {
      require(state.getWidth == (tree_nways - 1), s"wrong state bits width ${state.getWidth} for $tree_nways ways")

      // this algorithm recursively descends the binary tree, filling in the way-to-replace encoded value from msb to lsb
      if (tree_nways > 2) {
        // we are at a branching node in the tree, so recurse
        val right_nways: Int = 1 << (log2Ceil(tree_nways) - 1) // number of ways in the right sub-tree
        val left_nways:  Int = tree_nways - right_nways // number of ways in the left sub-tree
        val left_subtree_older = state(tree_nways - 2)
        val left_subtree_state = state(tree_nways - 3, right_nways - 1)
        val right_subtree_state = state(right_nways - 2, 0)

        if (left_nways > 1) {
          // we are at a branching node in the tree with both left and right sub-trees, so recurse both sub-trees
          Cat(
            left_subtree_older, // return the top state bit (current tree node) as msb of the way-to-replace encoded value
            Mux(
              left_subtree_older, // if left sub-tree is older, recurse left, else recurse right
              get_replace_way(left_subtree_state, left_nways), // recurse left
              get_replace_way(right_subtree_state, right_nways)
            )
          ) // recurse right
        } else {
          // we are at a branching node in the tree with only a right sub-tree, so recurse only right sub-tree
          Cat(
            left_subtree_older, // return the top state bit (current tree node) as msb of the way-to-replace encoded value
            Mux(
              left_subtree_older, // if left sub-tree is older, return and do not recurse right
              0.U(1.W),
              get_replace_way(right_subtree_state, right_nways)
            )
          ) // recurse right
        }
      } else if (tree_nways == 2) {
        // we are at a leaf node at the end of the tree, so just return the single state bit as lsb of the way-to-replace encoded value
        state(0)
      } else { // tree_nways <= 1
        // we are at an empty node in an unbalanced tree for non-power-of-2 ways, so return single zero bit as lsb of the way-to-replace encoded value
        0.U(1.W)
      }
    }

    def get_replace_way(state: UInt): UInt = get_replace_way(state, n_ways)

    def way = get_replace_way(state_reg)
    def miss = access(way)
    def hit = {}
  }
  val repl = new PseudoLRU(entries)

  val waddr = Mux(updateHit, updateHitAddr, repl.way)
  val r_resp = Pipe(io.resp)
  when(r_resp.valid && r_resp.bits.taken || r_btb_update.valid) {
    repl.access(Mux(r_btb_update.valid, waddr, r_resp.bits.entry))
  }

  when(r_btb_update.valid) {
    val mask = UIntToOH(waddr)
    idxs(waddr) := r_btb_update.bits.pc(matchBits - 1, log2Up(coreInstBytes))
    tgts(waddr) := update_target(matchBits - 1, log2Up(coreInstBytes))
    idxPages(waddr) := idxPageUpdate +& 1.U // the +1 corresponds to the <<1 on io.resp.valid
    tgtPages(waddr) := tgtPageUpdate
    cfiType(waddr) := r_btb_update.bits.cfiType
    isValid := Mux(r_btb_update.bits.isValid, isValid | mask, isValid & ~mask)
    if (fetchWidth > 1)
      brIdx(waddr) := r_btb_update.bits.br_pc >> log2Up(coreInstBytes)

    require(nPages % 2 == 0)
    val idxWritesEven = !idxPageUpdate(0)

    def writeBank(i: Int, mod: Int, en: UInt, data: UInt) =
      for (i <- i until nPages by mod)
        when(en(i)) { pages(i) := data }

    writeBank(
      0,
      2,
      Mux(idxWritesEven, idxPageReplEn, tgtPageReplEn),
      Mux(idxWritesEven, page(r_btb_update.bits.pc), page(update_target))
    )
    writeBank(
      1,
      2,
      Mux(idxWritesEven, tgtPageReplEn, idxPageReplEn),
      Mux(idxWritesEven, page(update_target), page(r_btb_update.bits.pc))
    )
    pageValid := pageValid | tgtPageReplEn | idxPageReplEn
  }

  io.resp.valid := (pageHit << 1)(Mux1H(idxHit, idxPages))
  io.resp.bits.taken := true.B
  io.resp.bits.target := Cat(pagesMasked(Mux1H(idxHit, tgtPages)), Mux1H(idxHit, tgts) << log2Up(coreInstBytes))
  io.resp.bits.entry := OHToUInt(idxHit)
  io.resp.bits.bridx := (if (fetchWidth > 1) Mux1H(idxHit, brIdx) else 0.U)
  io.resp.bits.mask := Cat((1.U << ~Mux(io.resp.bits.taken, ~io.resp.bits.bridx, 0.U)) - 1.U, 1.U)
  io.resp.bits.cfiType := Mux1H(idxHit, cfiType)

  // if multiple entries for same PC land in BTB, zap them

  // TODO: upstream these utilities
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

  when(PopCountAtLeast(idxHit, 2)) {
    isValid := isValid & ~idxHit
  }
  when(io.flush) {
    isValid := 0.U
  }

  parameter.bhtParameter.foreach { bhtParameter =>
    /** BHT contains table of 2-bit counters and a global history register.
      * The BHT only predicts and updates when there is a BTB hit.
      * The global history:
      *    - updated speculatively in fetch (if there's a BTB hit).
      *    - on a mispredict, the history register is reset (again, only if BTB hit).
      * The counter table:
      *    - each counter corresponds with the address of the fetch packet ("fetch pc").
      *    - updated when a branch resolves (and BTB was a hit for that branch).
      *      The updating branch must provide its "fetch pc".
      */
    class BHT {
      def index(addr: UInt, history: UInt) = {
        def hashHistory(hist: UInt) = if (bhtParameter.historyLength == bhtParameter.historyBits) hist
        else {
          val k = math.sqrt(3) / 2
          val i = BigDecimal(k * math.pow(2, bhtParameter.historyLength)).toBigInt
          (i.U * hist)(bhtParameter.historyLength - 1, bhtParameter.historyLength - bhtParameter.historyBits)
        }
        def hashAddr(addr: UInt) = {
          val hi = addr >> log2Ceil(parameter.fetchBytes)
          hi(log2Ceil(bhtParameter.nEntries) - 1, 0) ^ (hi >> log2Ceil(bhtParameter.nEntries))(1, 0)
        }
        hashAddr(addr) ^ (hashHistory(history) << (log2Up(bhtParameter.nEntries) - bhtParameter.historyBits))
      }
      def get(addr: UInt): BHTResp = {
        val res = Wire(new BHTResp(Some(bhtParameter.historyLength), Some(bhtParameter.counterLength)))
        res.value := Mux(resetting, 0.U, table(index(addr, history)))
        res.history := history
        res
      }
      def updateTable(addr: UInt, d: BHTResp, taken: Bool): Unit = {
        wen := true.B
        when(!resetting) {
          waddr := index(addr, d.history)
          wdata := (bhtParameter.counterLength match {
            case 1 => taken
            case 2 => Cat(taken ^ d.value(0), d.value === 1.U || d.value(1) && taken)
          })
        }
      }
      def resetHistory(d: BHTResp): Unit = {
        history := d.history
      }
      def updateHistory(addr: UInt, d: BHTResp, taken: Bool): Unit = {
        history := Cat(taken, d.history >> 1)
      }
      def advanceHistory(taken: Bool): Unit = {
        history := Cat(taken, history >> 1)
      }

      private val table = Mem(bhtParameter.nEntries, UInt(bhtParameter.counterLength.W))
      val history = RegInit(0.U(bhtParameter.historyLength.W))

      private val reset_waddr = RegInit(0.U((log2Ceil(bhtParameter.nEntries) + 1).W))
      private val resetting = !reset_waddr(log2Ceil(bhtParameter.nEntries))
      private val wen = WireInit(resetting)
      private val waddr = WireInit(reset_waddr)
      private val wdata = WireInit(0.U)
      when(resetting) { reset_waddr := reset_waddr + 1.U }
      when(wen) { table(waddr) := wdata }
    }
    val bht = new BHT
    val isBranch = (idxHit & VecInit(cfiType.map(_ === CFIType.branch)).asUInt).orR
    val res = bht.get(io.req.bits.addr)
    def taken(bht: BHTResp): Bool = bht.value(0)
    when(io.bht_advance.valid) {
      bht.advanceHistory(taken(io.bht_advance.bits.bht))
    }
    when(io.bht_update.valid) {
      when(io.bht_update.bits.branch) {
        bht.updateTable(io.bht_update.bits.pc, io.bht_update.bits.prediction, io.bht_update.bits.taken)
        when(io.bht_update.bits.mispredict) {
          bht.updateHistory(io.bht_update.bits.pc, io.bht_update.bits.prediction, io.bht_update.bits.taken)
        }
      }.elsewhen(io.bht_update.bits.mispredict) {
        bht.resetHistory(io.bht_update.bits.prediction)
      }
    }
    when(!taken(res) && isBranch) { io.resp.bits.taken := false.B }
    io.resp.bits.bht := res
  }

  if (parameter.nRAS > 0) {
    class RAS {
      def push(addr: UInt): Unit = {
        when(count < parameter.nRAS.U) { count := count + 1.U }
        val nextPos = Mux(isPow2(parameter.nRAS).B || pos < (parameter.nRAS - 1).U, pos + 1.U, 0.U)
        stack(nextPos) := addr
        pos := nextPos
      }
      def peek: UInt = stack(pos)
      def pop(): Unit = when(!isEmpty) {
        count := count - 1.U
        pos := Mux((isPow2(parameter.nRAS)).B || pos > 0.U, pos - 1.U, (parameter.nRAS - 1).U)
      }
      def clear(): Unit = count := 0.U
      def isEmpty: Bool = count === 0.U

      private val count = RegInit(0.U(log2Up(parameter.nRAS + 1).W))
      private val pos = RegInit(0.U(log2Up(parameter.nRAS).W))
      private val stack = Reg(Vec(parameter.nRAS, UInt()))
    }
    val ras = new RAS
    val doPeek = (idxHit & VecInit(cfiType.map(_ === CFIType.ret)).asUInt).orR
    io.ras_head.valid := !ras.isEmpty
    io.ras_head.bits := ras.peek
    when(!ras.isEmpty && doPeek) {
      io.resp.bits.target := ras.peek
    }
    when(io.ras_update.valid) {
      when(io.ras_update.bits.cfiType === CFIType.call) {
        ras.push(io.ras_update.bits.returnAddr)
      }.elsewhen(io.ras_update.bits.cfiType === CFIType.ret) {
        ras.pop()
      }
    }
  }

}
