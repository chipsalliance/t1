// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isZvbb {
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
          "vandn.vv",
          "vandn.vx",
          "vbrev.v",
          "vbrev8.v",
          "vrev8.v",
          "vclz.v",
          "vctz.v",
          "vrol.vv",
          "vrol.vx",
          "vror.vv",
          "vror.vx",
          "vror.vi",
          "vwsll.vv",
          "vwsll.vx",
          "vwsll.vi"
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

case class isZvbb(value: TriState) extends BooleanDecodeAttribute {
  override val description: String = "goes to [[org.chipsalliance.t1.rtl.LaneZvbb]]."
}
