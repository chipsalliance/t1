// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isFother {
  def apply(t1DecodePattern: T1DecodePattern): isFother =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isFother(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vfclass.v",
      "vfcvt.f.x.v",
      "vfcvt.f.xu.v",
      "vfcvt.rtz.x.f.v",
      "vfcvt.rtz.xu.f.v",
      "vfcvt.x.f.v",
      "vfcvt.xu.f.v",
      "vfrec7.v",
      "vfrsqrt7.v",
      "vfsgnj.vf",
      "vfsgnj.vv",
      "vfsgnjn.vf",
      "vfsgnjn.vv",
      "vfsgnjx.vf",
      "vfsgnjx.vv"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isFother(value: TriState) extends BooleanDecodeAttribute {
  override val description: String =
    "designed for Other Unit in FP. goes to [[org.chipsalliance.t1.rtl.LaneFloat]] OtherUnit. TODO: perf it."
}
