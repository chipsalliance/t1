// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isMasksource {
  def apply(t1DecodePattern: T1DecodePattern): isMasksource =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isMasksource(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vadc.vim",
      "vadc.vvm",
      "vadc.vxm",
      "vfmerge.vfm",
      "vfmv.v.f",
      "vmadc.vi",
      "vmadc.vim",
      "vmadc.vv",
      "vmadc.vvm",
      "vmadc.vx",
      "vmadc.vxm",
      "vmerge.vim",
      "vmv.v.i",
      "vmerge.vvm",
      "vmv.v.v",
      "vmerge.vxm",
      "vmv.v.x",
      "vmsbc.vv",
      "vmsbc.vvm",
      "vmsbc.vx",
      "vmsbc.vxm",
      "vsbc.vvm",
      "vsbc.vxm"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isMasksource(value: TriState) extends BooleanDecodeAttribute {
  override val description: String =
    "three ops. these ops don't use mask. use v0 as third op, read it from duplicate V0. it will read use mask(v0) in mask format as source. "
}
