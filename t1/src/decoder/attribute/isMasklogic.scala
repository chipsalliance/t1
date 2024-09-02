// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isMasklogic {
  def apply(t1DecodePattern: T1DecodePattern): isMasklogic =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isMasklogic(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vcpop.m",
      "vfirst.m",
      "viota.m",
      "vmand.mm",
      "vmandn.mm",
      "vmnand.mm",
      "vmnor.mm",
      "vmor.mm",
      "vmorn.mm",
      "vmsbf.m",
      "vmsif.m",
      "vmsof.m",
      "vmxnor.mm",
      "vmxor.mm"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isMasklogic(value: TriState) extends BooleanDecodeAttribute {
  override val description: String =
    "only one or two operators src is mask format(one element one bit). vl should align with src. if datapath is unaligned, need to take care the tail. "
}
