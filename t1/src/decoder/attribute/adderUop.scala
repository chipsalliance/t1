// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

trait AdderUOPType extends Uop
object addUop0     extends AdderUOPType
object addUop1     extends AdderUOPType
object addUop2     extends AdderUOPType
object addUop3     extends AdderUOPType
object addUop4     extends AdderUOPType
object addUop6     extends AdderUOPType
object addUop7     extends AdderUOPType
object addUop8     extends AdderUOPType
object addUop9     extends AdderUOPType
object addUop10    extends AdderUOPType
object addUop11    extends AdderUOPType

object AdderUOP {
  def apply(t1DecodePattern: T1DecodePattern): Uop     = {
    Seq(
      t0 _  -> addUop0,
      t1 _  -> addUop1,
      t2 _  -> addUop2,
      t3 _  -> addUop3,
      t4 _  -> addUop4,
      t6 _  -> addUop6,
      t7 _  -> addUop7,
      t8 _  -> addUop8,
      t9 _  -> addUop9,
      t10 _ -> addUop10,
      t11 _ -> addUop11
    ).collectFirst {
      case (fn, tpe) if fn(t1DecodePattern) => tpe
    }.getOrElse(UopDC)
  }
  def t0(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vaadd.vv",
      "vaadd.vx",
      "vaaddu.vv",
      "vaaddu.vx",
      "vadd.vi",
      "vadd.vv",
      "vadd.vx",
      "vredsum.vs",
      "vsadd.vi",
      "vsadd.vv",
      "vsadd.vx",
      "vsaddu.vi",
      "vsaddu.vv",
      "vsaddu.vx",
      "vwadd.vv",
      "vwadd.vx",
      "vwadd.wv",
      "vwadd.wx",
      "vwaddu.vv",
      "vwaddu.vx",
      "vwaddu.wv",
      "vwaddu.wx",
      "vwredsum.vs",
      "vwredsumu.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t1(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vasub.vv",
      "vasub.vx",
      "vasubu.vv",
      "vasubu.vx",
      "vrsub.vi",
      "vrsub.vx",
      "vssub.vv",
      "vssub.vx",
      "vssubu.vv",
      "vssubu.vx",
      "vsub.vv",
      "vsub.vx",
      "vwsub.vv",
      "vwsub.vx",
      "vwsub.wv",
      "vwsub.wx",
      "vwsubu.vv",
      "vwsubu.vx",
      "vwsubu.wv",
      "vwsubu.wx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t2(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmslt.vv",
      "vmslt.vx",
      "vmsltu.vv",
      "vmsltu.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t3(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmsle.vi",
      "vmsle.vv",
      "vmsle.vx",
      "vmsleu.vi",
      "vmsleu.vv",
      "vmsleu.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t4(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmsgt.vi",
      "vmsgt.vx",
      "vmsgtu.vi",
      "vmsgtu.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t6(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmax.vv",
      "vmax.vx",
      "vmaxu.vv",
      "vmaxu.vx",
      "vredmax.vs",
      "vredmaxu.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t7(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmin.vv",
      "vmin.vx",
      "vminu.vv",
      "vminu.vx",
      "vredmin.vs",
      "vredminu.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t8(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmseq.vi",
      "vmseq.vv",
      "vmseq.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t9(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmsne.vi",
      "vmsne.vv",
      "vmsne.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t10(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq(
      "vadc.vim",
      "vadc.vvm",
      "vadc.vxm",
      "vmadc.vi",
      "vmadc.vim",
      "vmadc.vv",
      "vmadc.vvm",
      "vmadc.vx",
      "vmadc.vxm"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t11(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmsbc.vv",
      "vmsbc.vvm",
      "vmsbc.vx",
      "vmsbc.vxm",
      "vsbc.vvm",
      "vsbc.vxm"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
}
