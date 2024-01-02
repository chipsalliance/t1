// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.subsystememu.dpi

import chisel3._

class InitCosim extends DPIModule {
  val isImport: Boolean = true
  override val trigger: String = s"initial"
}
