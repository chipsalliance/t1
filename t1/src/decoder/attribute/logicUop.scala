// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

trait LogicUopType extends Uop
object logicUop0   extends LogicUopType
object logicUop1   extends LogicUopType
object logicUop2   extends LogicUopType
object logicUop4   extends LogicUopType
object logicUop5   extends LogicUopType
object logicUop6   extends LogicUopType
object logicUop8   extends LogicUopType
object logicUop9   extends LogicUopType

object LogicUop {
  def apply(t1DecodePattern: T1DecodePattern): Uop     = {
    Seq(
      t0 _ -> logicUop0,
      t1 _ -> logicUop1,
      t2 _ -> logicUop2,
      t4 _ -> logicUop4,
      t5 _ -> logicUop5,
      t6 _ -> logicUop6,
      t8 _ -> logicUop8,
      t9 _ -> logicUop9
    ).collectFirst {
      case (fn, tpe) if fn(t1DecodePattern) => tpe
    }.getOrElse(UopDC)
  }
  def t0(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vand.vi",
      "vand.vv",
      "vand.vx",
      "vmand.mm",
      "vredand.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t1(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmor.mm",
      "vor.vi",
      "vor.vv",
      "vor.vx",
      "vredor.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t2(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmxor.mm",
      "vredxor.vs",
      "vxor.vi",
      "vxor.vv",
      "vxor.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t4(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmandn.mm"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t5(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmorn.mm"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t6(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmxnor.mm"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t8(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmnand.mm"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t9(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmnor.mm"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
}
