// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rocketv

import chisel3._
import chisel3.experimental.{ExtModule, SerializableModuleGenerator}
import chisel3.experimental.dataview.DataViewable
import chisel3.probe.{define, Probe}
import chisel3.util.{log2Ceil, HasExtModuleInline, PopCount, UIntToOH, Valid}
import chisel3.util.circt.dpi.RawUnclockedNonVoidFunctionCall
import org.chipsalliance.amba.axi4.bundle._
import org.chipsalliance.t1.rocketv.dpi._
import org.chipsalliance.rocketv.{RocketTile, RocketTileParameter}

class TestBench(generator: SerializableModuleGenerator[RocketTile, RocketTileParameter])
    extends RawModule
    with ImplicitClock
    with ImplicitReset {
  layer.enable(layers.Verification)
  val clockGen = Module(new ExtModule with HasExtModuleInline {
    override def desiredName = "ClockGen"
    setInline(
      s"$desiredName.sv",
      s"""module $desiredName(output reg clock, output reg reset);
         |  export "DPI-C" function dump_wave;
         |  function dump_wave(input string file);
         |    $$dumpfile(file);
         |    $$dumpvars(0);
         |  endfunction;
         |
         |  import "DPI-C" function void cosim_init();
         |  initial begin
         |    cosim_init();
         |    clock = 1'b0;
         |    reset = 1'b1;
         |  end
         |  initial #(101) reset = 1'b0;
         |  always #10 clock = ~clock;
         |endmodule
         |""".stripMargin
    )
    val clock                = IO(Output(Bool()))
    val reset                = IO(Output(Bool()))
  })

  val clock: Clock = clockGen.clock.asClock
  val reset: Bool  = clockGen.reset

  override protected def implicitClock: Clock = clockGen.clock.asClock
  override protected def implicitReset: Reset = clockGen.reset

  val simulationTime: UInt = withClockAndReset(clock, reset)(RegInit(0.U(64.W)))
  simulationTime := simulationTime + 1.U

  withClockAndReset(clock, reset) {
    val watchdog = RawUnclockedNonVoidFunctionCall("cosim_watchdog", UInt(8.W))(simulationTime(9, 0) === 0.U)
    when(watchdog =/= 0.U) {
      stop(cf"""{"event":"SimulationStop","reason": ${watchdog},"cycle":${simulationTime}}\n""")
    }
  }

  val dut: RocketTile = withClockAndReset(clock, reset)(Module(generator.module()))
  dut.io.clock    := clockGen.clock.asClock
  dut.io.reset    := clockGen.reset
  dut.io.hartid   := 0.U
  dut.io.debug    := 0.U
  dut.io.mtip     := 0.U
  dut.io.meip     := 0.U
  dut.io.msip     := 0.U
  dut.io.buserror := 0.U

  // get resetVector from simulator
  dut.io.resetVector := RawUnclockedNonVoidFunctionCall("get_resetvector", Const(UInt(64.W)))(simulationTime === 0.U)

  // output probes
  val rocketProbe = probe.read(dut.io.rocketProbe)
  when(rocketProbe.rfWen && rocketProbe.rfWaddr =/= 0.U)(
    printf(
      cf"""{"event":"RegWrite","addr":${rocketProbe.rfWaddr},"data":${rocketProbe.rfWdata},"cycle":${simulationTime}}\n"""
    )
  )

  // Memory Drivers
  val instFetchAXI   = dut.io.instructionFetchAXI.viewAs[AXI4ROIrrevocableVerilog]
  val instFetchAgent = Module(
    new AXI4SlaveAgent(
      AXI4SlaveAgentParameter(
        name = "instructionFetchAXI",
        axiParameter = instFetchAXI.parameter,
        outstanding = 4,
        readPayloadSize = 1,
        writePayloadSize = 1
      )
    ).suggestName("axi4_channel0_instructionFetchAXI")
  )
  instFetchAgent.io.channel match {
    case io: AXI4ROIrrevocableVerilog => io <> instFetchAXI
  }
  instFetchAgent.io.clock := clock
  instFetchAgent.io.reset     := reset
  instFetchAgent.io.channelId := 0.U
  instFetchAgent.io.gateRead  := false.B
  instFetchAgent.io.gateWrite := false.B

  val loadStoreAXI   = dut.io.loadStoreAXI.viewAs[AXI4RWIrrevocableVerilog]
  val loadStoreAgent = Module(
    new AXI4SlaveAgent(
      AXI4SlaveAgentParameter(
        name = "loadStoreAXI",
        axiParameter = loadStoreAXI.parameter,
        outstanding = 4,
        readPayloadSize = 8, // todo: align with parameter in the future
        writePayloadSize = 8
      )
    ).suggestName("axi4_channel1_loadStoreAXI")
  )
  loadStoreAgent.io.channel match {
    case io: AXI4RWIrrevocableVerilog => io <> loadStoreAXI
  }
  loadStoreAgent.io.clock := clock
  loadStoreAgent.io.reset     := reset
  loadStoreAgent.io.channelId := 0.U
  loadStoreAgent.io.gateRead  := false.B
  loadStoreAgent.io.gateWrite := false.B
}
