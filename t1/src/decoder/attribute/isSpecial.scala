// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isSpecial {
  def apply(t1DecodePattern: T1DecodePattern): isSpecial =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isSpecial(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vcompress.vm",
      "vcpop.m",
      "vfirst.m",
      "vfmv.f.s",
      "vfmv.s.f",
      "vfredmax.vs",
      "vfredmin.vs",
      "vfredosum.vs",
      "vfredusum.vs",
      "vfslide1down.vf",
      "vfslide1up.vf",
      "vfwredosum.vs",
      "vfwredusum.vs",
      "viota.m",
      "vloxei1024.v",
      "vloxei128.v",
      "vloxei16.v",
      "vloxei256.v",
      "vloxei32.v",
      "vloxei512.v",
      "vloxei64.v",
      "vloxei8.v",
      "vluxei1024.v",
      "vluxei128.v",
      "vluxei16.v",
      "vluxei256.v",
      "vluxei32.v",
      "vluxei512.v",
      "vluxei64.v",
      "vluxei8.v",
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
      "vmsof.m",
      "vmv.s.x",
      "vmv.x.s",
      "vredand.vs",
      "vredmax.vs",
      "vredmaxu.vs",
      "vredmin.vs",
      "vredminu.vs",
      "vredor.vs",
      "vredsum.vs",
      "vredxor.vs",
      "vrgather.vv",
      "vrgatherei16.vv",
      "vsext.vf2",
      "vsext.vf4",
      "vsext.vf8",
      "vslide1down.vx",
      "vslide1up.vx",
      "vslidedown.vi",
      "vslidedown.vx",
      "vslideup.vi",
      "vslideup.vx",
      "vsoxei1024.v",
      "vsoxei128.v",
      "vsoxei16.v",
      "vsoxei256.v",
      "vsoxei32.v",
      "vsoxei512.v",
      "vsoxei64.v",
      "vsoxei8.v",
      "vsuxei1024.v",
      "vsuxei128.v",
      "vsuxei16.v",
      "vsuxei256.v",
      "vsuxei32.v",
      "vsuxei512.v",
      "vsuxei64.v",
      "vsuxei8.v",
      "vwredsum.vs",
      "vwredsumu.vs",
      "vzext.vf2",
      "vzext.vf4",
      "vzext.vf8"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isSpecial(value: TriState) extends BooleanDecodeAttribute {
  override val description: String =
    "if Sequencer is the router for data from Lane to LSU or Sequencer mask unit. special -> maskUnit || index type load store "
}
