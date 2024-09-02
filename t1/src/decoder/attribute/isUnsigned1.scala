// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isUnsigned1 {
  def apply(t1DecodePattern: T1DecodePattern): isUnsigned1 =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isUnsigned1(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vaaddu.vv",
      "vaaddu.vx",
      "vasubu.vv",
      "vasubu.vx",
      "vcpop.m",
      "vdivu.vv",
      "vdivu.vx",
      "vfcvt.f.xu.v",
      "vfcvt.rtz.xu.f.v",
      "vfirst.m",
      "vid.v",
      "viota.m",
      "vmadc.vi",
      "vmadc.vim",
      "vmadc.vv",
      "vmadc.vvm",
      "vmadc.vx",
      "vmadc.vxm",
      "vmaxu.vv",
      "vmaxu.vx",
      "vminu.vv",
      "vminu.vx",
      "vmsbc.vv",
      "vmsbc.vvm",
      "vmsbc.vx",
      "vmsbc.vxm",
      "vmsbf.m",
      "vmsgtu.vi",
      "vmsgtu.vx",
      "vmsif.m",
      "vmsleu.vi",
      "vmsleu.vv",
      "vmsleu.vx",
      "vmsltu.vv",
      "vmsltu.vx",
      "vmsof.m",
      "vmulhu.vv",
      "vmulhu.vx",
      "vmv.s.x",
      "vmv.x.s",
      "vnclipu.wi",
      "vnclipu.wv",
      "vnclipu.wx",
      "vnsrl.wi",
      "vnsrl.wv",
      "vnsrl.wx",
      "vredmaxu.vs",
      "vredminu.vs",
      "vremu.vv",
      "vremu.vx",
      "vsaddu.vi",
      "vsaddu.vv",
      "vsaddu.vx",
      "vsext.vf2",
      "vsext.vf4",
      "vsext.vf8",
      "vsll.vi",
      "vsll.vv",
      "vsll.vx",
      "vsrl.vi",
      "vsrl.vv",
      "vsrl.vx",
      "vssrl.vi",
      "vssrl.vv",
      "vssrl.vx",
      "vssubu.vv",
      "vssubu.vx",
      "vwaddu.vv",
      "vwaddu.vx",
      "vwaddu.wv",
      "vwaddu.wx",
      "vwmaccsu.vv",
      "vwmaccsu.vx",
      "vwmaccu.vv",
      "vwmaccu.vx",
      "vwmulu.vv",
      "vwmulu.vx",
      "vwredsumu.vs",
      "vwsubu.vv",
      "vwsubu.vx",
      "vwsubu.wv",
      "vwsubu.wx",
      "vzext.vf2",
      "vzext.vf4",
      "vzext.vf8",
      // rv_zvbb
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
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isUnsigned1(value: TriState) extends BooleanDecodeAttribute {
  override val description: String = " is src1 unsigned? used everywhere in Lane and VFU. "
}
