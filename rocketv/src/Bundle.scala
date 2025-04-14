// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.util.{isPow2, log2Ceil, Cat, Decoupled, DecoupledIO, Valid, ValidIO}

// This file defines Bundle shared in the project.
// all Bundle only have datatype without any helper or functions, while they only exist in the companion Bundle.

// TODO: make it Enum
object PRV {
  val SZ = 2
  val U  = 0
  val S  = 1
  val H  = 2
  val M  = 3
}

class MStatus extends Bundle {
  // not truly part of mstatus, but convenient
  val debug = Bool()
  val cease = Bool()
  val wfi   = Bool()
  val isa   = UInt(32.W)

  val dprv = UInt(PRV.SZ.W) // effective prv for data accesses
  val dv   = Bool()         // effective v for data accesses
  val prv  = UInt(PRV.SZ.W)
  val v    = Bool()

  val sd      = Bool()
  val zero2   = UInt(23.W)
  val mpv     = Bool()
  val gva     = Bool()
  val mbe     = Bool()
  val sbe     = Bool()
  val sxl     = UInt(2.W)
  val uxl     = UInt(2.W)
  val sd_rv32 = Bool()
  val zero1   = UInt(8.W)
  val tsr     = Bool()
  val tw      = Bool()
  val tvm     = Bool()
  val mxr     = Bool()
  val sum     = Bool()
  val mprv    = Bool()
  val xs      = UInt(2.W)
  val fs      = UInt(2.W)
  val mpp     = UInt(2.W)
  val vs      = UInt(2.W)
  val spp     = UInt(1.W)
  val mpie    = Bool()
  val ube     = Bool()
  val spie    = Bool()
  val upie    = Bool()
  val mie     = Bool()
  val hie     = Bool()
  val sie     = Bool()
  val uie     = Bool()
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
  val textra  = new TExtra(xLen, mcontextWidth, scontextWidth)
}

object BPControl {
  def enabled(bpControl: BPControl, mstatus: MStatus): Bool =
    !mstatus.debug && Cat(bpControl.m, bpControl.h, bpControl.s, bpControl.u)(mstatus.prv)
}

class BPControl(xLen: Int, useBPWatch: Boolean) extends Bundle {
  val ttype    = UInt(4.W)
  val dmode    = Bool()
  val maskmax  = UInt(6.W)
  val reserved = UInt((xLen - (if (useBPWatch) 26 else 24)).W)
  val action   = UInt((if (useBPWatch) 3 else 1).W)
  val chain    = Bool()
  val zero     = UInt(2.W)
  val tmatch   = UInt(2.W)
  val m        = Bool()
  val h        = Bool()
  val s        = Bool()
  val u        = Bool()
  val x        = Bool()
  val w        = Bool()
  val r        = Bool()
}

object TExtra {
  def mvalueBits(xLen: Int, mcontextWidth: Int): Int = if (xLen == 32) mcontextWidth.min(6) else mcontextWidth.min(13)
  def svalueBits(xLen: Int, scontextWidth: Int): Int = if (xLen == 32) scontextWidth.min(16) else scontextWidth.min(34)
  def mselectPos(xLen: Int):                     Int = if (xLen == 32) 25 else 50
  def mvaluePos(xLen:  Int):                     Int = mselectPos(xLen) + 1
  def sselectPos:                                Int = 0
  def svaluePos:                                 Int = 2
}

class TExtra(xLen: Int, mcontextWidth: Int, scontextWidth: Int) extends Bundle {
  import TExtra._
  val mvalue  = UInt(mvalueBits(xLen, mcontextWidth).W)
  val mselect = Bool()
  val pad2    = UInt((mselectPos(xLen) - svalueBits(xLen, scontextWidth) - 2).W)
  val svalue  = UInt(svalueBits(xLen, scontextWidth).W)
  val pad1    = UInt(1.W)
  val sselect = Bool()
}

// originally in RocketChip, there is (n: Int) as parameter. this is designed for retire width,
// since Rocket is a single issue core, we removed it.
class BPWatch extends Bundle() {
  val valid  = Bool()
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
  fetchWidth:       Int,
  bhtHistoryLength: Option[Int],
  bhtCounterLength: Option[Int])
    extends Bundle {

  val cfiType = UInt(CFIType.width.W)
  val taken   = Bool()
  val mask    = UInt(fetchWidth.W)
  val bridx   = UInt(log2Ceil(fetchWidth).W)
  val target  = UInt(vaddrBits.W)
  val entry   = UInt(log2Ceil(entries + 1).W)
  // @todo make it optional with bhtHistoryLength and bhtCounterLength
  val bht     = new BHTResp(bhtHistoryLength, bhtCounterLength)
}

object BHTResp {
  def taken(bht:              BHTResp): Bool = bht.value(0)
  def strongly_taken(bhtResp: BHTResp): Bool = bhtResp.value === 1.U
}

class BHTResp(bhtHistoryLength: Option[Int], bhtCounterLength: Option[Int]) extends Bundle {
  val history = UInt(bhtHistoryLength.getOrElse(1).W)
  val value   = UInt(bhtCounterLength.getOrElse(1).W)

  // @todo: change to:
  //  val history = bhtHistoryLength.map(i => UInt(i.W))
  //  val value = bhtCounterLength.map(i => UInt(i.W))
}

class BTBUpdate(
  vaddrBits:        Int,
  entries:          Int,
  fetchWidth:       Int,
  bhtHistoryLength: Option[Int],
  bhtCounterLength: Option[Int])
    extends Bundle {
  def fetchWidth: Int = 1

  val prediction = new BTBResp(vaddrBits, entries, fetchWidth, bhtHistoryLength, bhtCounterLength)
  val pc         = UInt(vaddrBits.W)
  val target     = UInt(vaddrBits.W)
  val taken      = Bool()
  val isValid    = Bool()
  val br_pc      = UInt(vaddrBits.W)
  val cfiType    = UInt(CFIType.width.W)
}

class BHTUpdate(bhtHistoryLength: Option[Int], bhtCounterLength: Option[Int], vaddrBits: Int) extends Bundle {
  val prediction = new BHTResp(bhtHistoryLength, bhtCounterLength)
  val pc         = UInt(vaddrBits.W)
  val branch     = Bool()
  val taken      = Bool()
  val mispredict = Bool()
}

class RASUpdate(vaddrBits: Int) extends Bundle {
  val cfiType    = UInt(CFIType.width.W)
  val returnAddr = UInt(vaddrBits.W)
}

// TODO: make it Enum
object CFIType {
  def width  = 2
  def branch = 0.U
  def jump   = 1.U
  def call   = 2.U
  def ret    = 3.U
}

class CustomCSRIO(xLen: Int) extends Bundle {
  val ren   = Output(Bool())       // set by CSRFile, indicates an instruction is reading the CSR
  val wen   = Output(Bool())       // set by CSRFile, indicates an instruction is writing the CSR
  val wdata = Output(UInt(xLen.W)) // wdata provided by instruction writing CSR
  val value = Output(UInt(xLen.W)) // current value of CSR in CSRFile

