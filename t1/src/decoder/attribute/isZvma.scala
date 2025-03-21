// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isZvma {
  def apply(t1DecodePattern: T1DecodePattern): isZvma =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isZvma(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched =
      if (t1DecodePattern.param.zvmaEnable)
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
          "mm.e5m2.e4m3",
          "mm.e5m2.e5m2",
          "mm.e4m3.e4m3",
          "mm.e4m3.e5m2",
          "vtzero.t",
          "p2mm.f.f",
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
