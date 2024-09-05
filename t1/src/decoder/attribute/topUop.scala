// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

trait TopUopType extends Uop
object TopT0     extends TopUopType
object TopT1     extends TopUopType
object TopT2     extends TopUopType
object TopT3     extends TopUopType
object TopT4     extends TopUopType
object TopT5     extends TopUopType
object TopT6     extends TopUopType
object TopT7     extends TopUopType
object TopT8     extends TopUopType
object TopT9     extends TopUopType
object TopT10    extends TopUopType
object TopT11    extends TopUopType
object TopT12    extends TopUopType
object TopT13    extends TopUopType
object TopT14    extends TopUopType
object TopT15    extends TopUopType
object TopT16    extends TopUopType
object TopT17    extends TopUopType
object TopT18    extends TopUopType
object TopT19    extends TopUopType
object TopT20    extends TopUopType
object TopT21    extends TopUopType
object TopT22    extends TopUopType
object TopT23    extends TopUopType
object TopT24    extends TopUopType
object TopT25    extends TopUopType
object TopT26    extends TopUopType
object TopT27    extends TopUopType
object TopT28    extends TopUopType
object TopT29    extends TopUopType
object TopT30    extends TopUopType
object TopT31    extends TopUopType

object TopUop {
  def apply(t1DecodePattern: T1DecodePattern): TopUop  = {
    Seq(
      t0 _  -> TopT0,
      t1 _  -> TopT1,
      t2 _  -> TopT2,
      t3 _  -> TopT3,
      t4 _  -> TopT4,
      t5 _  -> TopT5,
      t6 _  -> TopT6,
      t7 _  -> TopT7,
      t8 _  -> TopT8,
      t9 _  -> TopT9,
      t10 _ -> TopT10,
      t11 _ -> TopT11,
      t12 _ -> TopT12,
      t13 _ -> TopT13,
      t14 _ -> TopT14,
      t15 _ -> TopT15,
      t16 _ -> TopT16,
      t17 _ -> TopT17,
      t18 _ -> TopT18,
      t19 _ -> TopT19,
      t20 _ -> TopT20,
      t21 _ -> TopT21,
      t22 _ -> TopT22,
      t23 _ -> TopT23,
      t24 _ -> TopT24,
      t25 _ -> TopT25,
      t26 _ -> TopT26,
      t27 _ -> TopT27,
      t28 _ -> TopT28,
      t29 _ -> TopT29,
      t30 _ -> TopT30,
      t31 _ -> TopT31
    ).collectFirst {
      case (fn, tpe) if fn(t1DecodePattern) => TopUop(tpe)
    }.getOrElse(TopUop(TopT0))
  }
  def t0(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vslidedown.vi",
      "vslidedown.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t1(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vslideup.vi",
      "vslideup.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t2(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq("vslide1down.vx")
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t3(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq("vslide1up.vx")
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t4(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vrgather.vv"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t5(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq(
      "vrgatherei16.vv"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t6(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq()
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t7(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq()
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t8(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq("viota.m")
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t9(t1DecodePattern: T1DecodePattern):    Boolean = {
    val allMatched: Seq[String] = Seq("vcompress.vm")
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t10(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfmv.s.f",
      "vmv.s.x"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t11(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfmv.f.s",
      "vmv.x.s"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t12(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq()
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t13(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq()
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t14(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmsbf.m",
      "vmsif.m",
      "vmsof.m"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t15(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq("vfirst.m")
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t16(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq(
      "vcpop.m",
      "vredmax.vs",
      "vredmaxu.vs",
      "vredmin.vs",
      "vredminu.vs",
      "vredsum.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t17(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq(
      "vwredsum.vs",
      "vwredsumu.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t18(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq(
      "vredand.vs",
      "vredor.vs",
      "vredxor.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t19(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfredmax.vs",
      "vfredmin.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t20(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq("vfredusum.vs")
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t21(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq("vfredosum.vs")
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t22(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq("vfwredusum.vs")
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t23(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq("vfwredosum.vs")
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t24(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq(
      "vmadc.vi",
      "vmadc.vim",
      "vmadc.vv",
      "vmadc.vvm",
      "vmadc.vx",
      "vmadc.vxm",
      "vmfeq.vf",
      "vmfeq.vv",
      "vmfge.vf",
      "vmfgt.vf",
      "vmfle.vf",
      "vmfle.vv",
      "vmflt.vf",
      "vmflt.vv",
      "vmfne.vf",
      "vmfne.vv",
      "vmsbc.vv",
      "vmsbc.vvm",
      "vmsbc.vx",
      "vmsbc.vxm",
      "vmseq.vi",
      "vmseq.vv",
      "vmseq.vx",
      "vmsgt.vi",
      "vmsgt.vx",
      "vmsgtu.vi",
      "vmsgtu.vx",
      "vmsle.vi",
      "vmsle.vv",
      "vmsle.vx",
      "vmsleu.vi",
      "vmsleu.vv",
      "vmsleu.vx",
      "vmslt.vv",
      "vmslt.vx",
      "vmsltu.vv",
      "vmsltu.vx",
      "vmsne.vi",
      "vmsne.vv",
      "vmsne.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t25(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq()
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t26(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq("vzext.vf2")
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t27(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq("vsext.vf2")
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t28(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq("vzext.vf4")
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t29(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq("vsext.vf4")
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t30(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq("vzext.vf8")
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t31(t1DecodePattern: T1DecodePattern):   Boolean = {
    val allMatched: Seq[String] = Seq("vsext.vf8")
    allMatched.contains(t1DecodePattern.instruction.name)
  }
}

case class TopUop(value: TopUopType) extends UopDecodeAttribute[TopUopType] {
  override val description: String = "uop for mask unit."
}
