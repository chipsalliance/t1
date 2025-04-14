// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._

@instantiable
class LaneFFO(datapathWidth: Int) extends Module {
  // Seq(mask, data, destination)
  @public
  val src:          Vec[UInt]     = IO(Input(Vec(4, UInt(datapathWidth.W))))
  @public
  val resultSelect: UInt          = IO(Input(UInt(2.W)))
  @public
  val resp:         ValidIO[UInt] = IO(Output(Valid(UInt(datapathWidth.W))))
  @public
  val complete:     Bool          = IO(Input(Bool()))
  @public
  val maskType:     Bool          = IO(Input(Bool()))

  val truthMask: UInt = Mux(maskType, src.head, -1.S(datapathWidth.W).asUInt) & src(3)
  val srcData:   UInt = truthMask & src(1)
  val notZero:   Bool = srcData.orR
  val lo:        UInt = scanLeftOr(srcData)
  // set before(right or)
  val ro:        UInt = (~lo).asUInt
  // set including
  val inc:       UInt = ro ## notZero
  // 1H
  val OH:        UInt = lo & inc
  val index:     UInt = OHToUInt(OH)

  // copy&paste from rocket-chip: src/main/scala/util/package.scala
  // todo: upstream this to chisel3
  private def OH1ToOH(x:   UInt): UInt = (((x << 1): UInt) | 1.U) & ~Cat(0.U(1.W), x)
  private def OH1ToUInt(x: UInt): UInt = OHToUInt(OH1ToOH(x))
  resp.valid := notZero
  //
  val selectOH = UIntToOH(resultSelect)
  // find-first-set
  val first:                      Bool = selectOH(0)
  // set-before-first
  val sbf:                        Bool = selectOH(1)
  // set-only-first
  val sof:                        Bool = selectOH(2)
  // set-including-first
  val sif:                        Bool = selectOH(3)

  /** first: complete ? 0 : index sbf: complete ? 0 : notZero ? ro : -1 sof: complete ? 0 : OH sif: complete ? 0 :
    * notZero ? inc : -1
    */
  val ffoResult:  UInt = Mux1H(
    Seq(
      complete,
      !complete && first,
      !complete && notZero && sbf,
      !complete && sof,
      !complete && notZero && sif,
      !complete && !notZero && resultSelect(0)
    ),
    Seq(0.U, index, ro, OH, inc, -1.S.asUInt)
  )
  val resultMask: UInt = Mux(maskType && !first, src.head, -1.S(datapathWidth.W).asUInt) &
    Mux(first, -1.S(datapathWidth.W).asUInt, src.last)
  resp.bits := (ffoResult & resultMask) | (src(2) & (~resultMask).asUInt)
}
