// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isUnorderwrite {
  def apply(t1DecodePattern: T1DecodePattern): isUnorderwrite =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isUnorderwrite(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vfmv.f.s",
      "vfmv.s.f",
      "vfredosum.vs",
      "vfslide1down.vf",
      "vfslide1up.vf",
      "viota.m",
      "vmv.s.x",
      "vmv.x.s",
      "vslide1down.vx",
      "vslide1up.vx",
      "vslidedown.vi",
      "vslidedown.vx",
      "vslideup.vi",
      "vslideup.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isUnorderwrite(value: TriState) extends BooleanDecodeAttribute {
  override val description: String =
    "unmanaged write for VRF. these instructions cannot be chain as source. TODO: add an attribute these instruction cannot be the source of chaining. "
}
