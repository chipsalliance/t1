// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.subsystememu

import chisel3._
import chisel3.probe._
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.subsystem.ExtMem
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.t1.subsystem.T1SubsystemSystem
import org.chipsalliance.t1.subsystememu.dpi._

class TestHarness(implicit val p: Parameters) extends RawModule {
  val ldut = LazyModule(new T1SubsystemSystem)
  val dpiClockGen = Module(new ClockGen(ClockGenParameter(2)))
  val clock = read(dpiClockGen.clock)
  val reset = read(dpiClockGen.reset)
  val dpiInit = Module(new InitCosim)
  val dpiDumpWave = Module(new DumpWave)
  val dpiFinish = Module(new Finish)
  val dpiResetVector = Module(new ResetVector)
  val dpi_plus_arg = Module(new PlusArgVal)

  withClockAndReset(clock.asClock, reset) {
    val dut = Module(ldut.module)
    // Allow the debug ndreset to reset the dut, but not until the initial reset has completed
    dut.interrupts := 0.U
    dut.dontTouchPorts()

    ldut.resetVector := dpiResetVector.resetVector.ref
    dpiResetVector.reset.ref := reset
    dpiResetVector.clock.ref := clock
    ldut.mem_axi4.zip(ldut.memAXI4Node.in).map { case (io, (_, edge)) =>
      val mem = LazyModule(new LazyAXI4MemBFM(edge, base = p(ExtMem).get.master.base, size = p(ExtMem).get.master.size))
      Module(mem.module).suggestName("mem")
      mem.io_axi4.head <> io
      mem
    }.toSeq

    ldut.mmio_axi4.zip(ldut.mmioAXI4Node.in).map { case (io, (_, edge)) =>
      val mmio = LazyModule(new LazyAXI4MemBFM(edge, base = p(ExtMem).get.master.base, size = p(ExtMem).get.master.size, dpiName = "AXI4MMIODPI"))
      Module(mmio.module).suggestName("mmio")
      mmio.io_axi4.head <> io
      mmio
    }.toSeq
  }
}
