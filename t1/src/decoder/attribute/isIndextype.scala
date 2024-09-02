// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isIndextype {
  def apply(t1DecodePattern: T1DecodePattern): isIndextype =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isIndextype(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
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
      "vsuxei8.v"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isIndextype(value: TriState) extends BooleanDecodeAttribute {
  override val description: String = "TODO: remove it."
}