  val stall = Input(Bool()) // reads and writes to this CSR should stall (must be bounded)

  val set   = Input(Bool()) // set/sdata enables external agents to set the value of this CSR
  val sdata = Input(UInt(xLen.W))
}

class CustomCSRs(xLen: Int) extends Bundle {
  val csrs = Vec(decls.size, new CustomCSRIO(xLen))

  // Not all cores have these CSRs, but those that do should follow the same
  // numbering conventions.  So we list them here but default them to None.
  protected def bpmCSRId = 0x7c0
  protected def bpmCSR: Option[CustomCSR] = None
  protected def chickenCSRId = 0x7c1
  protected def chickenCSR: Option[CustomCSR] = None
  // If you override this, you'll want to concatenate super.decls
  def decls:                Seq[CustomCSR]    = bpmCSR.toSeq ++ chickenCSR
  def flushBTB                       = getOrElse(bpmCSR, _.wen, false.B)
  def bpmStatic                      = getOrElse(bpmCSR, _.value(0), false.B)
  def disableDCacheClockGate         = getOrElse(chickenCSR, _.value(0), false.B)
  def disableICacheClockGate         = getOrElse(chickenCSR, _.value(1), false.B)
  def disableCoreClockGate           = getOrElse(chickenCSR, _.value(2), false.B)
  def disableSpeculativeICacheRefill = getOrElse(chickenCSR, _.value(3), false.B)
  def suppressCorruptOnGrantData     = getOrElse(chickenCSR, _.value(9), false.B)
  protected def getByIdOrElse[T](id: Int, f: CustomCSRIO => T, alt: T): T = {
    val idx = decls.indexWhere(_.id == id)
    if (idx < 0) alt else f(csrs(idx))
  }

  protected def getOrElse[T](csr: Option[CustomCSR], f: CustomCSRIO => T, alt: T): T =
    csr.map(c => getByIdOrElse(c.id, f, alt)).getOrElse(alt)
}

class TileInterrupts(usingSupervisor: Boolean, nLocalInterrupts: Int, usingNMI: Boolean, resetVectorLen: Int)
    extends Bundle {
  val debug: Bool         = Bool()
  val mtip:  Bool         = Bool()
  val msip:  Bool         = Bool()
  val meip:  Bool         = Bool()
  val seip:  Option[Bool] = Option.when(usingSupervisor)(Bool())
  val lip:   Vec[Bool]    = Vec(nLocalInterrupts, Bool())
  val nmi = Option.when(usingNMI)(new NMI(resetVectorLen))
}

class NMI(w: Int) extends Bundle {
  val rnmi                  = Bool()
  val rnmi_interrupt_vector = UInt(w.W)
  val rnmi_exception_vector = UInt(w.W)
}

class CoreInterrupts(
  usingSupervisor:  Boolean,
  nLocalInterrupts: Int,
  hasBeu:           Boolean,
  usingNMI:         Boolean,
  resetVectorLen:   Int)
    extends Bundle {
  val tileInterrupts = new TileInterrupts(usingSupervisor, nLocalInterrupts, usingNMI, resetVectorLen)
  val buserror       = Option.when(hasBeu)(Bool())
}

class HStatus extends Bundle {
  val zero6 = UInt(30.W)
  val vsxl  = UInt(2.W)
  val zero5 = UInt(9.W)
  val vtsr  = Bool()
  val vtw   = Bool()
  val vtvm  = Bool()
  val zero3 = UInt(2.W)
  val vgein = UInt(6.W)
  val zero2 = UInt(2.W)
  val hu    = Bool()
  val spvp  = Bool()
  val spv   = Bool()
  val gva   = Bool()
  val vsbe  = Bool()
  val zero1 = UInt(5.W)
}

class CSRDecodeIO(iLen: Int) extends Bundle {
  val inst                 = Input(UInt(iLen.W))
  val fpIllegal            = Output(Bool())
  val fpCsr                = Output(Bool())
  val readIllegal          = Output(Bool())
  val writeIllegal         = Output(Bool())
  val writeFlush           = Output(Bool())
  val systemIllegal        = Output(Bool())
  val virtualAccessIllegal = Output(Bool())
  val virtualSystemIllegal = Output(Bool())
}

object PTBR {
  def additionalPgLevels(ptbr: PTBR, pgLevels: Int, minPgLevels: Int) =
    ptbr.mode(log2Ceil(pgLevels - minPgLevels + 1) - 1, 0)
  def modeBits(xLen: Int)                                             = xLen match {
    case 32 => 1
    case 64 => 4
  }
  def maxASIdBits(xLen: Int)                                          = xLen match {
    case 32 => 9
    case 64 => 16
  }
}

class PTBR(xLen: Int, maxPAddrBits: Int, pgIdxBits: Int) extends Bundle {
  val mode: UInt = UInt(PTBR.modeBits(xLen).W)
  val asid = UInt(PTBR.maxASIdBits(xLen).W)
  val ppn  = UInt((maxPAddrBits - pgIdxBits).W)
}

// TODO: remove me.
object FPConstants {
  val RM_SZ    = 3
  val FLAGS_SZ = 5
}

object PMP {
  def lgAlign = 2
  private def UIntToOH1(x: UInt, width: Int): UInt = ~((-1).S(width.W).asUInt << x)(width - 1, 0)

  // For PMPReg
  def reset(pmp: PMP):                                            Unit = {
    pmp.cfg.a := 0.U
    pmp.cfg.l := 0.U
  }
  def readAddr(pmp: PMP, pmpGranularity: Int) =
    if (log2Ceil(pmpGranularity) == PMP.lgAlign)
      pmp.addr
    else {
      val mask = ((BigInt(1) << (log2Ceil(pmpGranularity) - PMP.lgAlign)) - 1).U
      Mux(napot(pmp), pmp.addr | (mask >> 1), ~(~pmp.addr | mask))
    }
  def napot(pmp:             PMP) = pmp.cfg.a(1)
  def napot(pmp:             PMPReg) = pmp.cfg.a(1)
  def torNotNAPOT(pmp:       PMP) = pmp.cfg.a(0)
  def tor(pmp:               PMP) = !napot(pmp) && torNotNAPOT(pmp)
  def cfgLocked(pmp:         PMP) = pmp.cfg.l
  def addrLocked(pmp:        PMP, next:           PMP) = cfgLocked(pmp) || cfgLocked(next) && tor(next)
  // PMP
  def computeMask(pmp: PMP, pmpGranularity: Int):                 UInt = {
    val base = Cat(pmp.addr, pmp.cfg.a(0)) | ((pmpGranularity - 1).U >> lgAlign)
    Cat(base & ~(base + 1.U), ((1 << lgAlign) - 1).U)
  }
  private def comparand(pmp: PMP, pmpGranularity: Int):           UInt = ~(~(pmp.addr << lgAlign) | (pmpGranularity - 1).U)

