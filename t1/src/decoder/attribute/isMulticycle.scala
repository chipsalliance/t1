// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isMulticycle {
  def apply(t1DecodePattern: T1DecodePattern): isMulticycle =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isMulticycle(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vdiv.vv",
      "vdiv.vx",
      "vdivu.vv",
      "vdivu.vx",
      "vfadd.vf",
      "vfadd.vv",
      "vfclass.v",
      "vfcvt.f.x.v",
      "vfcvt.f.xu.v",
      "vfcvt.rtz.x.f.v",
      "vfcvt.rtz.xu.f.v",
      "vfcvt.x.f.v",
      "vfcvt.xu.f.v",
      "vfdiv.vf",
      "vfdiv.vv",
      "vfmacc.vf",
      "vfmacc.vv",
      "vfmadd.vf",
      "vfmadd.vv",
      "vfmax.vf",
      "vfmax.vv",
      "vfmin.vf",
      "vfmin.vv",
      "vfmsac.vf",
      "vfmsac.vv",
      "vfmsub.vf",
      "vfmsub.vv",
      "vfmul.vf",
      "vfmul.vv",
      "vfmv.f.s",
      "vfncvt.f.f.w",
      "vfncvt.f.x.w",
      "vfncvt.f.xu.w",
      "vfncvt.rod.f.f.w",
      "vfncvt.rtz.x.f.w",
      "vfncvt.rtz.xu.f.w",
      "vfncvt.x.f.w",
      "vfncvt.xu.f.w",
      "vfnmacc.vf",
      "vfnmacc.vv",
      "vfnmadd.vf",
      "vfnmadd.vv",
      "vfnmsac.vf",
      "vfnmsac.vv",
      "vfnmsub.vf",
      "vfnmsub.vv",
      "vfrdiv.vf",
      "vfrec7.v",
      "vfredmax.vs",
      "vfredmin.vs",
      "vfredosum.vs",
      "vfredusum.vs",
      "vfrsqrt7.v",
      "vfrsub.vf",
      "vfsgnj.vf",
      "vfsgnj.vv",
      "vfsgnjn.vf",
      "vfsgnjn.vv",
      "vfsgnjx.vf",
      "vfsgnjx.vv",
      "vfsqrt.v",
      "vfsub.vf",
      "vfsub.vv",
      "vfwadd.vf",
      "vfwadd.vv",
      "vfwadd.wf",
      "vfwadd.wv",
      "vfwcvt.f.f.v",
      "vfwcvt.f.x.v",
      "vfwcvt.f.xu.v",
      "vfwcvt.rtz.x.f.v",
      "vfwcvt.rtz.xu.f.v",
      "vfwcvt.x.f.v",
      "vfwcvt.xu.f.v",
      "vfwmacc.vf",
      "vfwmacc.vv",
      "vfwmsac.vf",
      "vfwmsac.vv",
      "vfwmul.vf",
      "vfwmul.vv",
      "vfwnmacc.vf",
      "vfwnmacc.vv",
      "vfwnmsac.vf",
      "vfwnmsac.vv",
      "vfwredosum.vs",
      "vfwredusum.vs",
      "vfwsub.vf",
      "vfwsub.vv",
      "vfwsub.wf",
      "vfwsub.wv",
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
      "vrem.vv",
      "vrem.vx",
      "vremu.vv",
      "vremu.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isMulticycle(value: TriState) extends BooleanDecodeAttribute {
  override val description: String = "TODO: remove? only Div or customer"
}
