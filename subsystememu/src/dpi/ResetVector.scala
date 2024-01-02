// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.subsystememu.dpi

import chisel3._

class ResetVector extends DPIModule {
  val isImport: Boolean = true
  val reset = dpiTrigger("reset", Input(Bool()))
  val clock = dpiTrigger("clock", Input(Bool()))
  override val trigger: String = s"always_latch@(*)"
  override val guard: String = s"${reset.name}"
  val resetVector = dpi("resetVector", Output(UInt(32.W)))
}