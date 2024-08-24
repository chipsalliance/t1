// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu

import chisel3._
import chisel3.experimental.dataview.DataViewable
import chisel3.experimental.hierarchy.{Instance, Instantiate, instantiable, public}
import chisel3.experimental.{ExtModule, SerializableModuleGenerator}
import chisel3.properties.{AnyClassType, Class, ClassType, Property}
import chisel3.util.circt.dpi.{RawClockedNonVoidFunctionCall, RawClockedVoidFunctionCall, RawUnclockedNonVoidFunctionCall}
import chisel3.util.{HasExtModuleInline, PopCount, UIntToOH, Valid}
import org.chipsalliance.amba.axi4.bundle._
import org.chipsalliance.t1.ipemu.dpi._
import org.chipsalliance.t1.rtl.{T1, T1Parameter}

@instantiable
class TestBenchOM extends Class {
  @public
  val t1 = IO(Output(Property[AnyClassType]()))
  @public
  val t1In = IO(Input(Property[AnyClassType]()))
  t1 := t1In
}

class TestBench(generator: SerializableModuleGenerator[T1, T1Parameter])
    extends RawModule
    with ImplicitClock
    with ImplicitReset {
  layer.enable(layers.Verification)
  val omInstance: Instance[TestBenchOM] = Instantiate(new TestBenchOM)
  val omType:     ClassType = omInstance.toDefinition.getClassType
  @public
  val om: Property[ClassType] = IO(Output(Property[omType.Type]()))
  om := omInstance.getPropertyReference

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
  val dut: Instance[T1] = generator.instance()

  val simulationTime: UInt = RegInit(0.U(64.W))
  simulationTime := simulationTime + 1.U

  dut.io.clock := clockGen.clock.asClock
  dut.io.reset := clockGen.reset
  omInstance.t1In := Property(dut.io.om.asAnyClassType)
  // Instruction Drivers

  // TODO: this initial way cannot happen before reset...
  val initFlag = RegInit(false.B)
  when(!initFlag) {
    initFlag := true.B
    printf(cf"""{"event":"SimulationStart","cycle":${simulationTime}}\n""")
  }
  val watchdog = RawUnclockedNonVoidFunctionCall("cosim_watchdog", UInt(8.W))(simulationTime(9, 0) === 0.U)
  when(watchdog =/= 0.U) {
    stop(cf"""{"event":"SimulationStop","reason": ${watchdog},"cycle":${simulationTime}}\n""")
  }

  // uint32_t -> svBitVecVal -> reference type with 7 length.
  class Issue extends Bundle {
    val instruction: UInt = UInt(32.W)
    val src1Data:    UInt = UInt(32.W)
    val src2Data:    UInt = UInt(32.W)
    // mstatus, vstatus?
    val vtype: UInt = UInt(32.W)
    val vl:    UInt = UInt(32.W)
    // vlenb
    val vstart: UInt = UInt(32.W)
    // vxrm, vxsat are merged to vcsr
    val vcsr: UInt = UInt(32.W)
    // meta is used to control the simulation.
    // 0 is reserved, aka not valid
    // 1 is normal, it's a valid instruction
    // 2 is fence, it will request
    // others are exit, will end the simulation immediately
    val meta: UInt = UInt(32.W)
  }
  class Retire extends Bundle {
    val rd:      UInt = UInt(32.W)
    val data:    UInt = UInt(32.W)
    val writeRd: UInt = UInt(32.W)
    val vxsat:   UInt = UInt(32.W)
  }
  // X gated by didIssue
  val issue = WireDefault(0.U.asTypeOf(new Issue))
  val fence = RegInit(false.B)
  val outstanding = RegInit(0.U(4.W))
  val hasBeenReset = RegNext(true.B, false.B)
  val doIssue: Bool = dut.io.issue.ready && !fence && hasBeenReset
  outstanding := outstanding + (RegNext(doIssue) && (issue.meta === 1.U)) - dut.io.issue.valid
  // TODO: refactor driver to spawn 3 scoreboards for record different retirement.
  val t1Probe = probe.read(dut.io.t1Probe)
  fence := Mux(RegNext(doIssue), issue.meta === 2.U, fence && !t1Probe.retireValid && !(outstanding === 0.U))
  issue := Mux(doIssue,
    RawClockedNonVoidFunctionCall("issue_vector_instruction", new Issue)(
      clock,
      doIssue,
    ),
    0.U.asTypeOf(new Issue)
  )
  dut.io.issue.bits.instruction := issue.instruction
  dut.io.issue.bits.rs1Data := issue.src1Data
  dut.io.issue.bits.rs2Data := issue.src2Data
  dut.io.issue.bits.vtype := issue.vtype
  dut.io.issue.bits.vl := issue.vl
  dut.io.issue.bits.vstart := issue.vstart
  dut.io.issue.bits.vcsr := issue.vcsr
  dut.io.issue.valid := issue.meta === 1.U
  when(issue.meta =/= 0.U && issue.meta =/= 1.U && issue.meta =/= 2.U) {
    stop(cf"""{"event":"SimulationStop","reason": ${issue.meta},"cycle":${simulationTime}}\n""")
  }
  val retire = Wire(new Retire)
  retire.rd := dut.io.retire.rd.bits.rdAddress
  retire.data := dut.io.retire.rd.bits.rdData
  retire.writeRd := dut.io.retire.rd.valid
  retire.vxsat := dut.io.retire.csr.bits.vxsat
  // TODO:
  //  retire.fflag := dut.io.retire.csr.bits.fflag
  RawClockedVoidFunctionCall("retire_vector_instruction")(clock, t1Probe.retireValid, retire)
  val dummy = Wire(Bool())
  dummy := false.B
  RawClockedVoidFunctionCall("retire_vector_mem")(clock, dut.io.retire.mem.valid, dummy)

  // Memory Drivers
  Seq(
    dut.io.highBandwidthLoadStorePort,
    dut.io.indexedLoadStorePort
  ).map(_.viewAs[AXI4RWIrrevocableVerilog])
    .lazyZip(
      Seq("highBandwidthPort", "indexedAccessPort")
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

  // Events for difftest and performance modeling

  // Probes
  val laneProbes = t1Probe.laneProbes.zipWithIndex.map {
    case (lane, i) => lane.suggestName(s"lane${i}Probe")
  }

  val lsuProbe = t1Probe.lsuProbe.suggestName("lsuProbe")

  val storeUnitProbe = lsuProbe.storeUnitProbe.suggestName("storeUnitProbe")

  val otherUnitProbe = lsuProbe.otherUnitProbe.suggestName("otherUnitProbe")

  // vrf write
  laneProbes.zipWithIndex.foreach {
    case (lane, i) =>
      val vrf = lane.vrfProbe.suggestName(s"lane${i}VrfProbe")
      when(vrf.valid)(
        printf(
          cf"""{"event":"VrfWrite","issue_idx":${vrf.requestInstruction},"vd":${vrf.requestVd},"offset":${vrf.requestOffset},"mask":"${vrf.requestMask}%x","data":"${vrf.requestData}%x","lane":$i,"cycle":${simulationTime}}\n"""
        )
      )
  }
  // memory write from store unit
  when(storeUnitProbe.valid)(
    printf(
      cf"""{"event":"MemoryWrite","lsu_idx":${storeUnitProbe.index},"mask":"${storeUnitProbe.mask}%x","data":"${storeUnitProbe.data}%x","address":"${storeUnitProbe.address}%x","cycle":${simulationTime}}\n"""
    )
  )
  // memory write from other unit
  when(otherUnitProbe.valid)(
    printf(
      cf"""{"event":"MemoryWrite","lsu_idx":${otherUnitProbe.index},"mask":"${otherUnitProbe.mask}%x","data":"${otherUnitProbe.data}%x","address":"${otherUnitProbe.address}%x","cycle":${simulationTime}}\n"""
    )
  )
  // issue
  when(dut.io.issue.fire)(
    printf(cf"""{"event":"Issue","idx":${t1Probe.instructionCounter},"cycle":${simulationTime}}\n""")
  )
  // check rd
  when(dut.io.retire.rd.valid)(
    printf(
      cf"""{"event":"CheckRd","data":"${dut.io.retire.rd.bits.rdData}%x","issue_idx":${t1Probe.responseCounter},"cycle":${simulationTime}}\n"""
    )
  )
  // lsu enq
  when(lsuProbe.reqEnq.orR)(printf(cf"""{"event":"LsuEnq","enq":${lsuProbe.reqEnq},"cycle":${simulationTime}}\n"""))

  // allocate 2 * chainingSize scoreboards
  val vrfWriteScoreboard: Seq[Valid[UInt]] = Seq.tabulate(2 * generator.parameter.chainingSize) { _ =>
    RegInit(0.U.asTypeOf(Valid(UInt(16.W))))
  }
  vrfWriteScoreboard.foreach(scoreboard => dontTouch(scoreboard))
  val instructionValid =
    (laneProbes.map(laneProbe => laneProbe.instructionValid ## laneProbe.instructionValid) :+
      lsuProbe.lsuInstructionValid :+ t1Probe.instructionValid).reduce(_ | _)
  val scoreboardEnq =
    Mux(t1Probe.instructionIssue, UIntToOH(t1Probe.issueTag), 0.U((2 * generator.parameter.chainingSize).W))
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
          cf"""{"event":"VrfScoreboardReport","count":${scoreboard.bits},"issue_idx":${tag},"cycle":${simulationTime}}\n"""
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
