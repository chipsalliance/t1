// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu.dpi

import chisel3._

case class VrfMonitorParameter(triggerDelay: Int)

class VrfMonitor(p: VrfMonitorParameter) extends DPIModule {
  val isImport: Boolean = true

  val clock = dpiTrigger("clock", Input(Bool()))

  val laneIdx = dpiIn("laneIdx", Input(UInt(32.W)))
  val vrfWriteValid = dpiIn("vrfWriteValid", Input(Bool()))

  override val trigger = s"always @(posedge ${clock.name}) #(${p.triggerDelay})"
}
