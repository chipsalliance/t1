// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

trait TopUopType extends Uop
object TopT0     extends TopUopType
object TopT1     extends TopUopType
object TopT2     extends TopUopType
object TopT3     extends TopUopType
object TopT5     extends TopUopType
object TopT6     extends TopUopType
object TopT7     extends TopUopType

object TopUop {
  def apply(t1DecodePattern: T1DecodePattern): TopUop  = {
    Seq(
      t0 _ -> TopT0,
      t1 _ -> TopT1,
      t2 _ -> TopT2,
      t3 _ -> TopT3,
      t5 _ -> TopT5,
      t6 _ -> TopT6,
      t7 _ -> TopT7
    ).collectFirst {
      case (fn, tpe) if fn(t1DecodePattern) => TopUop(tpe)
    }.getOrElse(TopUop(TopT0))
  }
  def t0(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i =>
      !(t1(t1DecodePattern)
        || t2(t1DecodePattern)
        || t3(t1DecodePattern)
        || t5(t1DecodePattern)
        || t6(t1DecodePattern)
        || t7(t1DecodePattern))
    )
    allMatched.contains(t1DecodePattern.instruction)
  }
  def t1(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfslide1down.vf",
      "vslide1down.vx",
      "vzext.vf2"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t2(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vslideup.vi",
      "vslideup.vx",
      "vzext.vf4"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t3(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfslide1up.vf",
      "vslide1up.vx",
      "vzext.vf8"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t5(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vsext.vf2"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t6(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vsext.vf4"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t7(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vsext.vf8"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
}

case class TopUop(value: TopUopType) extends UopDecodeAttribute[TopUopType] {
  override val description: String = "uop for mask unit."
}
