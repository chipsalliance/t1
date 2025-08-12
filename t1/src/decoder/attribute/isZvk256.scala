// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isZvk256 {
  def apply(t1DecodePattern: T1DecodePattern): isZvk256 =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isZvk256(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched =
      if (t1DecodePattern.param.zvkEnable)
        Seq(
          "vsm3c.vi",
          "vsm3me.vv"
        )
      else Seq()
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isZvk256(value: TriState) extends BooleanDecodeAttribute {
  override val description: String = "goes to [[org.chipsalliance.t1.rtl.LaneZvk256]]."
}
