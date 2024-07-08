// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rocketv

import chisel3._
import chisel3.experimental.SerializableModuleGenerator
import chisel3.experimental.dataview.DataViewable
import chisel3.util.circt.dpi.{
  RawClockedNonVoidFunctionCall,
  RawClockedVoidFunctionCall,
  RawUnlockedNonVoidFunctionCall
}
import org.chipsalliance.amba.axi4.bundle._
import org.chipsalliance.t1.rocketv.dpi._
import org.chipsalliance.rocketv.{Frontend, RocketTile, RocketTileParameter}

class TestBench(generator: SerializableModuleGenerator[RocketTile, RocketTileParameter]) extends RawModule with ImplicitClock with ImplicitReset {
  val clockGen = Module(new ClockGen)
  Module(new DumpWave)

  override protected def implicitClock: Clock = clockGen.clock.asClock
  override protected def implicitReset: Reset = clockGen.reset

  val clock: Clock = clockGen.clock.asClock
  val reset: Bool = clockGen.reset

  val dut: RocketTile = withClockAndReset(clock, reset)(Module(generator.module()))

  dut.io.clock := clockGen.clock.asClock
  dut.io.reset := clockGen.reset
  dut.io.hartid := 0.U
  dut.io.debug := 0.U
  dut.io.mtip := 0.U
  dut.io.meip := 0.U
  dut.io.msip := 0.U
  dut.io.buserror := 0.U

  dut.io.resetVector := 10000000.U

  val simulationTime = withClockAndReset(clock, reset)(RegInit(0.U(64.W)))
  simulationTime := simulationTime + 1.U

  // simulation env
  withClockAndReset(clock, reset) {
    // TODO: this initial way cannot happen before reset...
    val initFlag = RegInit(true.B)
    val callInit = RawUnlockedNonVoidFunctionCall("cosim_init", Bool())(initFlag).asInstanceOf[Bool]
    when(callInit) {
      initFlag := false.B
      printf(cf"""{"event":"simulationStart","parameter":{"cycle": ${simulationTime}}}\n""")
    }
    val watchdog =
      RawUnlockedNonVoidFunctionCall("cosim_watchdog", UInt(8.W))(simulationTime(9, 0) === 0.U).asInstanceOf[UInt]
    when(watchdog =/= 0.U) {
      stop(cf"""{"event":"simulationStop","parameter":{"reason": ${watchdog},"cycle": ${simulationTime}}}\n""")
    }
  }

  // Memory Drivers
  val instFetchAXI = dut.io.instructionFetchAXI.viewAs[AXI4ROIrrevocableVerilog]
  val instFetchAgent = Module(
    new AXI4SlaveAgent(
      AXI4SlaveAgentParameter(
        name = "instructionFetchAXI",
        axiParameter = instFetchAXI.parameter,
        outstanding = 4
      )
    ).suggestName("axi4_channel0_instructionFetchAXI")
  )
  instFetchAgent.io.channel match {
    case io: AXI4ROIrrevocableVerilog => io <> instFetchAXI
  }
  instFetchAgent.io.clock := clock
  instFetchAgent.io.reset := reset
  instFetchAgent.io.channelId := 0.U

  val loadStoreAXI = dut.io.loadStoreAXI.viewAs[AXI4RWIrrevocableVerilog]
  val loadStoreAgent = Module(
    new AXI4SlaveAgent(
      AXI4SlaveAgentParameter(name = "loadStoreAXI", axiParameter = loadStoreAXI.parameter, outstanding = 4)
    ).suggestName("axi4_channel1_loadStoreAXI")
  )
  loadStoreAgent.io.channel match {
    case io: AXI4RWIrrevocableVerilog => io <> loadStoreAXI
  }
  loadStoreAgent.io.clock := clock
  loadStoreAgent.io.reset := reset
  loadStoreAgent.io.channelId := 1.U
}
