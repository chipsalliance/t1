// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rocketv.dpi

import chisel3.experimental.ExtModule
import chisel3.util.HasExtModuleInline

class DumpWave extends ExtModule with HasExtModuleInline {
  setInline(
    s"DumpWave.sv",
    s"""module DumpWave;
       |export "DPI-C" function DumpWave;
       |function DumpWave(input string file);
       |$$dumpfile(file);
       |$$dumpvars(0);
       |endfunction;
       |endmodule
       |""".stripMargin
  )
}
