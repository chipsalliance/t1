// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.rocketv

import chisel3._
import chisel3.util._
import chisel3.util.random.LFSR

abstract class ReplacementPolicy {
  def nBits: Int
  def perSet: Boolean
  def way: UInt
  def miss: Unit
  def hit: Unit
  def access(touch_way: UInt): Unit
  def access(touch_ways: Seq[Valid[UInt]]): Unit
  def state_read: UInt
  def get_next_state(state: UInt, touch_way: UInt): UInt
  def get_next_state(state: UInt, touch_ways: Seq[Valid[UInt]]): UInt = {
    touch_ways.foldLeft(state)((prev, touch_way) => Mux(touch_way.valid, get_next_state(prev, touch_way.bits), prev))
  }
  def get_replace_way(state: UInt): UInt
}

object Random
{
  def apply(mod: Int, random: UInt): UInt = {
    if (isPow2(mod)) random(log2Ceil(mod)-1,0)
    else PriorityEncoder(partition(apply(1 << log2Up(mod*8), random), mod))
  }
  def apply(mod: Int): UInt = apply(mod, randomizer)
  def oneHot(mod: Int, random: UInt): UInt = {
    if (isPow2(mod)) UIntToOH(random(log2Up(mod)-1,0))
    else VecInit(PriorityEncoderOH(partition(apply(1 << log2Up(mod*8), random), mod))).asUInt
  }
  def oneHot(mod: Int): UInt = oneHot(mod, randomizer)

  private def randomizer = LFSR(16)
  private def partition(value: UInt, slices: Int) =
    Seq.tabulate(slices)(i => value < (((i + 1) << value.getWidth) / slices).U)
}

object ReplacementPolicy {
  def fromString(s: String, n_ways: Int): ReplacementPolicy = s.toLowerCase match {
    case "random" => new RandomReplacement(n_ways)
    case "lru"    => new TrueLRU(n_ways)
    case "plru"   => new PseudoLRU(n_ways)
    case t => throw new IllegalArgumentException(s"unknown Replacement Policy type $t")
  }
}

class RandomReplacement(n_ways: Int) extends ReplacementPolicy {
  private val replace = Wire(Bool())
  replace := false.B
  def nBits = 16
  def perSet = false
  private val lfsr = LFSR(nBits, replace)
  def state_read = WireDefault(lfsr)

  def way = Random(n_ways, lfsr)
  def miss = replace := true.B
  def hit = {}
  def access(touch_way: UInt) = {}
  def access(touch_ways: Seq[Valid[UInt]]) = {}
  def get_next_state(state: UInt, touch_way: UInt) = 0.U //DontCare
  def get_replace_way(state: UInt) = way
}

abstract class SeqReplacementPolicy {
  def access(set: UInt): Unit
  def update(valid: Bool, hit: Bool, set: UInt, way: UInt): Unit
  def way: UInt
}

abstract class SetAssocReplacementPolicy {
  def access(set: UInt, touch_way: UInt): Unit
  def access(sets: Seq[UInt], touch_ways: Seq[Valid[UInt]]): Unit
  def way(set: UInt): UInt
}

class SeqRandom(n_ways: Int) extends SeqReplacementPolicy {
  val logic = new RandomReplacement(n_ways)
  def access(set: UInt) = { }
  def update(valid: Bool, hit: Bool, set: UInt, way: UInt) = {
    when (valid && !hit) { logic.miss }
  }
  def way = logic.way
}

class TrueLRU(n_ways: Int) extends ReplacementPolicy {
  // True LRU replacement policy, using a triangular matrix to track which sets are more recently used than others.
  // The matrix is packed into a single UInt (or Bits).  Example 4-way (6-bits):
  // [5] - 3 more recent than 2
  // [4] - 3 more recent than 1
  // [3] - 2 more recent than 1
  // [2] - 3 more recent than 0
  // [1] - 2 more recent than 0
  // [0] - 1 more recent than 0
  def nBits = (n_ways * (n_ways-1)) / 2
  def perSet = true
  private val state_reg = RegInit(0.U(nBits.W))
  def state_read = WireDefault(state_reg)

  private def extractMRUVec(state: UInt): Seq[UInt] = {
    // Extract per-way information about which higher-indexed ways are more recently used
    val moreRecentVec = Wire(Vec(n_ways-1, UInt(n_ways.W)))
    var lsb = 0
    for (i <- 0 until n_ways-1) {
      moreRecentVec(i) := Cat(state(lsb+n_ways-i-2,lsb), 0.U((i+1).W))
      lsb = lsb + (n_ways - i - 1)
    }
    moreRecentVec
  }

