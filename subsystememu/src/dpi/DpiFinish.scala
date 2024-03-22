// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.subsystememu.dpi

import chisel3._

class DpiFinish extends DPIModuleLegacy {
  val isImport: Boolean = false

  // TODO: think about `chisel3.properties.Property`?
  override val exportBody = s"""
     |function $desiredName();
     |   $$finish;
     |endfunction;
     |""".stripMargin
}
