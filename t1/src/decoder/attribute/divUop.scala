// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

trait DivUOPType extends Uop
object divUop0   extends DivUOPType
object divUop1   extends DivUOPType
object divUop8   extends DivUOPType
object divUop9   extends DivUOPType
object divUop10  extends DivUOPType

object DivUOP {
  def apply(t1DecodePattern: T1DecodePattern): Uop     = {
    Seq(
      t0 _  -> divUop0,
      t1 _  -> divUop1,
      t8 _  -> divUop8,
      t9 _  -> divUop9,
      t10 _ -> divUop10
    ).collectFirst {
      case (fn, tpe) if fn(t1DecodePattern) => tpe
    }.getOrElse(UopDC)
  }
  def t0(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vdiv.vv",
      "vdiv.vx",
      "vdivu.vv",
      "vdivu.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t1(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vrem.vv",
      "vrem.vx",
      "vremu.vv",
      "vremu.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t8(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfdiv.vf",
      "vfdiv.vv"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t9(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfsqrt.v"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t10(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfrdiv.vf"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
}
