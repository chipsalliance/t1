// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.subsystememu

import chisel3._
import freechips.rocketchip.diplomacy.LazyModule
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.t1.subsystem.{T1Subsystem, T1SubsystemModuleImp}
import org.chipsalliance.t1.subsystememu.dpi._

class TestHarness(val p: Parameters) extends RawModule {

  // Instantiate DUT
  val dut: T1SubsystemModuleImp[T1Subsystem] = LazyModule(new T1Subsystem()(p)).module

  // Instantiate Verification Logic
  val dpiClockGen = Module(new ClockGen(ClockGenParameter(2)))
  val dpiInit = Module(new InitCosim)
  val dpiDumpWave = Module(new DumpWave)
  val dpiFinish = Module(new Finish)
  val dpiResetVector = Module(new ResetVector)
  val dpiPlusArg = Module(new PlusArgVal)
}
