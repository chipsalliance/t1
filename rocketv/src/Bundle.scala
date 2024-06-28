// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.util.{Cat, log2Ceil}

// This file defines Bundle shared in the project.
// all Bundle only have datatype without any helper or functions, while they only exist in the companion Bundle.

// TODO: make it Enum
object PRV {
  val SZ = 2
  val U = 0
  val S = 1
  val H = 2
  val M = 3
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

object BP {
  def contextMatch(bp: BP, mcontext: UInt, scontext: UInt, xLen: Int, mcontextWidth: Int, scontextWidth: Int): Bool =
    (if (mcontextWidth > 0)
       !bp.textra.mselect || (mcontext(TExtra.mvalueBits(xLen, mcontextWidth) - 1, 0) === bp.textra.mvalue)
     else true.B) &&
      (if (scontextWidth > 0)
         !bp.textra.sselect || (scontext(TExtra.svalueBits(xLen, scontextWidth) - 1, 0) === bp.textra.svalue)
       else true.B)

  def addressMatch(bp: BP, x: UInt) = {
    def rangeAddressMatch(x: UInt) =
      (x >= bp.address) ^ bp.control.tmatch(0)

    def pow2AddressMatch(x: UInt): Bool = {
      def mask(): UInt = {
        import chisel3.experimental.conversions.seq2vec
        def maskMax = 4
        (0 until maskMax - 1).scanLeft(bp.control.tmatch(0))((m, i) => m && bp.address(i)).asUInt
      }
      (~x | mask()) === (~bp.address | mask())
    }
    Mux(bp.control.tmatch(1), rangeAddressMatch(x), pow2AddressMatch(x))
  }
}

class BP(xLen: Int, useBPWatch: Boolean, vaddrBits: Int, mcontextWidth: Int, scontextWidth: Int) extends Bundle {
  val control = new BPControl(xLen, useBPWatch)
  val address = UInt(vaddrBits.W)
  val textra = new TExtra(xLen, mcontextWidth, scontextWidth)
}

object BPControl {
  def enabled(bpControl: BPControl, mstatus: MStatus): Bool =
    !mstatus.debug && Cat(bpControl.m, bpControl.h, bpControl.s, bpControl.u)(mstatus.prv)
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

object TExtra {
  def mvalueBits(xLen: Int, mcontextWidth: Int): Int = if (xLen == 32) mcontextWidth.min(6) else mcontextWidth.min(13)
  def svalueBits(xLen: Int, scontextWidth: Int): Int = if (xLen == 32) scontextWidth.min(16) else scontextWidth.min(34)
  def mselectPos(xLen: Int): Int = if (xLen == 32) 25 else 50
  def mvaluePos(xLen:  Int):               Int = mselectPos(xLen) + 1
  def sselectPos: Int = 0
  def svaluePos:  Int = 2
}

class TExtra(xLen: Int, mcontextWidth: Int, scontextWidth: Int) extends Bundle {
  import TExtra._
  val mvalue = UInt(mvalueBits(xLen, mcontextWidth).W)
  val mselect = Bool()
  val pad2 = UInt((mselectPos(xLen) - svalueBits(xLen, scontextWidth) - 2).W)
  val svalue = UInt(svalueBits(xLen, scontextWidth).W)
  val pad1 = UInt(1.W)
  val sselect = Bool()
}

// originally in RocketChip, there is (n: Int) as parameter. this is designed for retire width,
// since Rocket is a single issue core, we removed it.
class BPWatch extends Bundle() {
  val valid = Bool()
  val rvalid = Bool()
  val wvalid = Bool()
  val ivalid = Bool()
  val action = UInt(3.W)
}

class BTBReq(vaddrBits: Int) extends Bundle {
  val addr = UInt(vaddrBits.W)
}

class BTBResp(
  vaddrBits:        Int,
  entries:          Int,
  bhtHistoryLength: Option[Int],
  bhtCounterLength: Option[Int])
    extends Bundle {
  def fetchWidth: Int = 1

  val cfiType = UInt(CFIType.width.W)
  val taken = Bool()
  val mask = UInt(fetchWidth.W)
  val bridx = UInt(log2Ceil(fetchWidth).W)
  val target = UInt(vaddrBits.W)
  val entry = UInt(log2Ceil(entries + 1).W)
  // @todo make it optional with bhtHistoryLength and bhtCounterLength
  val bht = new BHTResp(bhtHistoryLength, bhtCounterLength)
}

object BHTResp {
  def taken(bht: BHTResp): Bool = bht.value(0)
}

class BHTResp(bhtHistoryLength: Option[Int], bhtCounterLength: Option[Int]) extends Bundle {
  val history = UInt(bhtHistoryLength.getOrElse(1).W)
  val value = UInt(bhtCounterLength.getOrElse(1).W)

  // @todo: change to:
  //  val history = bhtHistoryLength.map(i => UInt(i.W))
  //  val value = bhtCounterLength.map(i => UInt(i.W))
}

class BTBUpdate(
  vaddrBits:        Int,
  entries:          Int,
  bhtHistoryLength: Option[Int],
  bhtCounterLength: Option[Int])
    extends Bundle {
  def fetchWidth: Int = 1

  val prediction = new BTBResp(vaddrBits, entries, bhtHistoryLength, bhtCounterLength)
  val pc = UInt(vaddrBits.W)
  val target = UInt(vaddrBits.W)
  val taken = Bool()
  val isValid = Bool()
  val br_pc = UInt(vaddrBits.W)
  val cfiType = UInt(CFIType.width.W)
}

class BHTUpdate(bhtHistoryLength: Option[Int], bhtCounterLength: Option[Int], vaddrBits: Int) extends Bundle {
  val prediction = new BHTResp(bhtHistoryLength, bhtCounterLength)
  val pc = UInt(vaddrBits.W)
  val branch = Bool()
  val taken = Bool()
  val mispredict = Bool()
}

class RASUpdate(vaddrBits: Int) extends Bundle {
  val cfiType = UInt(CFIType.width.W)
  val returnAddr = UInt(vaddrBits.W)
}

// TODO: make it Enum
object CFIType {
  def width = 2
  def branch = 0.U
  def jump = 1.U
  def call = 2.U
  def ret = 3.U
}