  private def pow2Match(pmp: PMP, x: UInt, lgSize: UInt, lgMaxSize: Int, pmpGranularity: Int): Bool = {
    def eval(a: UInt, b: UInt, m: UInt) = ((a ^ b) & ~m) === 0.U
    if (lgMaxSize <= log2Ceil(pmpGranularity)) {
      eval(x, comparand(pmp, pmpGranularity), pmp.mask)
    } else {
      // break up the circuit; the MSB part will be CSE'd
      val lsbMask = pmp.mask | UIntToOH1(lgSize, lgMaxSize)
      val msbMatch: Bool = eval(x >> lgMaxSize, comparand(pmp, pmpGranularity) >> lgMaxSize, pmp.mask >> lgMaxSize)
      val lsbMatch: Bool =
        eval(x(lgMaxSize - 1, 0), comparand(pmp, pmpGranularity)(lgMaxSize - 1, 0), lsbMask(lgMaxSize - 1, 0))
      msbMatch && lsbMatch
    }
  }

  private def boundMatch(pmp: PMP, x: UInt, lsbMask: UInt, lgMaxSize: Int, pmpGranularity: Int): Bool = {
    if (lgMaxSize <= log2Ceil(pmpGranularity)) {
      x < comparand(pmp, pmpGranularity)
    } else {
      // break up the circuit; the MSB part will be CSE'd
      val msbsLess:  Bool = (x >> lgMaxSize) < (comparand(pmp, pmpGranularity) >> lgMaxSize)
      val msbsEqual: Bool = ((x >> lgMaxSize) ^ (comparand(pmp, pmpGranularity) >> lgMaxSize)) === 0.U
      val lsbsLess:  Bool = (x(lgMaxSize - 1, 0) | lsbMask) < comparand(pmp, pmpGranularity)(lgMaxSize - 1, 0)
      msbsLess || (msbsEqual && lsbsLess)
    }
  }

  private def lowerBoundMatch(pmp: PMP, x: UInt, lgSize: UInt, lgMaxSize: Int, pmpGranularity: Int): Bool =
    !boundMatch(pmp: PMP, x, UIntToOH1(lgSize, lgMaxSize), lgMaxSize, pmpGranularity: Int)

  private def upperBoundMatch(pmp: PMP, x: UInt, lgMaxSize: Int, pmpGranularity: Int): Bool =
    boundMatch(pmp, x, 0.U, lgMaxSize, pmpGranularity)

  private def rangeMatch(pmp: PMP, x: UInt, lgSize: UInt, lgMaxSize: Int, prev: PMP, pmpGranularity: Int) =
    lowerBoundMatch(prev, x, lgSize, lgMaxSize, pmpGranularity) && upperBoundMatch(pmp, x, lgMaxSize, pmpGranularity)

  private def pow2Homogeneous(
    pmp:            PMP,
    x:              UInt,
    pgLevel:        UInt,
    paddrBits:      Int,
    pmpGranularity: Int,
    pgLevels:       Int,
    pgIdxBits:      Int,
    pgLevelBits:    Int
  ): Bool = {
    val maskHomogeneous = VecInit(pgLevelMap(pgLevels, pgIdxBits, pgLevelBits) { idxBits =>
      if (idxBits > paddrBits) false.B else pmp.mask(idxBits - 1)
    })(pgLevel)
    maskHomogeneous || VecInit(pgLevelMap(pgLevels, pgIdxBits, pgLevelBits) { idxBits =>
      ((x ^ comparand(pmp, pmpGranularity)) >> idxBits) =/= 0.U
    })(pgLevel)
  }

  private def pgLevelMap[T](pgLevels: Int, pgIdxBits: Int, pgLevelBits: Int)(f: Int => T): Seq[T] =
    (0 until pgLevels).map { i =>
      f(pgIdxBits + (pgLevels - 1 - i) * pgLevelBits)
    }

  private def rangeHomogeneous(
    pmp:            PMP,
    x:              UInt,
    pgLevel:        UInt,
    prev:           PMP,
    paddrBits:      Int,
    pmpGranularity: Int,
    pgLevels:       Int,
    pgIdxBits:      Int,
    pgLevelBits:    Int
  ) = {
    val beginsAfterLower = !(x < comparand(prev, pmpGranularity))
    val beginsAfterUpper = !(x < comparand(pmp, pmpGranularity))

    val pgMask          = VecInit(pgLevelMap(pgLevels, pgIdxBits, pgLevelBits) { idxBits =>
      (((BigInt(1) << paddrBits) - (BigInt(1) << idxBits)).max(0)).U
    })(pgLevel)
    val endsBeforeLower = (x & pgMask) < (comparand(prev, pmpGranularity) & pgMask)
    val endsBeforeUpper = (x & pgMask) < (comparand(pmp, pmpGranularity) & pgMask)

    endsBeforeLower || beginsAfterUpper || (beginsAfterLower && endsBeforeUpper)
  }

  // returns whether this PMP completely contains, or contains none of, a page
  def homogeneous(
    pmp:            PMP,
    x:              UInt,
    pgLevel:        UInt,
    prev:           PMP,
    paddrBits:      Int,
    pmpGranularity: Int,
    pgLevels:       Int,
    pgIdxBits:      Int,
    pgLevelBits:    Int
  ): Bool =
    Mux(
      napot(pmp),
      pow2Homogeneous(pmp, x, pgLevel, paddrBits, pmpGranularity, pgLevels, pgIdxBits, pgLevelBits),
      !torNotNAPOT(pmp) || rangeHomogeneous(
        pmp,
        x,
        pgLevel,
        prev,
        paddrBits,
        pmpGranularity,
        pgLevels,
        pgIdxBits,
        pgLevelBits
      )
    )

  // returns whether this matching PMP fully contains the access
  def aligned(pmp: PMP, x: UInt, lgSize: UInt, lgMaxSize: Int, prev: PMP, pmpGranularity: Int): Bool = if (
    lgMaxSize <= log2Ceil(pmpGranularity)
  ) true.B
  else {
    val lsbMask = UIntToOH1(lgSize, lgMaxSize)
    val straddlesLowerBound: Bool =
      ((x >> lgMaxSize) ^ (comparand(prev, pmpGranularity) >> lgMaxSize)) === 0.U &&
        (comparand(prev, pmpGranularity)(lgMaxSize - 1, 0) & ~x(lgMaxSize - 1, 0)) =/= 0.U
    val straddlesUpperBound: Bool =
      ((x >> lgMaxSize) ^ (comparand(pmp, pmpGranularity) >> lgMaxSize)) === 0.U &&
        (comparand(pmp, pmpGranularity)(lgMaxSize - 1, 0) & (x(lgMaxSize - 1, 0) | lsbMask)) =/= 0.U
    val rangeAligned = !(straddlesLowerBound || straddlesUpperBound)
    val pow2Aligned  = (lsbMask & ~pmp.mask(lgMaxSize - 1, 0)) === 0.U
    Mux(napot(pmp), pow2Aligned, rangeAligned)
  }

