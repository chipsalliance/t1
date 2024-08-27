// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.instantiable
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util.experimental.BitSet

object PMACheckerParameter {
  implicit def bitSetP: upickle.default.ReadWriter[BitSet] = upickle.default
    .readwriter[String]
    .bimap[BitSet](
      bs => bs.terms.map("b" + _.rawString).mkString("\n"),
      str => if(str.isEmpty) BitSet.empty else BitSet.fromString(str)
    )
  implicit def rwP: upickle.default.ReadWriter[PMACheckerParameter] = upickle.default.macroRW[PMACheckerParameter]
}

case class PMACheckerParameter(
  paddrBits:   Int,
  legal:       BitSet,
  cacheable:   BitSet,
  read:        BitSet,
  write:       BitSet,
  putPartial:  BitSet,
  logic:       BitSet,
  arithmetic:  BitSet,
  exec:        BitSet,
  sideEffects: BitSet)
    extends SerializableModuleParameter

class PMACheckerInterface(parameter: PMACheckerParameter) extends Bundle {
  val paddr = Input(UInt(parameter.paddrBits.W))
  val resp = Output(new PMACheckerResponse)
}

@instantiable
class PMAChecker(val parameter: PMACheckerParameter)
    extends FixedIORawModule(new PMACheckerInterface(parameter))
    with SerializableModule[PMACheckerParameter]
    with Public {
  // check exist a slave can consume this address.
  val legal_address = parameter.legal.matches(io.paddr)
  io.resp.cacheable := legal_address && (if(parameter.cacheable.isEmpty) false.B else parameter.cacheable.matches(io.paddr))
  io.resp.r := legal_address && (if(parameter.read.isEmpty) false.B else parameter.read.matches(io.paddr))
  io.resp.w := legal_address && (if(parameter.write.isEmpty) false.B else parameter.write.matches(io.paddr))
  io.resp.pp := legal_address && (if(parameter.putPartial.isEmpty) false.B else parameter.putPartial.matches(io.paddr))
  io.resp.al := legal_address && (if(parameter.logic.isEmpty) false.B else parameter.logic.matches(io.paddr))
  io.resp.aa := legal_address && (if(parameter.arithmetic.isEmpty) false.B else parameter.arithmetic.matches(io.paddr))
  io.resp.x := legal_address && (if(parameter.exec.isEmpty) false.B else parameter.exec.matches(io.paddr))
  io.resp.eff := legal_address && (if(parameter.sideEffects.isEmpty) false.B else parameter.sideEffects.matches(io.paddr))
}
