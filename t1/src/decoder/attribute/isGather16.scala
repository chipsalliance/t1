// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isGather16 {
  def apply(t1DecodePattern: T1DecodePattern): isGather16 =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isGather16(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vrgatherei16.vv"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isGather16(value: TriState) extends BooleanDecodeAttribute {
  override val description: String =
    "same with [[gather]] ignore sew, address width is fixed to 16. @note When SEW=8, vrgather.vv can only reference vector elements 0-255. The vrgatherei16 form can index 64K elements, and can also be used to reduce the register capacity needed to hold indices when SEW > 16. "
}
