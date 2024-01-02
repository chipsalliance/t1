// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu.dpi

import chisel3._

case class PeekIssueParameter(instIdxBits: Int, triggerDelay: Int)

class PeekIssue(p: PeekIssueParameter) extends DPIModuleLegacy {
  val isImport: Boolean = true
  val clock = dpiTrigger("clock", Input(Bool()))
  val ready = dpiIn("ready", Input(Bool()))
  val issueIdx = dpiIn("issueIdx", Input(UInt(p.instIdxBits.W)))

  override val trigger = s"always @(posedge ${clock.name}) #(${p.triggerDelay})"
}
