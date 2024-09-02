// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.vfu

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.util._

@instantiable
class CSACompressor4_2 extends Module {
  @public
  val in   = IO(Input(Vec(4, UInt(1.W))))
  @public
  val cin  = IO(Input(UInt(1.W)))
  @public
  val out  = IO(Output(Vec(2, UInt(1.W))))
  @public
  val cout = IO(Output(UInt(1.W)))

  val ab   = in(0) ^ in(1)
  val cd   = in(2) ^ in(3)
  val abcd = ab ^ cd
  // sum
  out(1) := abcd ^ cin
  // carry
  out(0) := Mux(abcd.asBool, cin, in(3))
  cout   := Mux(ab.asBool, in(2), in(0))
}

@instantiable
class CSA42(width: Int) extends Module {
  @public
  val in  = IO(Input(Vec(4, UInt(width.W))))
  @public
  val out = IO(Output(Vec(2, UInt((width + 1).W))))

  val compressor: Seq[Instance[CSACompressor4_2]] = Seq.fill(width)(Instantiate(new CSACompressor4_2))

  /** cout in order
    *
    * coutUInt(i) represents cout for compressor(i)
    */
  val coutUInt = VecInit(compressor.map(_.cout)).asUInt
  val cinVec: Seq[Bool] = Cat(coutUInt(width - 2, 0), 0.U(1.W)).asBools
  val compressorAssign = compressor
    .zip(cinVec)
    .zipWithIndex
    .map {
      case ((c, cin), i) => {
        c.in(0) := in(0)(i)
        c.in(1) := in(1)(i)
        c.in(2) := in(2)(i)
        c.in(3) := in(3)(i)
        c.cin   := cin
      }
    }

  /** sum */
  out(1) := VecInit(compressor.map(_.out(1)) :+ coutUInt(width - 1)).asUInt

  /** carry */
  out(0) := VecInit(compressor.map(_.out(0))).asUInt

}

object CSA42 {
  def apply(
    width: Int
  )(in:    Vec[UInt]
  ): (UInt, UInt) = {
    val csa42 = Instantiate(new CSA42(width))
    csa42.in := in
    (csa42.out(0), csa42.out(1))
  }
}
