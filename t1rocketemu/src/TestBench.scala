// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.t1rocketemu

import chisel3._
import chisel3.experimental.{BaseModule, ExtModule, SerializableModuleGenerator}
import chisel3.experimental.dataview.DataViewable
import chisel3.util.circt.dpi.RawUnclockedNonVoidFunctionCall
import chisel3.util.{HasExtModuleInline, PopCount, UIntToOH, Valid}
import org.chipsalliance.amba.axi4.bundle._
import org.chipsalliance.t1.t1rocketemu.dpi._
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
         |  import "DPI-C" context function void t1rocket_cosim_init();
         |  initial begin
         |    t1rocket_cosim_init();
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

  // control simulation
  val simulationTime: UInt = RegInit(0.U(64.W))
  simulationTime := simulationTime + 1.U

  // this initial way cannot happen before reset
  val initFlag: Bool = RegInit(false.B)
  when(!initFlag) {
    initFlag := true.B
    printf(cf"""{"event":"SimulationStart","cycle":${simulationTime}}\n""")
  }
  val watchdog: UInt = RawUnclockedNonVoidFunctionCall("cosim_watchdog", UInt(8.W))(simulationTime(9, 0) === 0.U)
  when(watchdog =/= 0.U) {
    stop(cf"""{"event":"SimulationStop","reason": ${watchdog},"cycle":${simulationTime}}\n""")
  }

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
    dut.io.highBandwidthAXI, // index 0
    dut.io.highOutstandingAXI // index 1
  ).map(_.viewAs[AXI4RWIrrevocableVerilog])
    .lazyZip(
      Seq("highBandwidthAXI", "highOutstandingAXI")
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

  val instFetchAXI = dut.io.instructionFetchAXI.viewAs[AXI4ROIrrevocableVerilog]
  val instFetchAgent = Module(
    new AXI4SlaveAgent(
      AXI4SlaveAgentParameter(
        name = "instructionFetchAXI",
        axiParameter = instFetchAXI.parameter,
        outstanding = 4,
        readPayloadSize = 1,
        writePayloadSize = 1
      )
    ).suggestName("axi4_channel2_instructionFetchAXI")
  )
  instFetchAgent.io.channel match {
    case io: AXI4ROIrrevocableVerilog => io <> instFetchAXI
  }
  instFetchAgent.io.clock := clock
  instFetchAgent.io.reset := reset
  instFetchAgent.io.channelId := 0.U
  instFetchAgent.io.gateRead := false.B
  instFetchAgent.io.gateWrite := false.B

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

  // probes
  val t1RocketProbe = probe.read(dut.io.t1RocketProbe)
  val rocketProbe = t1RocketProbe.rocketProbe.suggestName(s"rocketProbe")
  val t1Probe = t1RocketProbe.t1Probe
  val lsuProbe = t1Probe.lsuProbe
  val laneProbes = t1Probe.laneProbes.zipWithIndex.map {
    case (p, idx) =>
      val wire = Wire(p.cloneType).suggestName(s"lane${idx}Probe")
      wire := probe.read(p)
      wire
  }
  val laneVrfProbes = t1Probe.laneProbes.map(_.vrfProbe).zipWithIndex.map {
    case (p, idx) =>
      val wire = Wire(p.cloneType).suggestName(s"lane${idx}VrfProbe")
      wire := probe.read(p)
      wire
  }
  val storeUnitProbe = t1Probe.lsuProbe.storeUnitProbe.suggestName("storeUnitProbe")
  val otherUnitProbe = t1Probe.lsuProbe.otherUnitProbe.suggestName("otherUnitProbe")

  // output the probes
  // rocket reg write
  when(rocketProbe.rfWen)(
    printf(
      cf"""{"event":"RegWrite","idx":${rocketProbe.rfWaddr},"data":"${rocketProbe.rfWdata}%x","cycle":${simulationTime}}\n"""
    )
  )

  // t1 vrf write
  laneVrfProbes.zipWithIndex.foreach {
    case (lane, i) =>
      when(lane.valid)(
        printf(
          cf"""{"event":"VrfWrite","issue_idx":${lane.requestInstruction},"vd":${lane.requestVd},"offset":${lane.requestOffset},"mask":"${lane.requestMask}%x","data":"${lane.requestData}%x","lane":$i,"cycle":${simulationTime}}\n"""
        )
      )
  }

  // t1 memory write from store unit
  when(storeUnitProbe.valid)(
    printf(
      cf"""{"event":"MemoryWrite","lsu_idx":${storeUnitProbe.index},"mask":"${storeUnitProbe.mask}%x","data":"${storeUnitProbe.data}%x","address":"${storeUnitProbe.address}%x","cycle":${simulationTime}}\n"""
    )
  )

  // t1 memory write from other unit
  when(otherUnitProbe.valid)(
    printf(
      cf"""{"event":"MemoryWrite","lsu_idx":${otherUnitProbe.index},"mask":"${otherUnitProbe.mask}%x","data":"${otherUnitProbe.data}%x","address":"${otherUnitProbe.address}%x","cycle":${simulationTime}}\n"""
    )
  )

  // t1 issue
  when(t1Probe.issue.valid)(
    printf(cf"""{"event":"Issue","idx":${t1Probe.issue.bits},"cycle":${simulationTime}}\n""")
  )

  // t1 retire
  when(t1Probe.retire.valid)(
    printf(
      cf"""{"event":"CheckRd","data":"${t1Probe.retire.bits}%x","issue_idx":${t1Probe.responseCounter},"cycle":${simulationTime}}\n"""
    )
  )

  // t1 lsu enq
  when(t1Probe.lsuProbe.reqEnq.orR)(printf(cf"""{"event":"LsuEnq","enq":${t1Probe.lsuProbe.reqEnq},"cycle":${simulationTime}}\n"""))

  // t1 vrf scoreboard
  val vrfWriteScoreboard: Seq[Valid[UInt]] = Seq.tabulate(2 * generator.parameter.t1Parameter.chainingSize) { _ =>
    RegInit(0.U.asTypeOf(Valid(UInt(16.W))))
  }
  vrfWriteScoreboard.foreach(scoreboard => dontTouch(scoreboard))
  val instructionValid =
    (laneProbes.map(laneProbe => laneProbe.instructionValid ## laneProbe.instructionValid) :+
      lsuProbe.lsuInstructionValid :+ t1Probe.instructionValid).reduce(_ | _)
  val scoreboardEnq =
    Mux(t1Probe.instructionIssue, UIntToOH(t1Probe.issueTag), 0.U((2 * generator.parameter.t1Parameter.chainingSize).W))
  vrfWriteScoreboard.zipWithIndex.foreach {
    case (scoreboard, tag) =>
      val writeEnq: UInt = VecInit(
        // vrf write from lane
        laneProbes.flatMap(laneProbe =>
          laneProbe.slots.map(slot => slot.writeTag === tag.U && slot.writeQueueEnq && slot.writeMask.orR)
        ) ++ laneProbes.flatMap(laneProbe =>
          laneProbe.crossWriteProbe.map(cp => cp.bits.writeTag === tag.U && cp.valid && cp.bits.writeMask.orR)
        ) ++
          // vrf write from lsu
          lsuProbe.slots.map(slot => slot.dataInstruction === tag.U && slot.writeValid && slot.dataMask.orR) ++
          // vrf write from Sequencer
          Some(t1Probe.writeQueueEnq.bits === tag.U && t1Probe.writeQueueEnq.valid && t1Probe.writeQueueEnqMask.orR)
      ).asUInt
      // always equal to array index
      scoreboard.bits := scoreboard.bits + PopCount(writeEnq)
      when(scoreboard.valid && !instructionValid(tag)) {
        printf(
          cf"""{"event":"VrfScoreboard","count":${scoreboard.bits},"issue_idx":${tag},"cycle":${simulationTime}}\n"""
        )
        scoreboard.valid := false.B
      }
      when(scoreboardEnq(tag)) {
        scoreboard.valid := true.B
        assert(!scoreboard.valid)
        scoreboard.bits := 0.U
      }
  }
}
