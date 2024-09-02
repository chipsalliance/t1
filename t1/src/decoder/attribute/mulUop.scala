// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

trait MulUOPType extends Uop
object mulUop0   extends MulUOPType
object mulUop1   extends MulUOPType
object mulUop3   extends MulUOPType
object mulUop5   extends MulUOPType
object mulUop10  extends MulUOPType
object mulUop14  extends MulUOPType

object MulUOP {
  def apply(t1DecodePattern: T1DecodePattern): Uop     = {
    Seq(
      t0 _  -> mulUop0,
      t1 _  -> mulUop1,
      t3 _  -> mulUop3,
      t5 _  -> mulUop5,
      t10 _ -> mulUop10,
      t14 _ -> mulUop14
    ).collectFirst {
      case (fn, tpe) if fn(t1DecodePattern) => tpe
    }.getOrElse(UopDC)
  }
  def t0(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmul.vv",
      "vmul.vx",
      "vsmul.vv",
      "vsmul.vx",
      "vwmul.vv",
      "vwmul.vx",
      "vwmulsu.vv",
      "vwmulsu.vx",
      "vwmulu.vv",
      "vwmulu.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t1(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmadd.vv",
      "vmadd.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t3(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmulh.vv",
      "vmulh.vx",
      "vmulhsu.vv",
      "vmulhsu.vx",
      "vmulhu.vv",
      "vmulhu.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t5(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmacc.vv",
      "vmacc.vx",
      "vwmacc.vv",
      "vwmacc.vx",
      "vwmaccsu.vv",
      "vwmaccsu.vx",
      "vwmaccu.vv",
      "vwmaccu.vx",
      "vwmaccus.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t10(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq(
      "vnmsub.vv",
      "vnmsub.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t14(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq(
      "vnmsac.vv",
      "vnmsac.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
}

case class MulUOP(value: MulUOPType) extends UopDecodeAttribute[MulUOPType] {
  override val description: String = ""
}
