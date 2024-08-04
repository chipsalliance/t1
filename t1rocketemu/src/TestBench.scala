// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.t1rocketemu

import chisel3.experimental.{BaseModule, ExtModule, SerializableModuleGenerator}
import chisel3.util.HasExtModuleInline
import chisel3.{Bool, ImplicitClock, ImplicitReset, Module, Output, RawModule}
import org.chipsalliance.t1.tile.{T1RocketTile, T1RocketTileParameter}

class TestBench(generator: SerializableModuleGenerator[T1RocketTile, T1RocketTileParameter])
    extends RawModule
    with ImplicitClock
    with ImplicitReset {
  val clockGen = Module(new ExtModule with HasExtModuleInline {

    override def desiredName = "ClockGen"
    setInline(
      s"$desiredName.sv",
      s"""module $desiredName(output reg clock, output reg reset);
         |  export "DPI-C" function dump_wave;
         |  function dump_wave(input string file);
         |`ifdef VCS
         |    $$fsdbDumpfile(file);
         |    $$fsdbDumpvars("+all");
         |    $$fsdbDumpon;
         |`endif
         |`ifdef VERILATOR
         |    $$dumpfile(file);
         |    $$dumpvars(0);
         |`endif
         |  endfunction;
         |
         |  import "DPI-C" context function void t1_cosim_init();
         |  initial begin
         |    t1_cosim_init();
         |    clock = 1'b0;
         |    reset = 1'b1;
         |  end
         |  initial #(11) reset = 1'b0;
         |  always #10 clock = ~clock;
         |endmodule
         |""".stripMargin
    )
    val clock = IO(Output(Bool()))
    val reset = IO(Output(Bool()))
  })
  def clock = clockGen.clock.asClock
  def reset = clockGen.reset
  override def implicitClock = clockGen.clock.asClock
  override def implicitReset = clockGen.reset
  val dut: T1RocketTile with BaseModule = Module(generator.module())
  dut.io.clock := clock
  dut.io.reset := reset

  // get resetVector from simulator
  dut.io.resetVector := RawUnclockedNonVoidFunctionCall("get_resetvector", Const(UInt(64.W)))(simulationTime === 0.U)

  dut.io.hartid := 0.U
  dut.io.debug := 0.U
  dut.io.mtip := 0.U
  dut.io.msip := 0.U
  dut.io.meip := 0.U
  dut.io.buserror := 0.U

  // memory driver
  Seq(
    dut.io.instructionFetchAXI, // index 0
    dut.io.highBandwidthAXI, // index 1
    dut.io.highOutstandingAXI // index 2
  ).map(_.viewAs[AXI4RWIrrevocableVerilog])
    .lazyZip(
      Seq("instructionFetchAXI", "highBandwidthAXI", "highOutstandingAXI")
    )
    .zipWithIndex
    .foreach {
      case ((bundle: AXI4RWIrrevocableVerilog, channelName: String), index: Int) =>
        val agent = Module(
          new AXI4SlaveAgent(
            AXI4SlaveAgentParameter(
              name = channelName,
              axiParameter = bundle.parameter,
              outstanding = 4,
              readPayloadSize = 1,
              writePayloadSize = 1
            )
          )
        ).suggestName(s"axi4_channel${index}_${channelName}")
        agent.io.channel match {
          case io: AXI4RWIrrevocableVerilog => io <> bundle
        }
        agent.io.clock := clock
        agent.io.reset := reset
        agent.io.channelId := index.U
        agent.io.gateRead := false.B
        agent.io.gateWrite := false.B
    }

  val loadStoreAXI = dut.io.loadStoreAXI.viewAs[AXI4RWIrrevocableVerilog]
  val loadStoreAgent = Module(
    new AXI4SlaveAgent(
      AXI4SlaveAgentParameter(
        name = "loadStoreAXI",
        axiParameter = loadStoreAXI.parameter,
        outstanding = 4,
        // TODO: add payloadSize config to parameter
        readPayloadSize = 8, // todo: align with parameter in the future
        writePayloadSize = 8
      )
    ).suggestName("axi4_channel3_loadStoreAXI")
  )
  loadStoreAgent.io.channel match {
    case io: AXI4RWIrrevocableVerilog => io <> loadStoreAXI
  }
  loadStoreAgent.io.clock := clock
  loadStoreAgent.io.reset := reset
  loadStoreAgent.io.channelId := 3.U
  loadStoreAgent.io.gateRead := false.B
  loadStoreAgent.io.gateWrite := false.B
}
