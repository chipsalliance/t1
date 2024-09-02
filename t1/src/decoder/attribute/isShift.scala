// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isShift {
  def apply(t1DecodePattern: T1DecodePattern): isShift =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isShift(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vnsra.wi",
      "vnsra.wv",
      "vnsra.wx",
      "vnsrl.wi",
      "vnsrl.wv",
      "vnsrl.wx",
      "vsll.vi",
      "vsll.vv",
      "vsll.vx",
      "vsra.vi",
      "vsra.vv",
      "vsra.vx",
      "vsrl.vi",
      "vsrl.vv",
      "vsrl.vx",
      "vssra.vi",
      "vssra.vv",
      "vssra.vx",
      "vssrl.vi",
      "vssrl.vv",
      "vssrl.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isShift(value: TriState) extends BooleanDecodeAttribute {
  override val description: String = "goes to [[org.chipsalliance.t1.rtl.LaneShifter]]."
}
