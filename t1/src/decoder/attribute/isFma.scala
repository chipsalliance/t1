// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isFma {
  def apply(t1DecodePattern: T1DecodePattern): isFma =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isFma(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vfadd.vf",
      "vfadd.vv",
      "vfmacc.vf",
      "vfmacc.vv",
      "vfmadd.vf",
      "vfmadd.vv",
      "vfmsac.vf",
      "vfmsac.vv",
      "vfmsub.vf",
      "vfmsub.vv",
      "vfmul.vf",
      "vfmul.vv",
      "vfnmacc.vf",
      "vfnmacc.vv",
      "vfnmadd.vf",
      "vfnmadd.vv",
      "vfnmsac.vf",
      "vfnmsac.vv",
      "vfnmsub.vf",
      "vfnmsub.vv",
      "vfredosum.vs",
      "vfredusum.vs",
      "vfrsub.vf",
      "vfsub.vf",
      "vfsub.vv"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isFma(value: TriState) extends BooleanDecodeAttribute {
  override val description: String = "uop of FMA, goes to [[org.chipsalliance.t1.rtl.LaneFloat]] FMA unit."
}
