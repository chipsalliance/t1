// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

trait ZvkUOPType extends Uop
object zvkUop0   extends ZvkUOPType
object zvkUop1   extends ZvkUOPType
object zvkUop2   extends ZvkUOPType
object zvkUop3   extends ZvkUOPType
object zvkUop4   extends ZvkUOPType
object zvkUop5   extends ZvkUOPType
object zvkUop6   extends ZvkUOPType
object zvkUop7   extends ZvkUOPType
object zvkUop8   extends ZvkUOPType
object zvkUop9   extends ZvkUOPType
object zvkUop10  extends ZvkUOPType
object zvkUop11  extends ZvkUOPType
object zvkUop12  extends ZvkUOPType
object zvkUop13  extends ZvkUOPType

object ZvkUOP {
  def apply(t1DecodePattern: T1DecodePattern): Uop     = {
    Seq(
      t0 _  -> zvkUop0,
      t1 _  -> zvkUop1,
      t2 _  -> zvkUop2,
      t3 _  -> zvkUop3,
      t4 _  -> zvkUop4,
      t5 _  -> zvkUop5,
      t6 _  -> zvkUop6,
      t7 _  -> zvkUop7,
      t8 _  -> zvkUop8,
      t9 _  -> zvkUop9,
      t10 _ -> zvkUop10,
      t11 _ -> zvkUop11,
      t12 _ -> zvkUop12,
      t13 _ -> zvkUop13
    ).collectFirst {
      case (fn, tpe) if fn(t1DecodePattern) => tpe
    }.getOrElse(UopDC)
  }
  def t0(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vghsh.vv",
      "vsm3c.vi" // reuse for zvk256
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t1(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vgmul.vv",
      "vsm3me.vv" // reuse for zvk256
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t2(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vaesdf.vv",
      "vaesdf.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t3(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vaesdm.vv",
      "vaesdm.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t4(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vaesef.vv",
      "vaesef.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t5(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vaesem.vv",
      "vaesem.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t6(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vaesz.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t7(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vaeskf1.vi"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t8(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vaeskf2.vi"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t9(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vsha2ms.vv"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t10(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq(
      "vsha2ch.vv"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t11(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq(
      "vsm4k.vi"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t12(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq(
      "vsm4r.vv",
      "vsm4r.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t13(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq(
      "vsha2cl.vv"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
}
