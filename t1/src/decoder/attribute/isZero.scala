// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isZero {
  def apply(t1DecodePattern: T1DecodePattern): isZero =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isZero(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vcompress.vm",
      "vfslide1down.vf",
      "vfslide1up.vf",
      "viota.m",
      "vmv1r.v",
      "vmv2r.v",
      "vmv4r.v",
      "vmv8r.v",
      "vsext.vf2",
      "vsext.vf4",
      "vsext.vf8",
      "vslide1down.vx",
      "vslide1up.vx",
      "vslidedown.vi",
      "vslidedown.vx",
      "vslideup.vi",
      "vslideup.vx",
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

case class isZero(value: TriState) extends BooleanDecodeAttribute {
  override val description: String = "goes to [[org.chipsalliance.t1.rtl.OtherUnit]]"
}
