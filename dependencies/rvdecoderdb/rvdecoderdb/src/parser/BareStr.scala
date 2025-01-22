// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2023 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.rvdecoderdb.parser

object BareStr {
  def unapply(str: String): Option[BareStr] = Some(new BareStr(str))
}

/** This either be Instruction or InstructionSet(only for import) */
class BareStr(val name: String) extends Token {
  override def toString: String = name
}
