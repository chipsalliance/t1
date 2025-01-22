// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2023 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.rvdecoderdb.parser

case class RawInstructionSet(name: String, ratified: Boolean, custom: Boolean, rawInstructions: Seq[RawInstruction])
