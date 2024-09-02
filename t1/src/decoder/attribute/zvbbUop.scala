// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

trait ZvbbUOPType extends Uop
object zvbbUop0   extends ZvbbUOPType // brev
object zvbbUop1   extends ZvbbUOPType // brev8
object zvbbUop2   extends ZvbbUOPType // rev8
object zvbbUop3   extends ZvbbUOPType // clz
object zvbbUop4   extends ZvbbUOPType // ctz
object zvbbUop5   extends ZvbbUOPType // rol
object zvbbUop6   extends ZvbbUOPType // ror
object zvbbUop7   extends ZvbbUOPType // wsll
object zvbbUop8   extends ZvbbUOPType // andn

object ZvbbUOP {
  def apply(t1DecodePattern: T1DecodePattern): Uop     = {
    Seq(
      t0 _ -> zvbbUop0,
      t1 _ -> zvbbUop1,
      t2 _ -> zvbbUop2,
      t3 _ -> zvbbUop3,
      t4 _ -> zvbbUop4,
      t5 _ -> zvbbUop5,
      t6 _ -> zvbbUop6,
      t7 _ -> zvbbUop7,
      t8 _ -> zvbbUop8
    ).collectFirst {
      case (fn, tpe) if fn(t1DecodePattern) => tpe
    }.getOrElse(UopDC)
  }
  def t0(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vbrev.v"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t1(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vbrev8.v"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t2(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vrev8.v"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t3(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vclz.v"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t4(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vctz.v"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t5(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vrol.vv",
      "vrol.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t6(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vror.vv",
      "vror.vx",
      "vror.vi"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t7(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vwsll.vv",
      "vwsll.vx",
      "vwsll.vi"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t8(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vandn.vv",
      "vandn.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
}
