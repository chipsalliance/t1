// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2023 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.rvdecoderdb.parser

object BitValue {
  def unapply(str: String): Option[BitValue] = str match {
    case s"${bit}=${value}" =>
      Some(
        new BitValue(
          bit.toInt,
          value match {
            case s"0b$bstr" => BigInt(bstr, 2)
            case s"0x$xstr" => BigInt(xstr, 16)
            case dstr       => BigInt(dstr)
          }
        )
      )
    case _ => None
  }
}

class BitValue(val bit: BigInt, val value: BigInt) extends Token {
  override def toString: String = s"$bit=$value"
}
