// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

trait MaskPipeUop extends Uop
object MaskUop0   extends MaskPipeUop
object MaskUop1   extends MaskPipeUop
object MaskUop2   extends MaskPipeUop
object MaskUop3   extends MaskPipeUop
object MaskUop4   extends MaskPipeUop
object MaskUop5   extends MaskPipeUop
object MaskUop6   extends MaskPipeUop
object MaskUop7   extends MaskPipeUop
object MaskUop8   extends MaskPipeUop
object MaskUop9   extends MaskPipeUop
object MaskUop10  extends MaskPipeUop
object MaskUop11  extends MaskPipeUop
// 0000 x => extend x?4:2                       [0,1]
// 0001 x => gather x?16:sew                    [2,3]
// 001 xy => slide  x?up:down   y?s:1           [4,7]
// 010 xx => 0: add 1: logic 2: float 3: order  [8,11]
object MaskPipeOpcode {
  def apply(t1DecodePattern: T1DecodePattern): MaskPipeOpcode = {
    Seq(
      t0 _  -> MaskUop0,
      t1 _  -> MaskUop1,
      t2 _  -> MaskUop2,
      t3 _  -> MaskUop3,
      t4 _  -> MaskUop4,
      t5 _  -> MaskUop5,
      t6 _  -> MaskUop6,
      t7 _  -> MaskUop7,
      t8 _  -> MaskUop8,
      t9 _  -> MaskUop9,
      t10 _ -> MaskUop10,
      t11 _ -> MaskUop11
    ).collectFirst {
      case (fn, tpe) if fn(t1DecodePattern) => MaskPipeOpcode(tpe)
    }.getOrElse(MaskPipeOpcode(MaskUop0))
  }

  def t0(t1DecodePattern: T1DecodePattern): Boolean = {
    val isCrossWrite = Seq(
      "vwadd.vv",
      "vwadd.vx",
      "vwadd.wv",
      "vwadd.wx",
      "vwaddu.vv",
      "vwaddu.vx",
      "vwaddu.wv",
      "vwaddu.wx",
      "vwmacc.vv",
      "vwmacc.vx",
      "vwmaccsu.vv",
      "vwmaccsu.vx",
      "vwmaccu.vv",
      "vwmaccu.vx",
      "vwmaccus.vx",
      "vwmul.vv",
      "vwmul.vx",
      "vwmulsu.vv",
      "vwmulsu.vx",
      "vwmulu.vv",
      "vwmulu.vx",
      "vwsub.vv",
      "vwsub.vx",
      "vwsub.wv",
      "vwsub.wx",
      "vwsubu.vv",
      "vwsubu.vx",
      "vwsubu.wv",
      "vwsubu.wx",
      // rv_zvbb
      "vwsll.vv",
      "vwsll.vx",
      "vwsll.vi"
    )
    val extend       = Seq(
      "vsext.vf2",
      "vzext.vf2"
    )
    val allMatched: Seq[String] = extend ++ isCrossWrite
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t1(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vsext.vf4",
      "vzext.vf4"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }

  def t2(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vrgather.vv"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }

  def t3(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vrgatherei16.vv"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }

  def t4(t1DecodePattern: T1DecodePattern):  Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfslide1down.vf",
      "vslide1down.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t5(t1DecodePattern: T1DecodePattern):  Boolean = {
    val allMatched: Seq[String] = Seq(
      "vslidedown.vi",
      "vslidedown.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t6(t1DecodePattern: T1DecodePattern):  Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfslide1up.vf",
      "vslide1up.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t7(t1DecodePattern: T1DecodePattern):  Boolean = {
    val allMatched: Seq[String] = Seq(
      "vslideup.vi",
      "vslideup.vx"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t8(t1DecodePattern: T1DecodePattern):  Boolean = {
    val allMatched: Seq[String] = Seq(
      "vcpop.m",
      "vredmax.vs",
      "vredmaxu.vs",
      "vredmin.vs",
      "vredminu.vs",
      "vredsum.vs",
      "vwredsum.vs",
      "vwredsumu.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t9(t1DecodePattern: T1DecodePattern):  Boolean = {
    val allMatched: Seq[String] = Seq(
      "vredand.vs",
      "vredor.vs",
      "vredxor.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t10(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfredmax.vs",
      "vfredmin.vs",
      "vfredusum.vs",
      "vfwredusum.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def t11(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched: Seq[String] = Seq(
      "vfredosum.vs",
      "vfwredosum.vs"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
}

case class MaskPipeOpcode(value: MaskPipeUop) extends UopDecodeAttribute[MaskPipeUop] {
  override val description: String = ""
}
