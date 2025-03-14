// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isZvma {
  def apply(t1DecodePattern: T1DecodePattern): isZvbb =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isZvbb(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched =
      if (t1DecodePattern.param.zvbbEnable)
        Seq(
          "vlte8",
          "vlte16",
          "vlte32",
          "vste8",
          "vste16",
          "vste32",
          "vtmv.v.t",
          "vtmv.t.v",
          "mm.u.u",
          "mm.u.s",
          "mm.s.u",
          "mm.s.s",
          "vtzero.t",
          "vtdiscard",
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

case class isZvma(value: TriState) extends BooleanDecodeAttribute {
  override val description: String = "goes to [[org.chipsalliance.t1.rtl.LaneZvma]]."
}
