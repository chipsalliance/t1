// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isNarrow {
  def apply(t1DecodePattern: T1DecodePattern): isNarrow =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isNarrow(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vnclip.wi",
      "vnclip.wv",
      "vnclip.wx",
      "vnclipu.wi",
      "vnclipu.wv",
      "vnclipu.wx",
      "vnsra.wi",
      "vnsra.wv",
      "vnsra.wx",
      "vnsrl.wi",
      "vnsrl.wv",
      "vnsrl.wx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isNarrow(value: TriState) extends BooleanDecodeAttribute {
  override val description: String =
    " dual width of src will be convert to single width to dst. narrow can be the src of chain. as the dst of chain, only can be fed with Load. TODO: remove it as attribute. "
}
