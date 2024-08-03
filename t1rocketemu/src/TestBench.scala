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
  dut.io.hartid
  dut.io.resetVector
  dut.io.debug
  dut.io.mtip
  dut.io.msip
  dut.io.meip
  dut.io.seip
  dut.io.lip
  dut.io.nmi
  dut.io.nmiInterruptVector
  dut.io.nmiIxceptionVector
  dut.io.buserror
  dut.io.wfi
  dut.io.halt
  dut.io.instructionFetchAXI
  dut.io.itimAXI
  dut.io.loadStoreAXI
  dut.io.dtimAXI
  dut.io.dtimAXI
  dut.io.highBandwidthAXI
  dut.io.highOutstandingAXI
}