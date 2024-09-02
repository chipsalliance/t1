// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isId {
  def apply(t1DecodePattern: T1DecodePattern): isId =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isId(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vdiv.vv",
      "vdiv.vx",
      "vdivu.vv",
      "vdivu.vx",
      "vfdiv.vf",
      "vfdiv.vv",
      "vfncvt.f.f.w",
      "vfncvt.f.x.w",
      "vfncvt.f.xu.w",
      "vfncvt.rod.f.f.w",
      "vfncvt.rtz.x.f.w",
      "vfncvt.rtz.xu.f.w",
      "vfncvt.x.f.w",
      "vfncvt.xu.f.w",
      "vfrdiv.vf",
      "vfslide1down.vf",
      "vfslide1up.vf",
      "vfsqrt.v",
      "vfwadd.wf",
      "vfwadd.wv",
      "vfwsub.wf",
      "vfwsub.wv",
      "vid.v",
      "vnclip.wv",
      "vnclip.wx",
      "vnclipu.wv",
      "vnclipu.wx",
      "vnsra.wv",
      "vnsra.wx",
      "vnsrl.wv",
      "vnsrl.wx",
      "vrem.vv",
      "vrem.vx",
      "vremu.vv",
      "vremu.vx",
      "vslide1down.vx",
      "vslide1up.vx",
      "vslidedown.vi",
      "vslidedown.vx",
      "vslideup.vi",
      "vslideup.vx",
      "vwadd.wv",
      "vwadd.wx",
      "vwaddu.wv",
      "vwaddu.wx",
      "vwmacc.vv",
      "vwmacc.vx",
      "vwmaccsu.vv",
      "vwmaccsu.vx",
      "vwmaccu.vv",
      "vwmaccu.vx",
      "vwmaccus.vx",
      "vwredsum.vs",
      "vwredsumu.vs",
      "vwsub.wv",
      "vwsub.wx",
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

case class isId(value: TriState) extends BooleanDecodeAttribute {
  override val description: String =
    "write 0...vlmax to VRF. Lane other unit should handle it. TODO: remove it, it's a uop. "
}
