// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isZvk128 {
  def apply(t1DecodePattern: T1DecodePattern): isZvk128 =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isZvk128(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched =
      if (t1DecodePattern.param.zvkEnable)
        Seq(
          "vghsh.vv",
          "vgmul.vv",
          "vaesdf.vv",
          "vaesdf.vs",
          "vaesdm.vv",
          "vaesdm.vs",
          "vaesef.vv",
          "vaesef.vs",
          "vaesem.vv",
          "vaesem.vs",
          "vaesz.vs",
          "vaeskf1.vi",
          "vaeskf2.vi",
          "vsha2ms.vv",
          "vsha2ch.vv",
          "vsha2cl.vv",
          "vsm4k.vi",
          "vsm4r.vv",
          "vsm4r.vs"
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

case class isZvk128(value: TriState) extends BooleanDecodeAttribute {
  override val description: String = "goes to [[org.chipsalliance.t1.rtl.LaneZvk]]."
}
