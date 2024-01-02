// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu.dpi

import chisel3._

case class PeekLsuEnqParameter(mshrSize: Int, triggerDelay: Int)

class PeekLsuEnq(p: PeekLsuEnqParameter) extends DPIModule {
  val isImport: Boolean = true
  val clock = dpiTrigger("clock", Input(Bool()))
  val enq = dpiIn("enq", Input(UInt(p.mshrSize.W)))

  override val trigger = s"always @(posedge ${clock.name}) #(${p.triggerDelay})"
}
