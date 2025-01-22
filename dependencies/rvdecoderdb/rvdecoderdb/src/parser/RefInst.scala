// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2023 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.rvdecoderdb.parser

object RefInst {
  def unapply(str: String): Option[RefInst] = str match {
    case s"$set::$instr" => Some(new RefInst(set, instr))
    case _               => None
  }
}

class RefInst(val set: String, val inst: String) extends Token {
  override def toString: String = s"RefInst($set, $inst)"
}