  // returns whether this PMP matches at least one byte of the access
  def hit(pmp: PMP, x: UInt, lgSize: UInt, lgMaxSize: Int, prev: PMP, pmpGranularity: Int): Bool =
    Mux(
      napot(pmp),
      pow2Match(pmp, x, lgSize, lgMaxSize, pmpGranularity),
      torNotNAPOT(pmp) && rangeMatch(pmp, x, lgSize, lgMaxSize, prev, pmpGranularity)
    )

}

class PMP(paddrBits: Int) extends Bundle {
  val mask = UInt(paddrBits.W)
  val cfg  = new PMPConfig
  val addr = UInt((paddrBits - PMP.lgAlign).W)
}

class PMPConfig extends Bundle {
  val l   = Bool()
  val res = UInt(2.W)
  val a   = UInt(2.W)
  val x   = Bool()
  val w   = Bool()
  val r   = Bool()
}

class PerfCounterIO(xLen: Int, retireWidth: Int) extends Bundle {
  val eventSel = Output(UInt(xLen.W))
  val inc      = Input(UInt(log2Ceil(1 + retireWidth).W))
}

class Envcfg extends Bundle {
  val stce   = Bool() // only for menvcfg/henvcfg
  val pbmte  = Bool() // only for menvcfg/henvcfg
  val zero54 = UInt(54.W)
  val cbze   = Bool()
  val cbcfe  = Bool()
  val cbie   = UInt(2.W)
  val zero3  = UInt(3.W)
  val fiom   = Bool()
}

class DCSR extends Bundle {
  val xdebugver = UInt(2.W)
  val zero4     = UInt(2.W)
  val zero3     = UInt(12.W)
  val ebreakm   = Bool()
  val ebreakh   = Bool()
  val ebreaks   = Bool()
  val ebreaku   = Bool()
  val zero2     = Bool()
  val stopcycle = Bool()
  val stoptime  = Bool()
  val cause     = UInt(3.W)
  val v         = Bool()
  val zero1     = UInt(2.W)
  val step      = Bool()
  val prv       = UInt(PRV.SZ.W)
}

class VCSR extends Bundle {
  val vtype:  UInt = UInt(32.W)
  val vl:     UInt = UInt(32.W)
  val vcsr:   UInt = UInt(32.W)
  val vstart: UInt = UInt(32.W)
}

class MIP(nLocalInterrupts: Int) extends Bundle {
  val lip   = Vec(nLocalInterrupts, Bool())
  val zero1 = Bool()
  val debug = Bool() // keep in sync with CSR.debugIntCause
  val sgeip = Bool()
  val meip  = Bool()
  val vseip = Bool()
  val seip  = Bool()
  val ueip  = Bool()
  val mtip  = Bool()
  val vstip = Bool()
  val stip  = Bool()
  val utip  = Bool()
  val msip  = Bool()
  val vssip = Bool()
  val ssip  = Bool()
  val usip  = Bool()
}

object PMPReg {
  def napot(pmp: PMPReg) = pmp.cfg.a(1)
}

class PMPReg(paddrBits: Int) extends Bundle {
  val cfg  = new PMPConfig
  val addr = UInt((paddrBits - PMP.lgAlign).W)
}

class MNStatus extends Bundle {
  val mpp   = UInt(2.W)
  val zero3 = UInt(3.W)
  val mpv   = Bool()
  val zero2 = UInt(3.W)
  val mie   = Bool()
  val zero1 = UInt(3.W)
}

class ExpandedInstruction extends Bundle {
  val bits = UInt(32.W)
  val rd   = UInt(5.W)
  val rs1  = UInt(5.W)
  val rs2  = UInt(5.W)
  val rs3  = UInt(5.W)
}

class FrontendResp(
  vaddrBits:         Int,
  entries:           Int,
  bhtHistoryLength:  Option[Int],
  bhtCounterLength:  Option[Int],
  vaddrBitsExtended: Int,
  coreInstBits:      Int,
  fetchWidth:        Int)
    extends Bundle {
  val btb    = new BTBResp(vaddrBits, entries, fetchWidth, bhtHistoryLength: Option[Int], bhtCounterLength: Option[Int])
  val pc     = UInt(vaddrBitsExtended.W) // ID stage PC
  val data   = UInt((fetchWidth * coreInstBits).W)
  val mask   = UInt(fetchWidth.W)
  val xcpt   = new FrontendExceptions
  val replay = Bool()
}

class FrontendExceptions extends Bundle {
  val pf = Bool()
  val gf = Bool()
  val ae = Bool()
}

class Instruction extends Bundle {
  val xcpt0  = new FrontendExceptions // exceptions on first half of instruction
  val xcpt1  = new FrontendExceptions // exceptions on second half of instruction
  val replay = Bool()
  val rvc    = Bool()
  val inst   = new ExpandedInstruction
  val raw    = UInt(32.W)
}

class MultiplierReq(dataBits: Int, tagBits: Int, uopWidth: Int) extends Bundle {
  val fn  = Bits(uopWidth.W)
  val dw  = Bool()
  val in1 = Bits(dataBits.W)
  val in2 = Bits(dataBits.W)
  val tag = UInt(tagBits.W)
}

class MultiplierResp(dataBits: Int, tagBits: Int) extends Bundle {
  val data      = Bits(dataBits.W)
  val full_data = Bits((2 * dataBits).W)
  val tag       = UInt(tagBits.W)
}

class PMACheckerResponse extends Bundle {
  val cacheable = Bool()
  val r         = Bool()
  val w         = Bool()
  val pp        = Bool()
  val al        = Bool()
  val aa        = Bool()
  val x         = Bool()
  val eff       = Bool()
}

/** IO between TLB and PTW
  *
  * PTW receives :
  *   - PTE request
  *   - CSRs info
  *   - pmp results from PMP(in TLB)
  */
class TLBPTWIO(
  nPMPs:        Int,
  vpnBits:      Int,
  paddrBits:    Int,
  vaddrBits:    Int,
  pgLevels:     Int,
  xLen:         Int,
  maxPAddrBits: Int,
  pgIdxBits:    Int)
    extends Bundle {
  val req     = Decoupled(Valid(new PTWReq(vpnBits)))
  val resp    = Flipped(Valid(new PTWResp(vaddrBits, pgLevels)))
  val ptbr    = Input(new PTBR(xLen, maxPAddrBits, pgIdxBits))
  val hgatp   = Input(new PTBR(xLen, maxPAddrBits, pgIdxBits))
  val vsatp   = Input(new PTBR(xLen, maxPAddrBits, pgIdxBits))
  val status  = Input(new MStatus)
  val hstatus = Input(new HStatus)
  val gstatus = Input(new MStatus)
  val pmp     = Input(Vec(nPMPs, new PMP(paddrBits)))
  // No customCSR for the first time refactor.
  //  val customCSRs = Flipped(coreParams.customCSRs)
}

class PTWReq(vpnBits: Int) extends Bundle {
  val addr     = UInt(vpnBits.W)
  val need_gpa = Bool()
  val vstage1  = Bool()
  val stage2   = Bool()
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
  val gpa         = Valid(UInt(vaddrBits.W))
  val gpa_is_pte  = Bool()
}

