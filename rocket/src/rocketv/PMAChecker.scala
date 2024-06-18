// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.Instance

case class PMACheckerParameter() extends SerializableModuleParameter
class PMACheckerInterface(parameter: PMACheckerParameter) extends Bundle {
  val paddr = Input(UInt())
  val resp = Output(new Bundle {
    val cacheable = Bool()
    val r = Bool()
    val w = Bool()
    val pp = Bool()
    val al = Bool()
    val aa = Bool()
    val x = Bool()
    val eff = Bool()
  })
}

class PMAChecker(val parameter: PMACheckerParameter)
  extends FixedIORawModule(new PMACheckerInterface(parameter))
    with SerializableModule[PMACheckerParameter] {
  // main memory
  io.resp.cacheable := false.B
  // read
  io.resp.r := false.B
  // write
  io.resp.w := false.B
  // put partial
  io.resp.pp := false.B
  // logic
  io.resp.al := false.B
  // arithmetic
  io.resp.aa := false.B
  // exe
  io.resp.x := false.B
  // has side effects
  io.resp.eff := false.B
}
