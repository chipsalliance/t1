// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

trait ShiftUopType extends Uop
object shiftUop0   extends ShiftUopType
object shiftUop1   extends ShiftUopType
object shiftUop2   extends ShiftUopType
object shiftUop4   extends ShiftUopType
object shiftUop6   extends ShiftUopType

object ShiftUop {
  def apply(t1DecodePattern: T1DecodePattern): Uop     = {
    Seq(
      t0 _ -> shiftUop0,
      t1 _ -> shiftUop1,
      t2 _ -> shiftUop2,
      t4 _ -> shiftUop4,
      t6 _ -> shiftUop6
    ).collectFirst {
      case (fn, tpe) if fn(t1DecodePattern) => tpe
    }.getOrElse(UopDC)
  }
  def t0(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vnsrl.wi",
      "vnsrl.wv",
      "vnsrl.wx",
      "vsrl.vi",
      "vsrl.vv",
      "vsrl.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t1(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vsll.vi",
      "vsll.vv",
      "vsll.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t2(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vnsra.wi",
      "vnsra.wv",
      "vnsra.wx",
      "vsra.vi",
      "vsra.vv",
      "vsra.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t4(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vssrl.vi",
      "vssrl.vv",
      "vssrl.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t6(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vssra.vi",
      "vssra.vv",
      "vssra.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
}

case class ShiftUop(value: ShiftUopType) extends UopDecodeAttribute[ShiftUopType] {
  override val description: String = ""
}
