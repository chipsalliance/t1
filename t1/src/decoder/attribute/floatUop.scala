// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

trait FloatUopType extends Uop
object FUT0        extends FloatUopType
object FUT1        extends FloatUopType
object FUT2        extends FloatUopType
object FUT3        extends FloatUopType
object FUT4        extends FloatUopType
object FUT5        extends FloatUopType
object FUT6        extends FloatUopType
object FUT7        extends FloatUopType
object FUT8        extends FloatUopType
object FUT9        extends FloatUopType
object FUT10       extends FloatUopType
object FUT12       extends FloatUopType
object FUT13       extends FloatUopType
object FUT14       extends FloatUopType

object FloatUop {
  def apply(t1DecodePattern: T1DecodePattern) = {
    Seq(
      t0 _  -> FUT0,
      t1 _  -> FUT1,
      t2 _  -> FUT2,
      t3 _  -> FUT3,
      t4 _  -> FUT4,
      t5 _  -> FUT5,
      t6 _  -> FUT6,
      t7 _  -> FUT7,
      t8 _  -> FUT8,
      t9 _  -> FUT9,
      t10 _ -> FUT10,
      t12 _ -> FUT12,
      t13 _ -> FUT13,
      t14 _ -> FUT14
    ).collectFirst {
      case (fn, tpe) if fn(t1DecodePattern) => tpe
    }.getOrElse(UopDC)
  }
  def t0(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i =>
      !(t1(t1DecodePattern)
        || t2(t1DecodePattern)
        || t3(t1DecodePattern)
        || t4(t1DecodePattern)
        || t5(t1DecodePattern)
        || t6(t1DecodePattern)
        || t7(t1DecodePattern)
        || t8(t1DecodePattern)
        || t9(t1DecodePattern)
        || t10(t1DecodePattern)
        || t12(t1DecodePattern)
        || t13(t1DecodePattern)
        || t14(t1DecodePattern))
    )
    allMatched.contains(t1DecodePattern.instruction)
  }
  def t1(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfmsac.vf",
      "vfmsac.vv",
      "vfsgnj.vf",
      "vfsgnj.vv",
      "vmfeq.vf",
      "vmfeq.vv"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t2(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfnmsac.vf",
      "vfnmsac.vv",
      "vfsgnjn.vf",
      "vfsgnjn.vv",
      "vmflt.vf",
      "vmflt.vv"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t3(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfnmacc.vf",
      "vfnmacc.vv",
      "vfsgnjx.vf",
      "vfsgnjx.vv",
      "vmfle.vf",
      "vmfle.vv"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t4(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfclass.v",
      "vfmadd.vf",
      "vfmadd.vv",
      "vmfgt.vf"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t5(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfmsub.vf",
      "vfmsub.vv",
      "vmfge.vf"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t6(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfnmsub.vf",
      "vfnmsub.vv",
      "vfrec7.v"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t7(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfnmadd.vf",
      "vfnmadd.vv",
      "vfrsqrt7.v"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t8(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfadd.vf",
      "vfadd.vv",
      "vfcvt.f.x.v",
      "vfcvt.f.xu.v",
      "vfmin.vf",
      "vfmin.vv",
      "vfredmin.vs",
      "vfredosum.vs",
      "vfredusum.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t9(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfcvt.xu.f.v",
      "vfsub.vf",
      "vfsub.vv"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t10(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfcvt.x.f.v"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t12(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfmax.vf",
      "vfmax.vv",
      "vfredmax.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t13(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfcvt.rtz.xu.f.v",
      "vfrsub.vf"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t14(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfcvt.rtz.x.f.v"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
}