object PTE {

  /** return true if find a pointer to next level page table */
  def table(pte: PTE) =
    pte.v && !pte.r && !pte.w && !pte.x && !pte.d && !pte.a && !pte.u && pte.reserved_for_future === 0.U

  /** return true if find a leaf PTE */
  def leaf(pte: PTE) = pte.v && (pte.r || (pte.x && !pte.w)) && pte.a

  /** user read */
  def ur(pte: PTE) = sr(pte) && pte.u

  /** user write */
  def uw(pte: PTE) = sw(pte) && pte.u

  /** user execute */
  def ux(pte: PTE) = sx(pte) && pte.u

  /** supervisor read */
  def sr(pte: PTE) = leaf(pte) && pte.r

  /** supervisor write */
  def sw(pte: PTE) = leaf(pte) && pte.w && pte.d

  /** supervisor execute */
  def sx(pte: PTE) = leaf(pte) && pte.x

  /** full permission: writable and executable in user mode */
  def isFullPerm(pte: PTE) = uw(pte) && ux(pte)
}

/** PTE template for transmission
  *
  * contains useful methods to check PTE attributes
  * @see
  *   RV-priv spec 4.3.1 for pgae table entry format
  */
class PTE extends Bundle {
  val reserved_for_future   = UInt(10.W)
  val ppn                   = UInt(44.W)
  val reserved_for_software = UInt(2.W)

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
  val req               = Decoupled(
    new HellaCacheReq(coreMaxAddrBits, usingVM, untagBits, pgIdxBits, dcacheReqTagBits, dcacheArbPorts, coreDataBytes)
  )
  val s1_kill           = Output(Bool())                                 // kill previous cycle's req
  val s1_data           = Output(new HellaCacheWriteData(coreDataBytes)) // data for previous cycle's req
  val s2_nack           = Input(Bool())                                  // req from two cycles ago is rejected
  val s2_nack_cause_raw = Input(Bool())                                  // reason for nack is store-load RAW hazard (performance hint)
  val s2_kill           = Output(Bool())                                 // kill req from two cycles ago
  val s2_uncached       = Input(Bool())                                  // advisory signal that the access is MMIO
  val s2_paddr          = Input(UInt(paddrBits.W))                       // translated address

  val resp          = Flipped(
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
  val replay_next   = Input(Bool())
  val s2_xcpt       = Input(new HellaCacheExceptions)
  val s2_gpa        = Input(UInt(vaddrBitsExtended.W))
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
  val ordered       = Input(Bool())
  val perf          = Input(new HellaCachePerfEvents())

  val keep_clock_enabled = Output(Bool()) // should D$ avoid clock-gating itself?
  val clock_enabled      = Input(Bool())  // is D$ currently being clocked?
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
  val M_SZ = 5

  val phys     = Bool()
  val no_alloc = Bool()
  val no_xcpt  = Bool()

  val addr   = UInt(coreMaxAddrBits.W)
  val idx    = Option.when(usingVM && untagBits > pgIdxBits)(UInt(coreMaxAddrBits.W))
  val tag    = UInt((dcacheReqTagBits + log2Ceil(dcacheArbPorts)).W)
  // TODO: handle this uop
  val cmd    = UInt(M_SZ.W)
  val size   = UInt(log2Ceil(log2Ceil(coreDataBytes) + 1).W)
  val signed = Bool()
  // TODO: handle this uop
  val dprv   = UInt(PRV.SZ.W)
  val dv     = Bool()

  val data = UInt(coreDataBits.W)
  val mask = UInt(coreDataBytes.W)
}

class HellaCacheWriteData(coreDataBytes: Int) extends Bundle {
  require(isPow2(coreDataBytes))
  val coreDataBits: Int = coreDataBytes * 8

  val data = UInt(coreDataBits.W)
  val mask = UInt(coreDataBytes.W)
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
  val M_SZ = 5

  val replay           = Bool()
  val has_data         = Bool()
  val data_word_bypass = UInt(coreDataBits.W)
  val data_raw         = UInt(coreDataBits.W)
  val store_data       = UInt(coreDataBits.W)

  val addr   = UInt(coreMaxAddrBits.W)
  val idx    = Option.when(usingVM && untagBits > pgIdxBits)(UInt(coreMaxAddrBits.W))
  val tag    = UInt((dcacheReqTagBits + log2Ceil(dcacheArbPorts)).W)
  val cmd    = UInt(M_SZ.W)
  val size   = UInt(log2Ceil(log2Ceil(coreDataBytes) + 1).W)
  val signed = Bool()
  val dprv   = UInt(PRV.SZ.W)
  val dv     = Bool()

  val data = UInt(coreDataBits.W)
  val mask = UInt(coreDataBytes.W)
}

class HellaCacheExceptions extends Bundle {
  val ma = new AlignmentExceptions
  val pf = new AlignmentExceptions
  val gf = new AlignmentExceptions
  val ae = new AlignmentExceptions
}

class AlignmentExceptions extends Bundle {
  val ld = Bool()
  val st = Bool()
}

class HellaCachePerfEvents extends Bundle {
  val acquire                    = Bool()
  val release                    = Bool()
  val grant                      = Bool()
  val tlbMiss                    = Bool()
  val blocked                    = Bool()
  val canAcceptStoreThenLoad     = Bool()
  val canAcceptStoreThenRMW      = Bool()
  val canAcceptLoadThenLoad      = Bool()
  val storeBufferEmptyAfterLoad  = Bool()
  val storeBufferEmptyAfterStore = Bool()
}

class DatapathPTWIO(
  xLen:         Int,
  maxPAddrBits: Int,
  pgIdxBits:    Int,
  vaddrBits:    Int,
  asidBits:     Int,
  nPMPs:        Int,
  paddrBits:    Int)
    extends Bundle {
  val ptbr    = Input(new PTBR(xLen, maxPAddrBits, pgIdxBits))
  val hgatp   = Input(new PTBR(xLen, maxPAddrBits, pgIdxBits))
  val vsatp   = Input(new PTBR(xLen, maxPAddrBits, pgIdxBits))
  val sfence  = Flipped(Valid(new SFenceReq(vaddrBits, asidBits)))
  val status  = Input(new MStatus())
  val hstatus = Input(new HStatus())
  val gstatus = Input(new MStatus())
  val pmp     = Input(Vec(nPMPs, new PMP(paddrBits)))
  val perf    = Output(new PTWPerfEvents())
  // No customCSR for the first time refactor.
  // val customCSRs = Flipped(coreParams.customCSRs)

  /** enable clock generated by ptw */
  val clock_enabled = Output(Bool())
}

class SFenceReq(vaddrBits: Int, asidBits: Int) extends Bundle {
  val rs1  = Bool()
  val rs2  = Bool()
  val addr = UInt(vaddrBits.W)
  val asid = UInt(asidBits.W)
  val hv   = Bool()
  val hg   = Bool()
}

class PTWPerfEvents extends Bundle {
  val l2miss   = Bool()
  val l2hit    = Bool()
  val pte_miss = Bool()
  val pte_hit  = Bool()
}

/** L2TLB PTE template
  *
  * contains tag bits
  * @param nSets
  *   number of sets in L2TLB
  * @see
  *   RV-priv spec 4.3.1 for page table entry format
  */
class L2TLBEntry(nSets: Int, ppnBits: Int, maxSVAddrBits: Int, pgIdxBits: Int, usingHypervisor: Boolean)
    extends Bundle {
  val idxBits = log2Ceil(nSets)
  val tagBits = maxSVAddrBits - pgIdxBits - idxBits + (if (usingHypervisor) 1 else 0)
  val tag     = UInt(tagBits.W)
  val ppn     = UInt(ppnBits.W)

  /** dirty bit */
  val d = Bool()

  /** access bit */
  val a = Bool()

  /** user mode accessible */
  val u = Bool()

  /** whether the page is executable */
  val x = Bool()

  /** whether the page is writable */
  val w = Bool()

  /** whether the page is readable */
  val r = Bool()
}

class ICacheReq(vaddrBits: Int) extends Bundle {
  val addr = UInt(vaddrBits.W)
}

class ICacheResp(fetchBytes: Int) extends Bundle {