  def get_next_state(state: UInt, touch_way: UInt): UInt = {
    val nextState     = Wire(Vec(n_ways-1, UInt(n_ways.W)))
    val moreRecentVec = extractMRUVec(state)  // reconstruct lower triangular matrix
    val wayDec        = UIntToOH(touch_way, n_ways)

    // Compute next value of triangular matrix
    // set the touched way as more recent than every other way
    nextState.zipWithIndex.foreach { case (e, i) =>
      e := Mux(i.U === touch_way, 0.U(n_ways.W), moreRecentVec(i) | wayDec)
    }

    nextState.zipWithIndex.tail.foldLeft((nextState.head.apply(n_ways-1,1),0)) { case ((pe,pi),(ce,ci)) => (Cat(ce.apply(n_ways-1,ci+1), pe), ci) }._1
  }

  def access(touch_way: UInt): Unit = {
    state_reg := get_next_state(state_reg, touch_way)
  }
  def access(touch_ways: Seq[Valid[UInt]]): Unit = {
    when (VecInit(touch_ways.map(_.valid)).asUInt.orR) {
      state_reg := get_next_state(state_reg, touch_ways)
    }
    //    for (i <- 1 until touch_ways.size) {
    //      cover(PopCount(touch_ways.map(_.valid)) === i.U, s"LRU_UpdateCount$i", s"LRU Update $i simultaneous")
    //    }
  }

  def get_replace_way(state: UInt): UInt = {
    val moreRecentVec = extractMRUVec(state)  // reconstruct lower triangular matrix
    // For each way, determine if all other ways are more recent
    val mruWayDec     = (0 until n_ways).map { i =>
      val upperMoreRecent = (if (i == n_ways-1) true.B else moreRecentVec(i).apply(n_ways-1,i+1).andR)
      val lowerMoreRecent = (if (i == 0)        true.B else moreRecentVec.map(e => !e(i)).reduce(_ && _))
      upperMoreRecent && lowerMoreRecent
    }
    OHToUInt(mruWayDec)
  }

  def way = get_replace_way(state_reg)
  def miss = access(way)
  def hit = {}
  @deprecated("replace 'replace' with 'way' from abstract class ReplacementPolicy","Rocket Chip 2020.05")
  def replace: UInt = way
}

class PseudoLRU(n_ways: Int) extends ReplacementPolicy {
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
    when (VecInit(touch_ways.map(_.valid)).asUInt.orR) {
      state_reg := get_next_state(state_reg, touch_ways)
    }
    //    for (i <- 1 until touch_ways.size) {
    //      cover(PopCount(touch_ways.map(_.valid)) === i.U, s"PLRU_UpdateCount$i", s"PLRU Update $i simultaneous")
    //    }
  }


  /** @param state state_reg bits for this sub-tree
    * @param touch_way touched way encoded value bits for this sub-tree
    * @param tree_nways number of ways in this sub-tree
    */
  def get_next_state(state: UInt, touch_way: UInt, tree_nways: Int): UInt = {
    require(state.getWidth == (tree_nways-1),                   s"wrong state bits width ${state.getWidth} for $tree_nways ways")
    require(touch_way.getWidth == (log2Ceil(tree_nways) max 1), s"wrong encoded way width ${touch_way.getWidth} for $tree_nways ways")

    if (tree_nways > 2) {
      // we are at a branching node in the tree, so recurse
      val right_nways: Int = 1 << (log2Ceil(tree_nways) - 1)  // number of ways in the right sub-tree
      val left_nways:  Int = tree_nways - right_nways         // number of ways in the left sub-tree
      val set_left_older      = !touch_way(log2Ceil(tree_nways)-1)
      val left_subtree_state  = if(tree_nways - 1 == right_nways) 0.U else state(tree_nways-3, right_nways-1)
      val right_subtree_state = state(right_nways-2, 0)

      if (left_nways > 1) {
        // we are at a branching node in the tree with both left and right sub-trees, so recurse both sub-trees
        Cat(set_left_older,
          Mux(set_left_older,
            left_subtree_state,  // if setting left sub-tree as older, do NOT recurse into left sub-tree
            get_next_state(left_subtree_state, touch_way(log2Ceil(left_nways)-1,0), left_nways)),  // recurse left if newer
          Mux(set_left_older,
            get_next_state(right_subtree_state, touch_way(log2Ceil(right_nways)-1,0), right_nways),  // recurse right if newer
            right_subtree_state))  // if setting right sub-tree as older, do NOT recurse into right sub-tree
      } else {
        // we are at a branching node in the tree with only a right sub-tree, so recurse only right sub-tree
        Cat(set_left_older,
          Mux(set_left_older,
            get_next_state(right_subtree_state, touch_way(log2Ceil(right_nways)-1,0), right_nways),  // recurse right if newer
            right_subtree_state))  // if setting right sub-tree as older, do NOT recurse into right sub-tree
      }
    } else if (tree_nways == 2) {
      // we are at a leaf node at the end of the tree, so set the single state bit opposite of the lsb of the touched way encoded value
      !touch_way(0)
    } else {  // tree_nways <= 1
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

    val touch_way_sized = if (touch_way.getWidth < log2Ceil(n_ways)) padTo(touch_way, log2Ceil(n_ways)) else touch_way(log2Ceil(n_ways)-1,0)
    get_next_state(state, touch_way_sized, n_ways)
  }


  /** @param state state_reg bits for this sub-tree
    * @param tree_nways number of ways in this sub-tree
    */
  def get_replace_way(state: UInt, tree_nways: Int): UInt = {
    require(state.getWidth == (tree_nways-1), s"wrong state bits width ${state.getWidth} for $tree_nways ways")

    // this algorithm recursively descends the binary tree, filling in the way-to-replace encoded value from msb to lsb
    if (tree_nways > 2) {
      // we are at a branching node in the tree, so recurse
      val right_nways: Int = 1 << (log2Ceil(tree_nways) - 1)  // number of ways in the right sub-tree
      val left_nways:  Int = tree_nways - right_nways         // number of ways in the left sub-tree
      val left_subtree_older  = state(tree_nways-2)
      val left_subtree_state  = if(tree_nways - 1 == right_nways) 0.U else state(tree_nways-3, right_nways-1)
      val right_subtree_state = state(right_nways-2, 0)

      if (left_nways > 1) {
        // we are at a branching node in the tree with both left and right sub-trees, so recurse both sub-trees
        Cat(left_subtree_older,      // return the top state bit (current tree node) as msb of the way-to-replace encoded value
          Mux(left_subtree_older,  // if left sub-tree is older, recurse left, else recurse right
            get_replace_way(left_subtree_state,  left_nways),    // recurse left
            get_replace_way(right_subtree_state, right_nways)))  // recurse right
      } else {
        // we are at a branching node in the tree with only a right sub-tree, so recurse only right sub-tree
        Cat(left_subtree_older,      // return the top state bit (current tree node) as msb of the way-to-replace encoded value
          Mux(left_subtree_older,  // if left sub-tree is older, return and do not recurse right
            0.U(1.W),
            get_replace_way(right_subtree_state, right_nways)))  // recurse right
      }
    } else if (tree_nways == 2) {
      // we are at a leaf node at the end of the tree, so just return the single state bit as lsb of the way-to-replace encoded value
      state(0)
    } else {  // tree_nways <= 1
      // we are at an empty node in an unbalanced tree for non-power-of-2 ways, so return single zero bit as lsb of the way-to-replace encoded value
      0.U(1.W)
    }
  }

  def get_replace_way(state: UInt): UInt = get_replace_way(state, n_ways)

  def way = get_replace_way(state_reg)
  def miss = access(way)
  def hit = {}
}

