// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isAverage {
  def apply(t1DecodePattern: T1DecodePattern): isAverage =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isAverage(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vaadd.vv",
      "vaadd.vx",
      "vaaddu.vv",
      "vaaddu.vx",
      "vasub.vv",
      "vasub.vx",
      "vasubu.vv",
      "vasubu.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vcpop.m",
      "vfclass.v",
      "vfcvt.f.x.v",
      "vfcvt.f.xu.v",
      "vfcvt.rtz.x.f.v",
      "vfcvt.rtz.xu.f.v",
      "vfcvt.x.f.v",
      "vfcvt.xu.f.v",
      "vfirst.m",
      "vfmv.f.s",
      "vfmv.s.f",
      "vfncvt.f.f.w",
      "vfncvt.f.x.w",
      "vfncvt.f.xu.w",
      "vfncvt.rod.f.f.w",
      "vfncvt.rtz.x.f.w",
      "vfncvt.rtz.xu.f.w",
      "vfncvt.x.f.w",
      "vfncvt.xu.f.w",
      "vfrec7.v",
      "vfrsqrt7.v",
      "vfsqrt.v",
      "vfwcvt.f.f.v",
      "vfwcvt.f.x.v",
      "vfwcvt.f.xu.v",
      "vfwcvt.rtz.x.f.v",
      "vfwcvt.rtz.xu.f.v",
      "vfwcvt.x.f.v",
      "vfwcvt.xu.f.v",
      "vid.v",
      "viota.m",
      "vmsbf.m",
      "vmsif.m",
      "vmsof.m",
      "vmv.s.x",
      "vmv.x.s",
      "vsext.vf2",
      "vsext.vf4",
      "vsext.vf8",
      "vzext.vf2",
      "vzext.vf4",
      "vzext.vf8"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
}

case class isAverage(value: TriState) extends BooleanDecodeAttribute {
  override val description: String = "For adder, does it need to take care of saturate. TODO: add to uop "
}