  /** data to CPU.
    * @todo
    *   why 4 instructions?
    */
  val data = UInt((fetchBytes * 8).W)

  /** ask CPU to replay fetch when tag or data ECC error happened. */
  val replay = Bool()

  /** access exception: indicate CPU an tag ECC error happened. if [[outer.icacheParams.latency]] is 1, tie 0.
    */
  val ae = Bool()

}

class ICacheErrors(hasCorrectable: Boolean, hasUncorrectable: Boolean, paddrBits: Int) extends Bundle {
  val correctable   = Option.when(hasCorrectable)(Valid(UInt(paddrBits.W)))
  val uncorrectable = Option.when(hasUncorrectable)(Valid(UInt(paddrBits.W)))
  val bus           = Valid(UInt(paddrBits.W))
}

class ICachePerfEvents extends Bundle {
  val acquire = Bool()
}

class FPInput(fLen: Int) extends Bundle {
  val fpuControl = new FPUCtrlSigs
  val rm         = UInt(FPConstants.RM_SZ.W)
  val fmaCmd     = UInt(2.W)
  val typ        = UInt(2.W)
  val fmt        = UInt(2.W)
  val in1        = UInt((fLen + 1).W)
  val in2        = UInt((fLen + 1).W)
  val in3        = UInt((fLen + 1).W)
}

// @todo DecodeBundle
class FPUCtrlSigs extends Bundle {
  val ldst       = Bool()
  val wen        = Bool()
  val ren1       = Bool()
  val ren2       = Bool()
  val ren3       = Bool()
  val swap12     = Bool()
  val swap23     = Bool()
  val typeTagIn  = UInt(2.W)
  val typeTagOut = UInt(2.W)
  val fromint    = Bool()
  val toint      = Bool()
  val fastpipe   = Bool()
  val fma        = Bool()
  val div        = Bool()
  val sqrt       = Bool()
  val wflags     = Bool()
}

class FPResult(fLen: Int) extends Bundle {
  val data = UInt((fLen + 1).W)
  val exc  = UInt(FPConstants.FLAGS_SZ.W)
}

class FPToIntOutput(fLen: Int, xLen: Int) extends Bundle {
  val in    = new FPInput(fLen)
  val lt    = Bool()
  val store = UInt(fLen.W)
  val toint = UInt(xLen.W)
  val exc   = UInt(FPConstants.FLAGS_SZ.W)
}

class IntToFPInput(xLen: Int) extends Bundle {
  val fpuControl = new FPUCtrlSigs
  val rm         = UInt(FPConstants.RM_SZ.W)
  val typ        = UInt(2.W)
  val in1        = UInt(xLen.W)
}

class FPUCoreIO(hartIdLen: Int, xLen: Int, fLen: Int) extends Bundle {
  val hartid = Input(UInt(hartIdLen.W))
  val time   = Input(UInt(xLen.W))

  val inst         = Input(UInt(32.W))
  val fromint_data = Input(UInt(xLen.W))

  val fcsr_rm    = Input(UInt(FPConstants.RM_SZ.W))
  val fcsr_flags = Valid(UInt(FPConstants.FLAGS_SZ.W))

  val store_data = Output(UInt(fLen.W))
  val toint_data = Output(UInt(xLen.W))

  val dmem_resp_val  = Input(Bool())
  val dmem_resp_type = Input(UInt(3.W))
  val dmem_resp_tag  = Input(UInt(5.W))
  val dmem_resp_data = Input(UInt(fLen.W))

  val valid       = Input(Bool())
  val fcsr_rdy    = Output(Bool())
  val nack_mem    = Output(Bool())
  val illegal_rm  = Output(Bool())
  val killx       = Input(Bool())
  val killm       = Input(Bool())
  val dec         = Input(new FPUCtrlSigs())
  val sboard_set  = Output(Bool())
  val sboard_clr  = Output(Bool())
  val sboard_clra = Output(UInt(5.W))

  val keep_clock_enabled = Input(Bool())
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
  val cmd = UInt(M_SZ.W)
  val prv = UInt(PRV.SZ.W)

  /** virtualization mode */
  val v = Bool()

}

class TLBResp(paddrBits: Int, vaddrBitsExtended: Int) extends Bundle {
  // lookup responses
  val miss = Bool()

  /** physical address */
  val paddr      = UInt(paddrBits.W)
  val gpa        = UInt(vaddrBitsExtended.W)
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
  val ld   = Bool()
  val st   = Bool()
  val inst = Bool()
}

object TLBEntry {

  /** returns all entry data in this entry */
  def entry_data(tlbEntry: TLBEntry) = tlbEntry.data.map(_.asTypeOf(new TLBEntryData(tlbEntry.ppnBits)))

  /** returns the index of sector */
  private def sectorIdx(tlbEntry: TLBEntry, vpn: UInt) = vpn(log2Ceil(tlbEntry.nSectors) - 1, 0)

  /** returns the entry data matched with this vpn */
  def getData(tlbEntry: TLBEntry, vpn: UInt) =
    tlbEntry.data(sectorIdx(tlbEntry, vpn)).asTypeOf(new TLBEntryData(tlbEntry.ppnBits))

  /** returns whether a sector hits */
  def sectorHit(tlbEntry: TLBEntry, vpn: UInt, virtual: Bool) =
    tlbEntry.valid.asUInt.orR && sectorTagMatch(tlbEntry, vpn, virtual)

  /** returns whether tag matches vpn */
  def sectorTagMatch(tlbEntry: TLBEntry, vpn: UInt, virtual: Bool) =
    (((tlbEntry.tag_vpn ^ vpn) >> log2Ceil(tlbEntry.nSectors)) === 0.U) && (tlbEntry.tag_v === virtual)

