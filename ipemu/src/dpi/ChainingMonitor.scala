// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu.dpi

import chisel3._

case class ChainingMonitorParameter(slotNum: Int, triggerDelay: Int)

class ChainingMonitor(p: ChainingMonitorParameter) extends DPIModuleLegacy {
  val isImport: Boolean = true

  val clock = dpiTrigger("clock", Input(Bool()))

  val laneIdx = dpiIn("laneIdx", Input(UInt(32.W)))
  val slotOccupied = dpiIn("slotOccupied", Input(UInt(p.slotNum.W)))

  override val trigger = s"always @(posedge ${clock.name}) #(${p.triggerDelay})"
}
