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

case class LaneFFOParam(datapathWidth: Int) extends Parameter

given upickle.default.ReadWriter[LaneFFOParam] = upickle.default.macroRW

class LaneFFOInterface(parameter: LaneFFOParam) extends HWBundle(parameter):
  val clock:        BundleField[Clock]         = Flipped(Clock())
  val reset:        BundleField[Reset]         = Flipped(Reset())
  val src:          BundleField[Vec[UInt]]     = Flipped(Vec(4, UInt(parameter.datapathWidth)))
  val resultSelect: BundleField[UInt]          = Flipped(UInt(2))
  val complete:     BundleField[Bool]          = Flipped(Bool())
  val maskType:     BundleField[Bool]          = Flipped(Bool())
  val resp:         BundleField[ValidIO[UInt]] = Aligned(Valid(UInt(parameter.datapathWidth)))

class LaneFFOLayers(parameter: LaneFFOParam) extends LayerInterface(parameter):
  def layers = Seq.empty

class LaneFFOProbe(parameter: LaneFFOParam) extends DVBundle[LaneFFOParam, LaneFFOLayers](parameter)

@generator
object LaneFFO extends Generator[LaneFFOParam, LaneFFOLayers, LaneFFOInterface, LaneFFOProbe]:
  override def moduleName(parameter: LaneFFOParam): String = "LaneFFO"

  def architecture(parameter: LaneFFOParam) =
    val p  = parameter
    val io = summon[Interface[LaneFFOInterface]]

    given Ref[Clock] = io.clock
    given Ref[Reset] = io.reset

    val allOnes = (-1).S(p.datapathWidth).asBits.asUInt

    val truthMask  = (io.maskType ? (io.src(0), allOnes)).asBits & io.src(3).asBits
    val srcData    = (truthMask & io.src(1).asBits).asUInt
    val notZero    = srcData.asBits.orR
    val lo         = scanLeftOr(srcData)
    // set before (right or)
    val ro         = (~lo.asBits).asUInt
    // set including
    val inc        = (ro.asBits ## notZero.asBits).asUInt
    // 1H: Chisel lo & inc with automatic width extension (lo zero-extended to match inc)
    val loExtended = (false.B.asBits ## lo.asBits).asUInt
    val ohFull     = (loExtended.asBits & inc.asBits).asUInt
    val oh         = ohFull.asBits.bits(p.datapathWidth - 1, 0).asUInt
    val index      = OHToUInt(oh)

    // OH1ToOH: (((x << 1) | 1) & ~Cat(0, x))
    // OH1ToUInt: OHToUInt(OH1ToOH(x))

    io.resp.valid := notZero

    val selectOH = UIntToOH(io.resultSelect)
    // find-first-set
    val first    = selectOH.asBits.bit(0)
    // set-before-first
    val sbf      = selectOH.asBits.bit(1)
    // set-only-first
    val sof      = selectOH.asBits.bit(2)
    // set-including-first
    val sif      = selectOH.asBits.bit(3)

    val ffoResult = mux1H(
      Seq(
        io.complete,
        !io.complete & first,
        !io.complete & notZero & sbf,
        !io.complete & sof,
        !io.complete & notZero & sif,
        !io.complete & !notZero & io.resultSelect.asBits.bit(0)
      ),
      Seq(
        0.U,
        index,
        ro,
        oh,
        inc.asBits.bits(p.datapathWidth - 1, 0).asUInt,
        1.U
      )
    )

    val resultMask = (((io.maskType & !first) ? (io.src(0), allOnes)).asBits &
      (first ? (allOnes, io.src(3))).asBits).asUInt
    io.resp.bits := ((ffoResult.asBits & resultMask.asBits) | (io.src(2).asBits & (~resultMask.asBits))).asUInt
