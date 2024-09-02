// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isAdder {
  def apply(t1DecodePattern: T1DecodePattern): isAdder =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isAdder(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vaadd.vv",
      "vaadd.vx",
      "vaaddu.vv",
      "vaaddu.vx",
      "vadc.vim",
      "vadc.vvm",
      "vadc.vxm",
      "vadd.vi",
      "vadd.vv",
      "vadd.vx",
      "vasub.vv",
      "vasub.vx",
      "vasubu.vv",
      "vasubu.vx",
      "vmadc.vi",
      "vmadc.vim",
      "vmadc.vv",
      "vmadc.vvm",
      "vmadc.vx",
      "vmadc.vxm",
      "vmax.vv",
      "vmax.vx",
      "vmaxu.vv",
      "vmaxu.vx",
      "vmin.vv",
      "vmin.vx",
      "vminu.vv",
      "vminu.vx",
      "vmsbc.vv",
      "vmsbc.vvm",
      "vmsbc.vx",
      "vmsbc.vxm",
      "vmseq.vi",
      "vmseq.vv",
      "vmseq.vx",
      "vmsgt.vi",
      "vmsgt.vx",
      "vmsgtu.vi",
      "vmsgtu.vx",
      "vmsle.vi",
      "vmsle.vv",
      "vmsle.vx",
      "vmsleu.vi",
      "vmsleu.vv",
      "vmsleu.vx",
      "vmslt.vv",
      "vmslt.vx",
      "vmsltu.vv",
      "vmsltu.vx",
      "vmsne.vi",
      "vmsne.vv",
      "vmsne.vx",
      "vredmax.vs",
      "vredmaxu.vs",
      "vredmin.vs",
      "vredminu.vs",
      "vredsum.vs",
      "vrsub.vi",
      "vrsub.vx",
      "vsadd.vi",
      "vsadd.vv",
      "vsadd.vx",
      "vsaddu.vi",
      "vsaddu.vv",
      "vsaddu.vx",
      "vsbc.vvm",
      "vsbc.vxm",
      "vssub.vv",
      "vssub.vx",
      "vssubu.vv",
      "vssubu.vx",
      "vsub.vv",
      "vsub.vx",
      "vwadd.vv",
      "vwadd.vx",
      "vwadd.wv",
      "vwadd.wx",
      "vwaddu.vv",
      "vwaddu.vx",
      "vwaddu.wv",
      "vwaddu.wx",
      "vwredsum.vs",
      "vwredsumu.vs",
      "vwsub.vv",
      "vwsub.vx",
      "vwsub.wv",
      "vwsub.wx",
      "vwsubu.vv",
      "vwsubu.vx",
      "vwsubu.wv",
      "vwsubu.wx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isAdder(value: TriState) extends BooleanDecodeAttribute {
  override val description: String = "goes to [[org.chipsalliance.t1.rtl.LaneAdder]]."
}