  /** returns hit signal */
  def hit(
    tlbEntry:                TLBEntry,
    vpn:                     UInt,
    virtual:                 Bool,
    usingVM:                 Boolean,
    pgLevelBits:             Int,
    hypervisorExtraAddrBits: Int,
    superpage:               Boolean,
    superpageOnly:           Boolean
  ): Bool = {
    if (superpage && usingVM) {
      var tagMatch = tlbEntry.valid.head && (tlbEntry.tag_v === virtual)
      for (j <- 0 until tlbEntry.pgLevels) {
        val base   = (tlbEntry.pgLevels - 1 - j) * pgLevelBits
        val n      = pgLevelBits + (if (j == 0) hypervisorExtraAddrBits else 0)
        val ignore = tlbEntry.level < j.U || (superpageOnly && (j == (tlbEntry.pgLevels - 1))).B
        tagMatch = tagMatch && (ignore || (tlbEntry.tag_vpn ^ vpn)(base + n - 1, base) === 0.U)
      }
      tagMatch
    } else {
      val idx = sectorIdx(tlbEntry, vpn)
      tlbEntry.valid(idx) && sectorTagMatch(tlbEntry, vpn, virtual)
    }
  }

  /** returns the ppn of the input TLBEntryData */
  def ppn(
    tlbEntry:      TLBEntry,
    vpn:           UInt,
    data:          TLBEntryData,
    usingVM:       Boolean,
    pgLevelBits:   Int,
    superpage:     Boolean,
    superpageOnly: Boolean
  ) = {
    val supervisorVPNBits = tlbEntry.pgLevels * pgLevelBits
    if (superpage && usingVM) {
      var res = data.ppn >> pgLevelBits * (tlbEntry.pgLevels - 1)
      for (j <- 1 until tlbEntry.pgLevels) {
        val ignore = tlbEntry.level < j.U || (superpageOnly && j == tlbEntry.pgLevels - 1).B
        res = Cat(
          res,
          (Mux(ignore, vpn, 0.U) | data.ppn)(
            supervisorVPNBits - j * pgLevelBits - 1,
            supervisorVPNBits - (j + 1) * pgLevelBits
          )
        )
      }
      res
    } else {
      data.ppn
    }
  }

  /** does the refill
    *
    * find the target entry with vpn tag and replace the target entry with the input entry data
    */
  def insert(tlbEntry: TLBEntry, vpn: UInt, virtual: Bool, level: UInt, entry: TLBEntryData, superpageOnly: Boolean)
    : Unit = {
    tlbEntry.tag_vpn := vpn
    tlbEntry.tag_v   := virtual
    tlbEntry.level   := level(log2Ceil(tlbEntry.pgLevels - (if (superpageOnly) 1 else 0)) - 1, 0)

    val idx = sectorIdx(tlbEntry, vpn)
    tlbEntry.valid(idx) := true.B
    tlbEntry.data(idx)  := entry.asUInt
  }

  def invalidate(tlbEntry: TLBEntry):                         Unit = { tlbEntry.valid.foreach(_ := false.B) }
  def invalidate(tlbEntry: TLBEntry, virtual: Bool):          Unit = {
    for ((v, e) <- tlbEntry.valid.zip(entry_data(tlbEntry)))
      when(tlbEntry.tag_v === virtual) { v := false.B }
  }
  def invalidateVPN(
    tlbEntry:                TLBEntry,
    vpn:                     UInt,
    virtual:                 Bool,
    usingVM:                 Boolean,
    pgLevelBits:             Int,
    hypervisorExtraAddrBits: Int,
    superpage:               Boolean,
    superpageOnly:           Boolean
  ): Unit = {
    if (superpage) {
      when(hit(tlbEntry, vpn, virtual, usingVM, pgLevelBits, hypervisorExtraAddrBits, superpage, superpageOnly)) {
        invalidate(tlbEntry)
      }
    } else {
      when(sectorTagMatch(tlbEntry, vpn, virtual)) {
        for (((v, e), i) <- (tlbEntry.valid.zip(entry_data(tlbEntry))).zipWithIndex)
          when(tlbEntry.tag_v === virtual && i.U === sectorIdx(tlbEntry, vpn)) { v := false.B }
      }
    }
    // For fragmented superpage mappings, we assume the worst (largest)
    // case, and zap entries whose most-significant VPNs match
    when(((tlbEntry.tag_vpn ^ vpn) >> (pgLevelBits * (tlbEntry.pgLevels - 1))) === 0.U) {
      for ((v, e) <- tlbEntry.valid.zip(entry_data(tlbEntry)))
        when(tlbEntry.tag_v === virtual && e.fragmented_superpage) { v := false.B }
    }
  }
  def invalidateNonGlobal(tlbEntry: TLBEntry, virtual: Bool): Unit = {
    for ((v, e) <- tlbEntry.valid.zip(entry_data(tlbEntry)))
      when(tlbEntry.tag_v === virtual && !e.g) { v := false.B }
  }
}

class TLBEntry(val nSectors: Int, val pgLevels: Int, vpnBits: Int, val ppnBits: Int) extends Bundle {

  val level = UInt(log2Ceil(pgLevels).W)

  /** use vpn as tag */
  val tag_vpn = UInt(vpnBits.W)

  /** tag in vitualization mode */
  val tag_v = Bool()

  /** entry data */
  val data = Vec(nSectors, UInt(new TLBEntryData(ppnBits).getWidth.W))

  /** valid bit */
  val valid = Vec(nSectors, Bool())
}

class TLBEntryData(ppnBits: Int) extends Bundle {
  val ppn = UInt(ppnBits.W)

  /** pte.u user */
  val u = Bool()

  /** pte.g global */
  val g = Bool()

  /** access exception. D$ -> PTW -> TLB AE Alignment failed.
    */
  val ae_ptw    = Bool()
  val ae_final  = Bool()
  val ae_stage2 = Bool()

  /** page fault */
  val pf = Bool()

  /** guest page fault */
  val gf = Bool()

  /** supervisor write */
  val sw = Bool()

  /** supervisor execute */
  val sx = Bool()

  /** supervisor read */
  val sr = Bool()

  /** hypervisor write */
  val hw = Bool()

  /** hypervisor excute */
  val hx = Bool()

  /** hypervisor read */
  val hr = Bool()

  /** prot_w */
  val pw = Bool()

  /** prot_x */
  val px = Bool()

  /** prot_r */
  val pr = Bool()

  /** PutPartial */
  val ppp = Bool()

  /** AMO logical */
  val pal = Bool()

  /** AMO arithmetic */
  val paa = Bool()

  /** get/put effects */
  val eff = Bool()

  /** cacheable */
  val c = Bool()

