// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isFfo {
  def apply(t1DecodePattern: T1DecodePattern): isFfo =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isFfo(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vfirst.m",
      "vmsbf.m",
      "vmsif.m",
      "vmsof.m"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isFfo(value: TriState) extends BooleanDecodeAttribute {
  override val description: String =
    "find first one, lane will report if 1 is found, Sequencer should decide which is exactly the first 1 in lanes. after 1 is found, tell each lane, 1 has been found at which the corresponding location. lane will stale at stage2. TODO: should split into lane control uop "
}
