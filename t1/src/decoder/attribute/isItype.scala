// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isItype {
  def apply(t1DecodePattern: T1DecodePattern): isItype =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isItype(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vadc.vim",
      "vadd.vi",
      "vand.vi",
      "vmadc.vi",
      "vmadc.vim",
      "vmerge.vim",
      "vmv.v.i",
      "vmseq.vi",
      "vmsgt.vi",
      "vmsgtu.vi",
      "vmsle.vi",
      "vmsleu.vi",
      "vmsne.vi",
      "vmv1r.v",
      "vmv2r.v",
      "vmv4r.v",
      "vmv8r.v",
      "vnclip.wi",
      "vnclipu.wi",
      "vnsra.wi",
      "vnsrl.wi",
      "vor.vi",
      "vrgather.vi",
      "vrsub.vi",
      "vsadd.vi",
      "vsaddu.vi",
      "vslidedown.vi",
      "vslideup.vi",
      "vsll.vi",
      "vsra.vi",
      "vsrl.vi",
      "vssra.vi",
      "vssrl.vi",
      "vxor.vi",
      // rv_zvbb
      "vror.vi",
      "vwsll.vi"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isItype(value: TriState) extends BooleanDecodeAttribute {
  override val description: String = "src is imm."
}
