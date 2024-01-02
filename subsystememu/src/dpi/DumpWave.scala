// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.subsystememu.dpi

import chisel3._

class DumpWave extends DPIModule {
  val isImport: Boolean = false

  // TODO: think about `chisel3.properties.Property`?
  override val exportBody = s"""
                               |function $desiredName(input string file);
                               |   $$dumpfile(file);
                               |   $$dumpvars(0);
                               |endfunction;
                               |""".stripMargin
}