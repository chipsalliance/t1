// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu.dpi

import chisel3._

class DpiError extends DPIModuleLegacy {
  val isImport: Boolean = false

  override val exportBody = s"""
     |function $desiredName(input string what);
     |   $$error(what);
     |endfunction;
     |""".stripMargin
}
