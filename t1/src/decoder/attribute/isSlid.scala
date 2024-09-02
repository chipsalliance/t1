// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isSlid {
  def apply(t1DecodePattern: T1DecodePattern): isSlid =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isSlid(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vfslide1down.vf",
      "vfslide1up.vf",
      "vslide1down.vx",
      "vslide1up.vx",
      "vslidedown.vi",
      "vslidedown.vx",
      "vslideup.vi",
      "vslideup.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isSlid(value: TriState) extends BooleanDecodeAttribute {
  override val description: String =
    "used in Sequencer mask unit, decide which vrf should be read. send read request to corresponding lane, lane will respond data to Sequencer. Sequencer will write data to VD. mask unit is work as the router here. TODO: opimize mask unit. "
}
