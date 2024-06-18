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
      val hit = hit(pmp, io.addr, io.size, lgMaxSize, prevPMP)
      val ignore = default && !pmp.cfg.l


      val aligned = PMP.aligned(pmp, io.addr, io.size, lgMaxSize, prevPMP, pmpGranularity)

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
