// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util.log2Ceil

object PMPCheckerParameter {
  implicit def rwP: upickle.default.ReadWriter[PMPCheckerParameter] = upickle.default.macroRW[PMPCheckerParameter]
}

case class PMPCheckerParameter(
  nPMPs:          Int,
  paddrBits:      Int,
  // @todo: log2Ceil(coreDataBytes)?
  lgMaxSize:      Int,
  pmpGranularity: Int)
    extends SerializableModuleParameter

class PMPCheckerInterface(parameter: PMPCheckerParameter) extends Bundle {
  val prv = Input(UInt(PRV.SZ.W))
  val pmp = Input(Vec(parameter.nPMPs, new PMP(parameter.paddrBits)))
  val addr = Input(UInt(parameter.paddrBits.W))
  val size = Input(UInt(log2Ceil(parameter.lgMaxSize + 1).W))
  val r = Output(Bool())
  val w = Output(Bool())
  val x = Output(Bool())
}

@instantiable
class PMPChecker(val parameter: PMPCheckerParameter)
    extends FixedIORawModule(new PMPCheckerInterface(parameter))
    with SerializableModule[PMPCheckerParameter]
    with Public {

  val paddrBits = parameter.paddrBits
  val pmpGranularity = parameter.pmpGranularity
  val lgMaxSize = parameter.lgMaxSize

  val default = if (io.pmp.isEmpty) true.B else io.prv > PRV.S.U
  val pmp0 = WireInit(0.U.asTypeOf(new PMP(paddrBits)))
  pmp0.cfg.r := default
  pmp0.cfg.w := default
  pmp0.cfg.x := default

  val res = io.pmp.zip(pmp0 +: io.pmp).reverse.foldLeft(pmp0) {
    case (prev, (pmp, prevPMP)) =>
      val hit = PMP.hit(pmp, io.addr, io.size, lgMaxSize, prevPMP, pmpGranularity)
      val ignore = default && !pmp.cfg.l
      val aligned = PMP.aligned(pmp, io.addr, io.size, lgMaxSize, prevPMP, pmpGranularity)
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
