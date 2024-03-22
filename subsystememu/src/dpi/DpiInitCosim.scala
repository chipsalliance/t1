// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.subsystememu.dpi

import chisel3._

class DpiInitCosim extends DPIModuleLegacy {
  val isImport: Boolean = true
  val resetVector = dpiOut("resetVector", Output(UInt(32.W)))
  override val trigger: String = s"initial"
}