  /** fragmented_superpage support */
  val fragmented_superpage = Bool()
}

class DCacheErrors(hasCorrectable: Boolean, hasUncorrectable: Boolean, paddrBits: Int) extends Bundle {
  val correctable:   Option[Valid[UInt]] = Option.when(hasCorrectable)(Valid(UInt(paddrBits.W)))
  val uncorrectable: Option[Valid[UInt]] = Option.when(hasUncorrectable)(Valid(UInt(paddrBits.W)))
  val bus:           Valid[UInt]         = Valid(UInt(paddrBits.W))
}

class DCacheTLBPort(paddrBits: Int, vaddrBitsExtended: Int) extends Bundle {
  val req:     DecoupledIO[TLBReq] = Flipped(Decoupled(new TLBReq(paddrBits, vaddrBitsExtended)))
  val s1_resp: TLBResp             = Output(new TLBResp(paddrBits, vaddrBitsExtended))
  val s2_kill: Bool                = Input(Bool())
}

object ClientStates {
  val width = 2

  def Nothing = 0.U(width.W)
  def Branch  = 1.U(width.W)
  def Trunk   = 2.U(width.W)
  def Dirty   = 3.U(width.W)

  def hasReadPermission(state:  UInt): Bool = state > Nothing
  def hasWritePermission(state: UInt): Bool = state > Branch
}

class ClientMetadata extends Bundle {

  /** Actual state information stored in this bundle */
  val state = UInt(ClientStates.width.W)
}

class L1Metadata(tagBits: Int) extends Bundle {
  val coh = new ClientMetadata
  val tag = UInt(tagBits.W)
}

class DCacheMetadataReq(vaddrBitsExtended: Int, idxBits: Int, nWays: Int, dataWidth: Int) extends Bundle {
  val write:  Bool = Bool()
  val addr:   UInt = UInt(vaddrBitsExtended.W)
  val idx:    UInt = UInt(idxBits.W)
  val way_en: UInt = UInt(nWays.W)
  val data:   UInt = UInt(dataWidth.W)
}

class DCacheDataReq(
  untagBits:    Int,
  encBits:      Int,
  rowBytes:     Int,
  eccBytes:     Int,
  subWordBytes: Int,
  wordBytes:    Int,
  nWays:        Int)
    extends Bundle {
  val addr:     UInt = UInt(untagBits.W)
  val write:    Bool = Bool()
  val wdata:    UInt = UInt((encBits * rowBytes / eccBytes).W)
  val wordMask: UInt = UInt((rowBytes / subWordBytes).W)
  val eccMask:  UInt = UInt((wordBytes / eccBytes).W)
  val way_en:   UInt = UInt(nWays.W)
}

class FrontendReq(vaddrBitsExtended: Int) extends Bundle {
  val pc          = UInt(vaddrBitsExtended.W)
  val speculative = Bool()
}

class FrontendPerfEvents extends Bundle {
  val acquire = Bool()
  val tlbMiss = Bool()
}

class FrontendIO(
  vaddrBitsExtended: Int,
  vaddrBits:         Int,
  asidBits:          Int,
  entries:           Int,
  bhtHistoryLength:  Option[Int],
  bhtCounterLength:  Option[Int],
  coreInstBits:      Int,
  fetchWidth:        Int)
    extends Bundle {
  val might_request = Output(Bool())
  val clock_enabled = Input(Bool())
  val req           = Valid(new FrontendReq(vaddrBitsExtended))
  val sfence        = Valid(new SFenceReq(vaddrBits, asidBits))
  val resp          = Flipped(
    Decoupled(
      new FrontendResp(
        vaddrBits,
        entries,
        bhtHistoryLength,
        bhtCounterLength,
        vaddrBitsExtended,
        coreInstBits,
        fetchWidth
      )
    )
  )
  val gpa           = Flipped(Valid(UInt(vaddrBitsExtended.W)))
  val btb_update    = Valid(new BTBUpdate(vaddrBits, entries, fetchWidth, bhtHistoryLength, bhtCounterLength))
  val bht_update    = Valid(new BHTUpdate(bhtHistoryLength, bhtCounterLength, vaddrBits))
  val ras_update    = Valid(new RASUpdate(vaddrBits))
  val flush_icache  = Output(Bool())
  val npc           = Input(UInt(vaddrBitsExtended.W))
  val perf          = Input(new FrontendPerfEvents)
  val progress      = Output(Bool())
}

// Non-diplomatic version of Frontend
class FrontendBundle(
  vaddrBitsExtended: Int,
  vaddrBits:         Int,
  asidBits:          Int,
  entries:           Int,
  bhtHistoryLength:  Option[Int],
  bhtCounterLength:  Option[Int],
  coreInstBits:      Int,
  nPMPs:             Int,
  vpnBits:           Int,
  paddrBits:         Int,
  pgLevels:          Int,
  xLen:              Int,
  maxPAddrBits:      Int,
  pgIdxBits:         Int,
  hasCorrectable:    Boolean,
  hasUncorrectable:  Boolean,
  fetchWidth:        Int)
    extends Bundle {
  val cpu    = Flipped(
    new FrontendIO(
      vaddrBitsExtended,
      vaddrBits,
      asidBits,
      entries,
      bhtHistoryLength,
      bhtCounterLength,
      coreInstBits,
      fetchWidth
    )
  )
  val ptw    = new TLBPTWIO(nPMPs, vpnBits, paddrBits, vaddrBits, pgLevels, xLen, maxPAddrBits, pgIdxBits)
  val errors = new ICacheErrors(hasCorrectable, hasUncorrectable, paddrBits)
}

// Interface between T1 <> Rocket integration
class RocketCoreToT1(xLen: Int, vlWidth: Int) extends Bundle {
  val issue:  DecoupledIO[T1Issue] = Decoupled(new T1Issue(xLen, vlWidth))
  val retire: T1Retire             = Flipped(new T1Retire(xLen))
}

class T1Issue(xLen: Int, vlWidth: Int) extends Bundle {
  val instruction: UInt = UInt(32.W)
  val rs1Data:     UInt = UInt(xLen.W)
  val rs2Data:     UInt = UInt(xLen.W)
  val vtype:       UInt = UInt(32.W)
  val vl:          UInt = UInt(32.W)
  val vstart:      UInt = UInt(32.W)
  val vcsr:        UInt = UInt(32.W)
}

object T1Issue {
  def vlmul(issue: T1Issue): UInt = issue.vtype(2, 0)
  def vsew(issue:  T1Issue): UInt = issue.vtype(5, 3)
  def vta(issue:   T1Issue): Bool = issue.vtype(6)
  def vma(issue:   T1Issue): Bool = issue.vtype(7)
  def vxrm(issue:  T1Issue): UInt = issue.vcsr(2, 1)
}

class T1RdRetire(xLen: Int) extends Bundle {
  val rdAddress: UInt = UInt(5.W)
  val rdData:    UInt = UInt(xLen.W)
  val isFp:      Bool = Bool()
}

class T1CSRRetire extends Bundle {
  val vxsat: UInt = UInt(32.W)
  val fflag: UInt = UInt(32.W)
}

class T1Retire(xLen: Int) extends Bundle {
  val rd:  Valid[T1RdRetire]  = Valid(new T1RdRetire(xLen))
  val csr: Valid[T1CSRRetire] = Valid(new T1CSRRetire)
  val mem: Valid[Bundle]      = Valid(new Bundle {})
}
