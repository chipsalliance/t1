// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.montior

import chisel3._
import chisel3.properties.{Path, Property}
import chisel3.util.experimental.BoringUtils.tapAndRead
import org.chipsalliance.t1.rtl.V

class Issue(dut: V) {
  val valid: Bool = Wire(Bool())
  val instruction: UInt = Wire(UInt(32.W))

  valid := tapAndRead(dut.request.fire)
  instruction := tapAndRead(dut.request.bits.instruction)

  val issueProp = IO(Output(Property[Seq[Path]]()))
  issueProp := Property(Seq(valid, instruction))
}
