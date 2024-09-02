// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object DecoderUop {
  def apply(t1DecodePattern: T1DecodePattern): DecoderUop = {
    val tpe: Option[DecoderUop] = Seq(
      isDivider.y(t1DecodePattern)    -> DivUOP(t1DecodePattern),
      isFloat.y(t1DecodePattern)      -> FloatUop(t1DecodePattern),
      isMultiplier.y(t1DecodePattern) -> MulUOP(t1DecodePattern),
      isAdder.y(t1DecodePattern)      -> AdderUOP(t1DecodePattern),
      isLogic.y(t1DecodePattern)      -> LogicUop(t1DecodePattern),
      isShift.y(t1DecodePattern)      -> ShiftUop(t1DecodePattern),
      isOther.y(t1DecodePattern)      -> OtherUop(t1DecodePattern),
      isZero.y(t1DecodePattern)       -> ZeroUOP(t1DecodePattern),
      isZvbb.y(t1DecodePattern)       -> ZvbbUOP(t1DecodePattern)
    ).collectFirst {
      case (fn, tpe) if fn => DecoderUop(tpe)
    }
    require(tpe.size <= 1)
    tpe.getOrElse(DecoderUop(UopDC))
  }
}

case class DecoderUop(value: Uop) extends UopDecodeAttribute[Uop] {
  override val description: String = "uop for mask unit."
}
