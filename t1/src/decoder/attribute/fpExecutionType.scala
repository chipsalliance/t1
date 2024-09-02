// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object FpExecutionType {
  trait Type          extends Uop  {
    def apply(t1DecodePattern: T1DecodePattern): Boolean
  }
  case object Compare extends Type {
    def apply(t1DecodePattern: T1DecodePattern): Boolean = isFcompare.y(t1DecodePattern)
  }
  case object Other   extends Type {
    def apply(t1DecodePattern: T1DecodePattern): Boolean = isFother.y(t1DecodePattern)
  }
  case object MA      extends Type {
    def apply(t1DecodePattern: T1DecodePattern): Boolean =
      !(isFcompare.y(t1DecodePattern) || isFother.y(t1DecodePattern))
  }
  case object Nil     extends Type {
    def apply(t1DecodePattern: T1DecodePattern): Boolean = {
      require(requirement = false, "unreachable")
      false
    }
  }
  def apply(t1DecodePattern: T1DecodePattern): Type = {
    val tpe = Seq(Compare, Other, MA).filter(tpe => tpe(t1DecodePattern))
    require(tpe.size <= 1)
    tpe.headOption.getOrElse(Nil)
  }
}

case class FpExecutionType(value: FpExecutionType.Type) extends UopDecodeAttribute[FpExecutionType.Type] {
  override val description: String = "float uop, goes to [[org.chipsalliance.t1.rtl.LaneFloatRequest.unitSelet]]"
}
