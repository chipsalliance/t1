// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util.log2Ceil

case class PMPCheckerParameter(
  nPMPs:          Int,
  paddrBits:      Int,
  lgMaxSize:      Int,
  pmpGranularity: Int)
    extends SerializableModuleParameter {}

class PMPCheckerInterface(parameter: PMPCheckerParameter) extends Bundle {
  val prv = Input(UInt(PRV.SZ.W))
  val pmp = Input(Vec(parameter.nPMPs, new PMP(parameter.paddrBits)))
  val addr = Input(UInt(parameter.paddrBits.W))
  val size = Input(UInt(log2Ceil(parameter.lgMaxSize + 1).W))
  val r = Output(Bool())
  val w = Output(Bool())
  val x = Output(Bool())
}

class PMPChecker(val parameter: PMPCheckerParameter)
    extends FixedIORawModule(new PMPCheckerInterface(parameter))
    with SerializableModule[PMPCheckerParameter] {

  val paddrBits = parameter.paddrBits
  val pmpGranularity = parameter.pmpGranularity
  val lgMaxSize = parameter.lgMaxSize
  def UIntToOH1(x: UInt, width: Int): UInt = ~((-1).S(width.W).asUInt << x)(width - 1, 0)

  val default = if (io.pmp.isEmpty) true.B else io.prv > PRV.S.U
  val pmp0 = WireInit(0.U.asTypeOf(new PMP(paddrBits)))
  pmp0.cfg.r := default
  pmp0.cfg.w := default
  pmp0.cfg.x := default

  val res = (io.pmp.zip(pmp0 +: io.pmp)).reverse.foldLeft(pmp0) {
    case (prev, (pmp, prevPMP)) =>
      def napot(cfg: PMPConfig) = cfg.a(1)
      // returns whether this PMP matches at least one byte of the access
      def comparand(pmp: PMP): UInt = ~(~(pmp.addr << PMP.lgAlign) | (pmpGranularity - 1).U)

      def pow2Match(pmp: PMP, x: UInt, lgSize: UInt, lgMaxSize: Int) = {
        def eval(a: UInt, b: UInt, m: UInt): Bool = ((a ^ b) & ~m) === 0.U

        if (lgMaxSize <= log2Ceil(pmpGranularity)) {
          eval(x, comparand(pmp), pmp.mask)
        } else {
          // break up the circuit; the MSB part will be CSE'd
          val lsbMask = pmp.mask | UIntToOH1(lgSize, lgMaxSize)
          val msbMatch = eval(x >> lgMaxSize, comparand(pmp) >> lgMaxSize, pmp.mask >> lgMaxSize)
          val lsbMatch = eval(x(lgMaxSize - 1, 0), comparand(pmp)(lgMaxSize - 1, 0), lsbMask(lgMaxSize - 1, 0))
          msbMatch && lsbMatch
        }
      }
      def torNotNAPOT(cfg: PMPConfig) = cfg.a(0)
      def rangeMatch(pmp: PMP, x: UInt, lgSize: UInt, lgMaxSize: Int, prev: PMP) = {
        def boundMatch(x: UInt, lsbMask: UInt, lgMaxSize: Int) = {
          if (lgMaxSize <= log2Ceil(pmpGranularity)) {
            x < comparand(pmp)
          } else {
            // break up the circuit; the MSB part will be CSE'd
            val msbsLess:  Bool = ((x >> lgMaxSize): UInt) < ((comparand(pmp) >> lgMaxSize): UInt)
            val msbsEqual: Bool = (((x >> lgMaxSize): UInt) ^ ((comparand(pmp) >> lgMaxSize): UInt)) === 0.U
            val lsbsLess:  Bool = (x(lgMaxSize - 1, 0) | lsbMask) < comparand(pmp)(lgMaxSize - 1, 0)
            msbsLess || (msbsEqual && lsbsLess)
          }
        }
        def lowerBoundMatch(x: UInt, lgSize: UInt, lgMaxSize: Int) =
          !boundMatch(x, UIntToOH1(lgSize, lgMaxSize), lgMaxSize)
        def upperBoundMatch(x: UInt, lgMaxSize: Int) = boundMatch(x, 0.U, lgMaxSize)
        lowerBoundMatch(x, lgSize, lgMaxSize) && upperBoundMatch(x, lgMaxSize)
      }

      def hit(pmp: PMP, x: UInt, lgSize: UInt, lgMaxSize: Int, prev: PMP): Bool =
        Mux(
          napot(pmp.cfg),
          pow2Match(pmp, x, lgSize, lgMaxSize),
          torNotNAPOT(pmp.cfg) && rangeMatch(pmp, x, lgSize, lgMaxSize, prev)
        )
      val hit = hit(pmp, io.addr, io.size, lgMaxSize, prevPMP)
      val ignore = default && !pmp.cfg.l

      // returns whether this matching PMP fully contains the access
      def isAligned(pmp: PMP, x: UInt, lgSize: UInt, lgMaxSize: Int, prev: PMP): Bool =
        if (lgMaxSize <= log2Ceil(pmpGranularity)) true.B
        else {
          val lsbMask = UIntToOH1(lgSize, lgMaxSize)
          val straddlesLowerBound =
            (((x >> lgMaxSize) ^ (comparand(prev) >> lgMaxSize)) === 0.U: Bool) && // WTF?
              (comparand(prev)(lgMaxSize - 1, 0) & ~x(lgMaxSize - 1, 0)) =/= 0.U
          val straddlesUpperBound =
            (((x >> lgMaxSize) ^ (comparand(pmp) >> lgMaxSize)) === 0.U: Bool) && // WTF?
              (comparand(pmp)(lgMaxSize - 1, 0) & (x(lgMaxSize - 1, 0) | lsbMask)) =/= 0.U
          val rangeAligned = !(straddlesLowerBound || straddlesUpperBound)
          val pow2Aligned = (lsbMask & ~pmp.mask(lgMaxSize - 1, 0)) === 0.U
          Mux(napot(pmp.cfg), pow2Aligned, rangeAligned)
        }

      val aligned = isAligned(pmp, io.addr, io.size, lgMaxSize, prevPMP)

      // for (
      //   (name, idx) <- Seq("no", "TOR", if (pmpGranularity <= 4) "NA4" else "", "NAPOT").zipWithIndex; if name.nonEmpty
      // )
      //   property
      //     .cover(pmp.cfg.a === idx.U, s"The cfg access is set to ${name} access ", "Cover PMP access mode setting")

      //  property.cover(pmp.cfg.l === 0x1.U, s"The cfg lock is set to high ", "Cover PMP lock mode setting")

      // Not including Write and no Read permission as the combination is reserved
      // for ((name, idx) <- Seq("no", "RO", "", "RW", "X", "RX", "", "RWX").zipWithIndex; if name.nonEmpty)
      //   property.cover(
      //     (Cat(pmp.cfg.x, pmp.cfg.w, pmp.cfg.r) === idx.U),
      //     s"The permission is set to ${name} access ",
      //     "Cover PMP access permission setting"
      //   )

      //for (
      //  (name, idx) <- Seq("", "TOR", if (pmpGranularity <= 4) "NA4" else "", "NAPOT").zipWithIndex; if name.nonEmpty
      //) {
      //  property.cover(
      //    !ignore && hit && aligned && pmp.cfg.a === idx.U,
      //    s"The access matches ${name} mode ",
      //    "Cover PMP access"
      //  )
      //  property.cover(
      //    pmp.cfg.l && hit && aligned && pmp.cfg.a === idx.U,
      //    s"The access matches ${name} mode with lock bit high",
      //    "Cover PMP access with lock bit"
      //  )
      // }

      val cur = WireInit(pmp)
      cur.cfg.r := aligned && (pmp.cfg.r || ignore)
      cur.cfg.w := aligned && (pmp.cfg.w || ignore)
      cur.cfg.x := aligned && (pmp.cfg.x || ignore)
      Mux(hit, cur, prev)
  }

  io.r := res.cfg.r
  io.w := res.cfg.w
  io.x := res.cfg.x
}
