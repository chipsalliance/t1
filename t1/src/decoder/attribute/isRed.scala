// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isRed {
  def apply(t1DecodePattern: T1DecodePattern): isRed =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isRed(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vcpop.m",
      "vfredmax.vs",
      "vfredmin.vs",
      "vfredosum.vs",
      "vfredusum.vs",
      "vfwredosum.vs",
      "vfwredusum.vs",
      "vredand.vs",
      "vredmax.vs",
      "vredmaxu.vs",
      "vredmin.vs",
      "vredminu.vs",
      "vredor.vs",
      "vredsum.vs",
      "vredxor.vs",
      "vwredsum.vs",
      "vwredsumu.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isRed(value: TriState) extends BooleanDecodeAttribute {
  override val description: String =
    "do reduce in each lane. each element will sequentially executed in each lanes. after finishing, pop it to Top, and use ALU at top to get the final result and send to element0 TODO: better name. "
}
