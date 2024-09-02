// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder

import org.chipsalliance.rvdecoderdb.Instruction

object T1CustomInstruction {
  implicit def rw: upickle.default.ReadWriter[T1CustomInstruction] = upickle.default.macroRW[T1CustomInstruction]
}

// TODO: other field will be fill in the future, e.g. something that user can config, e.g.
//       readRS1, readRD?
case class T1CustomInstruction(instruction: Instruction)
