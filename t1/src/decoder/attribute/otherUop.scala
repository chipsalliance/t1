// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

trait OtherUopType extends Uop
object otherUop0   extends OtherUopType
object otherUop1   extends OtherUopType
object otherUop2   extends OtherUopType
object otherUop3   extends OtherUopType
object otherUop4   extends OtherUopType
object otherUop5   extends OtherUopType
object otherUop6   extends OtherUopType
object otherUop7   extends OtherUopType
object otherUop8   extends OtherUopType
object otherUop9   extends OtherUopType

object OtherUop {
  def apply(t1DecodePattern: T1DecodePattern): Uop     = {
    Seq(
      t0 _ -> otherUop0,
      t1 _ -> otherUop1,
      t2 _ -> otherUop2,
      t3 _ -> otherUop3,
      t4 _ -> otherUop4,
      t5 _ -> otherUop5,
      t6 _ -> otherUop6,
      t7 _ -> otherUop7,
      t8 _ -> otherUop8,
      t9 _ -> otherUop9
    ).collectFirst {
      case (fn, tpe) if fn(t1DecodePattern) => tpe
    }.getOrElse(UopDC)
  }
  def t0(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfirst.m"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t1(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmsbf.m"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t2(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmsof.m"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t3(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmsif.m"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t4(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vrgather.vi",
      "vrgather.vv",
      "vrgather.vx",
      "vrgatherei16.vv"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t5(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfmerge.vfm",
      "vfmv.v.f",
      "vmerge.vim",
      "vmv.v.i",
      "vmerge.vvm",
      "vmv.v.v",
      "vmerge.vxm",
      "vmv.v.x"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t6(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vnclip.wi",
      "vnclip.wv",
      "vnclip.wx",
      "vnclipu.wi",
      "vnclipu.wv",
      "vnclipu.wx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t7(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfmv.s.f",
      "vmv.s.x",
      "vmv.x.s"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t8(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vcpop.m"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t9(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vid.v"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
}
