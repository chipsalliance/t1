// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isOther {
  def apply(t1DecodePattern: T1DecodePattern): isOther =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isOther(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vcpop.m",
      "vfirst.m",
      "vfmerge.vfm",
      "vfmv.v.f",
      "vfmv.s.f",
      "vid.v",
      "vmerge.vim",
      "vmv.v.i",
      "vmerge.vvm",
      "vmv.v.v",
      "vmerge.vxm",
      "vmv.v.x",
      "vmsbf.m",
      "vmsif.m",
      "vmsof.m",
      "vmv.s.x",
      "vmv.x.s",
      "vnclip.wi",
      "vnclip.wv",
      "vnclip.wx",
      "vnclipu.wi",
      "vnclipu.wv",
      "vnclipu.wx",
      "vrgather.vi",
      "vrgather.vv",
      "vrgather.vx",
      "vrgatherei16.vv"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isOther(value: TriState) extends BooleanDecodeAttribute {
  override val description: String = "goes to [[org.chipsalliance.t1.rtl.OtherUnit]]"
}