class SeqPLRU(n_sets: Int, n_ways: Int) extends SeqReplacementPolicy {
  val logic = new PseudoLRU(n_ways)
  val state = SyncReadMem(n_sets, UInt(logic.nBits.W))
  val current_state = Wire(UInt(logic.nBits.W))
  val next_state    = Wire(UInt(logic.nBits.W))
  val plru_way = logic.get_replace_way(current_state)

  def access(set: UInt) = {
    current_state := state.read(set)
  }

  def update(valid: Bool, hit: Bool, set: UInt, way: UInt) = {
    val update_way = Mux(hit, way, plru_way)
    next_state := logic.get_next_state(current_state, update_way)
    when (valid) { state.write(set, next_state) }
  }

  def way = plru_way
}

class SetAssocLRU(n_sets: Int, n_ways: Int, policy: String) extends SetAssocReplacementPolicy {
  val logic = policy.toLowerCase match {
    case "plru"  => new PseudoLRU(n_ways)
    case "lru"   => new TrueLRU(n_ways)
    case t => throw new IllegalArgumentException(s"unknown Replacement Policy type $t")
  }
  val state_vec =
    if (logic.nBits == 0) Reg(Vec(n_sets, UInt(logic.nBits.W))) // Work around elaboration error on following line
    else RegInit(VecInit(Seq.fill(n_sets)(0.U(logic.nBits.W))))

  def access(set: UInt, touch_way: UInt) = {
    state_vec(set) := logic.get_next_state(state_vec(set), touch_way)
  }

  def access(sets: Seq[UInt], touch_ways: Seq[Valid[UInt]]) = {
    require(sets.size == touch_ways.size, "internal consistency check: should be same number of simultaneous updates for sets and touch_ways")
    for (set <- 0 until n_sets) {
      val set_touch_ways = (sets zip touch_ways).map { case (touch_set, touch_way) =>
        Pipe(touch_way.valid && (touch_set === set.U), touch_way.bits, 0)}
      when (VecInit(set_touch_ways.map(_.valid)).asUInt.orR) {
        state_vec(set) := logic.get_next_state(state_vec(set), set_touch_ways)
      }
    }
  }

  def way(set: UInt) = logic.get_replace_way(state_vec(set))
}
