// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2023 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.rvdecoderdb.parser

import org.chipsalliance.rvdecoderdb.Encoding

class RawInstruction(tokens: Seq[Token]) {
  def importInstruction: Option[(String, String)] = Option
    .when(tokens.head == Import && tokens.exists(t => t.isInstanceOf[RefInst]))(tokens.collectFirst {
      case r: RefInst => (r.set, r.inst)
    }.get)

  def importInstructionSet: Option[String] = Option
    .when(tokens.head == Import && importInstruction.isEmpty)(tokens.collectFirst {
      case str: BareStr => str.name
    })
    .flatten

  def pseudoInstruction: Option[(String, String)] = Option
    .when(tokens.head == PseudoOp)(tokens.collectFirst {
      case r: RefInst => (r.set, r.inst)
    }.get)

  def isNormal: Boolean =
    importInstruction.isEmpty &&
      importInstructionSet.isEmpty &&
      pseudoInstruction.isEmpty

  def nameOpt: Option[String] = Option
    .when(importInstruction.isEmpty && importInstructionSet.isEmpty)(tokens.collectFirst {
      case str: BareStr => str.name
    })
    .flatten

  def name: String = nameOpt.getOrElse(throw new Exception(s"error at token: ${tokens.mkString(",")}"))

  def args: Seq[ArgLUT] = tokens.flatMap {
    case a: ArgLUT => Some(a)
    case _ => None
  }

  def encoding: Encoding = tokens.flatMap {
    case b: BitValue => Some(Encoding(b.value << b.bit.toInt, BigInt(1) << b.bit.toInt))
    case b: FixedRangeValue =>
      Some(Encoding(b.value << b.lsb.toInt, (b.lsb.toInt to b.msb.toInt).map(BigInt(1) << _).sum))
    case _ => None
  }.reduce((l, r) => Encoding(l.value + r.value, l.mask + r.mask))

//  def encoding: Encoding =

  override def toString: String = {
    if (importInstruction.nonEmpty) s"import instruction: ${importInstruction.get._2} from ${importInstruction.get._1}"
    else if (importInstructionSet.nonEmpty) s"import set: ${importInstructionSet.get}"
    else if (pseudoInstruction.nonEmpty) s"$name(pseudo ${pseudoInstruction.get._2} from ${pseudoInstruction.get._1})"
    else s"$name:$encoding"
  }
}
