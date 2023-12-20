// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.subsystememu

import chisel3._
import chisel3.probe._
import chisel3.util.experimental.BoringUtils.tapAndRead
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.subsystem.{ExtBus, ExtMem}
import freechips.rocketchip.util.AsyncResetReg
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.t1.subsystem.VerdesSystem
import org.chipsalliance.t1.subsystememu.dpi._

class TestHarness(implicit val p: Parameters) extends RawModule {
  val ldut = LazyModule(new VerdesSystem)
  val dpiClockGen = Module(new ClockGen(ClockGenParameter(2)))
  val clock = read(dpiClockGen.clock)
  val reset = read(dpiClockGen.reset)
  val dpiInit = Module(new InitCosim)
  val dpiDumpWave = Module(new DumpWave)
  val dpiFinish = Module(new Finish)
  val dpiResetVector = Module(new ResetVector)
  val dpi_plus_arg = Module(new PlusArgVal)
  val dpiCommitPeek = Module(new dpiCommitPeek(2))
  val dpiRefillQueue = Module(new dpiRefillQueue(2))

  withClockAndReset(clock.asClock, reset) {
    val dut = Module(ldut.module)
    // Allow the debug ndreset to reset the dut, but not until the initial reset has completed
    dut.reset := reset.asBool
    dut.interrupts := 0.U
    dut.dontTouchPorts()

    dpiCommitPeek.llWen.ref := tapAndRead(ldut.t1Tiles.head.module.core.gatedDomain.longLatencyWenable)
    dpiCommitPeek.rfWen.ref     := tapAndRead(ldut.t1Tiles.head.module.core.gatedDomain.rfWen)
    dpiCommitPeek.rfWaddr.ref   := tapAndRead(ldut.t1Tiles.head.module.core.gatedDomain.rfWaddr)
    dpiCommitPeek.rfWdata.ref   := tapAndRead(ldut.t1Tiles.head.module.core.gatedDomain.rfWdata)
    dpiCommitPeek.wbRegPC.ref   := tapAndRead(ldut.t1Tiles.head.module.core.gatedDomain.wbRegPc)
    dpiCommitPeek.wbRegInst.ref := tapAndRead(ldut.t1Tiles.head.module.core.gatedDomain.wbRegInstruction)
    dpiCommitPeek.wbValid.ref   := tapAndRead(ldut.t1Tiles.head.module.core.gatedDomain.wbValid)
    dpiCommitPeek.clock.ref     := clock

    dpiRefillQueue.clock.ref := clock

    ldut.resetVector := dpiResetVector.resetVector.ref
    dpiResetVector.reset.ref := dut.reset
    dpiResetVector.clock.ref := dut.clock.asBool
    ldut.mem_axi4.zip(ldut.memAXI4Node.in).map { case (io, (_, edge)) =>
      val mem = LazyModule(new LazyAXI4MemBFM(edge, base = p(ExtMem).get.master.base, size = p(ExtMem).get.master.size))
      Module(mem.module).suggestName("mem")
      mem.io_axi4.head <> io
      mem
    }.toSeq
    ldut.mmio_axi4.zip(ldut.mmioAXI4Node.in).map { case (io, (_, edge)) =>
      io <> DontCare
    }.toSeq
  }
}

//class TestHarness(implicit val p: Parameters) extends RawModule {
//  val ldut = LazyModule(new VerdesSystem)
//  val dpiClockGen = Module(new ClockGen(ClockGenParameter(2)))
//  val clock = read(dpiClockGen.clock)
//  val reset = read(dpiClockGen.reset)
//
//  val dpiResetVector = Module(new ResetVector)
//  val dpi_plus_arg = Module(new PlusArgVal)
//
//
//  withClockAndReset(clock.asClock, reset) {
//    val dut = Module(ldut.module)
//    val verificationModule = Module(new VerificationModule(ldut))
//    // Allow the debug ndreset to reset the dut, but not until the initial reset has completed
//    dut.reset := reset.asBool
//    dut.interrupts := 0.U
//    dut.dontTouchPorts()
//
//    verificationModule.commitPeek.llWen     := tapAndRead(ldut.t1Tiles.head.module.core.gatedDomain.longLatencyWenable)
//    verificationModule.commitPeek.rfWen     := tapAndRead(ldut.t1Tiles.head.module.core.gatedDomain.rfWen)
//    verificationModule.commitPeek.rfWaddr   := tapAndRead(ldut.t1Tiles.head.module.core.gatedDomain.rfWaddr)
//    verificationModule.commitPeek.rfWdata   := tapAndRead(ldut.t1Tiles.head.module.core.gatedDomain.rfWdata)
//    verificationModule.commitPeek.wbRegPC   := tapAndRead(ldut.t1Tiles.head.module.core.gatedDomain.wbRegPc)
//    verificationModule.commitPeek.wbRegInst := tapAndRead(ldut.t1Tiles.head.module.core.gatedDomain.wbRegInstruction)
//    verificationModule.commitPeek.wbValid   := tapAndRead(ldut.t1Tiles.head.module.core.gatedDomain.wbValid)
//
//
//    ldut.resetVector := dpiResetVector.resetVector.ref
//    dpiResetVector.reset.ref := dut.reset
//    dpiResetVector.clock.ref := dut.clock.asBool
//    ldut.mem_axi4.zip(ldut.memAXI4Node.in).map { case (io, (_, edge)) =>
//      val mem = LazyModule(new LazyAXI4MemBFM(edge, base = p(ExtMem).get.master.base, size = p(ExtMem).get.master.size))
//      Module(mem.module).suggestName("mem")
//      mem.io_axi4.head <> io
//      mem
//    }.toSeq
//    ldut.mmio_axi4.zip(ldut.mmioAXI4Node.in).map { case (io, (_, edge)) =>
//      io <> DontCare
//    }.toSeq
//  }
//}
