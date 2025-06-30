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

object MaskPipeOpcode {
  def apply(t1DecodePattern: T1DecodePattern): MaskPipeOpcode = {
    Seq(
      t0 _ -> MaskUop0,
      t1 _ -> MaskUop1
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
}

case class MaskPipeOpcode(value: MaskPipeUop) extends UopDecodeAttribute[MaskPipeUop] {
  override val description: String = ""
}
