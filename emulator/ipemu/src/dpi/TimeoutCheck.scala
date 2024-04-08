// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu.dpi

import chisel3._

case class TimeoutCheckParameter(clockRate: Int)

class TimeoutCheck(p: TimeoutCheckParameter) extends DPIModuleLegacy {
  val isImport: Boolean = true
  override val trigger = s"always #(${2 * p.clockRate + 1})"
}
