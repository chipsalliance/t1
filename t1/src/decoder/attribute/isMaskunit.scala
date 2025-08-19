// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isMaskunit {
  def apply(t1DecodePattern: T1DecodePattern): isMaskunit =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isMaskunit(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val mvType          = Seq(
      "vfmv.f.s",
      "vfmv.s.f",
      "vmv.s.x",
      "vmv.x.s"
    )
    val compress        = Seq(
      "vcompress.vm",
      "viota.m"
    )
    val maskDestination = Seq(
      "vmadc.vi",
      "vmadc.vim",
      "vmadc.vv",
      "vmadc.vvm",
      "vmadc.vx",
      "vmadc.vxm",
      "vmfeq.vf",
      "vmfeq.vv",
      "vmfge.vf",
      "vmfgt.vf",
      "vmfle.vf",
      "vmfle.vv",
      "vmflt.vf",
      "vmflt.vv",
      "vmfne.vf",
      "vmfne.vv",
      "vmsbc.vv",
      "vmsbc.vvm",
      "vmsbc.vx",
      "vmsbc.vxm",
      "vmsbf.m",
      "vmseq.vi",
      "vmseq.vv",
      "vmseq.vx",
      "vmsgt.vi",
      "vmsgt.vx",
      "vmsgtu.vi",
      "vmsgtu.vx",
      "vmsif.m",
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
      "vmsof.m"
    )
    val popCount        = Seq(
      "vcpop.m"
    )
    val isFFO           = Seq(
      "vfirst.m",
      "vmsbf.m",
      "vmsif.m",
      "vmsof.m"
    )
    val allMatched      = mvType ++ compress ++ maskDestination ++ popCount ++ isFFO
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isMaskunit(value: TriState) extends BooleanDecodeAttribute {
  override val description: String =
    "mask unit -> red || compress || viota || ffo || slid || maskDestination || gather(v) || mv || popCount || extend all instruction in Sequencer mask unit. "
}
