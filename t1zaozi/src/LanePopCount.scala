// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.rtl

import me.jiuyang.zaozi.*
import me.jiuyang.zaozi.default.{*, given}
import me.jiuyang.zaozi.reftpe.*
import me.jiuyang.zaozi.valuetpe.*
import me.jiuyang.stdlib.*
import me.jiuyang.stdlib.default.{*, given}
import org.llvm.mlir.scalalib.capi.ir.{*, given}

import java.lang.foreign.Arena

case class LanePopCountParam(datapathWidth: Int) extends Parameter

given upickle.default.ReadWriter[LanePopCountParam] = upickle.default.macroRW

class LanePopCountInterface(parameter: LanePopCountParam) extends HWBundle(parameter):
  val clock: BundleField[Clock] = Flipped(Clock())
  val reset: BundleField[Reset] = Flipped(Reset())
  val src:   BundleField[UInt]  = Flipped(UInt(parameter.datapathWidth))
  val resp:  BundleField[UInt]  = Aligned(UInt(parameter.datapathWidth))

class LanePopCountLayers(parameter: LanePopCountParam) extends LayerInterface(parameter):
  def layers = Seq.empty

class LanePopCountProbe(parameter: LanePopCountParam) extends DVBundle[LanePopCountParam, LanePopCountLayers](parameter)

@generator
object LanePopCount extends Generator[LanePopCountParam, LanePopCountLayers, LanePopCountInterface, LanePopCountProbe]:
  override def moduleName(parameter: LanePopCountParam): String = "LanePopCount"

  def architecture(parameter: LanePopCountParam) =
    val io = summon[Interface[LanePopCountInterface]]

    given Ref[Clock] = io.clock
    given Ref[Reset] = io.reset

    io.resp := popCount(io.src)
