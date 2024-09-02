// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isGather {
  def apply(t1DecodePattern: T1DecodePattern): isGather =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isGather(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vrgather.vi",
      "vrgather.vv",
      "vrgather.vx",
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

case class isGather(value: TriState) extends BooleanDecodeAttribute {
  override val description: String =
    "lane will read index from vs1, send to Sequencer. mask unit will calculate vrf address based on the vs1 from lane, and send read request to lanes, lanes should read it and send vs2 to Sequencer. Sequencer will write vd at last. address: 0 -> vlmax(sew decide address width.) "
}
