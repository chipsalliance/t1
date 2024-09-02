// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isFirstwiden {
  def apply(t1DecodePattern: T1DecodePattern): isFirstwiden =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isFirstwiden(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vfncvt.f.f.w",
      "vfncvt.f.x.w",
      "vfncvt.f.xu.w",
      "vfncvt.rod.f.f.w",
      "vfncvt.rtz.x.f.w",
      "vfncvt.rtz.xu.f.w",
      "vfncvt.x.f.w",
      "vfncvt.xu.f.w",
      "vfwadd.wf",
      "vfwadd.wv",
      "vfwsub.wf",
      "vfwsub.wv",
      "vnclip.wv",
      "vnclip.wx",
      "vnclipu.wv",
      "vnclipu.wx",
      "vnsra.wv",
      "vnsra.wx",
      "vnsrl.wv",
      "vnsrl.wx",
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

case class isFirstwiden(value: TriState) extends BooleanDecodeAttribute {
  override val description: String =
    "There are two types of widen: - vd -> widen. - vs2, vd -> widen. This op will widen vs2. TODO: remove it as attribute."
}
