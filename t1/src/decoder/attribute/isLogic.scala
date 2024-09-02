// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isLogic {
  def apply(t1DecodePattern: T1DecodePattern): isLogic =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isLogic(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vand.vi",
      "vand.vv",
      "vand.vx",
      "vmand.mm",
      "vmandn.mm",
      "vmnand.mm",
      "vmnor.mm",
      "vmor.mm",
      "vmorn.mm",
      "vmxnor.mm",
      "vmxor.mm",
      "vor.vi",
      "vor.vv",
      "vor.vx",
      "vredand.vs",
      "vredor.vs",
      "vredxor.vs",
      "vxor.vi",
      "vxor.vv",
      "vxor.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isLogic(value: TriState) extends BooleanDecodeAttribute {
  override val description: String =
    "Instruction should use [[org.chipsalliance.t1.rtl.decoder.TableGenerator.LaneDecodeTable.LogicUnit]]."
}
