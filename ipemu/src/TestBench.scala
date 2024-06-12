// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu

import chisel3._
import chisel3.experimental.SerializableModuleGenerator
import chisel3.experimental.dataview.DataViewable
import chisel3.util.circt.dpi.{RawClockedNonVoidFunctionCall, RawClockedVoidFunctionCall, RawUnlockedNonVoidFunctionCall}
import org.chipsalliance.amba.axi4.bundle._
import org.chipsalliance.t1.ipemu.dpi._
import org.chipsalliance.t1.rtl.{CSRInterface, T1, T1Parameter, VRequest}

class TestBench(generator: SerializableModuleGenerator[T1, T1Parameter]) extends RawModule {
  val clockGen = Module(new ClockGen)
  Module(new DumpWave)

  val clock: Clock = clockGen.clock.asClock
  val reset: Bool = clockGen.reset

  val dut: T1 = withClockAndReset(clock, reset)(Module(generator.module()))
  val simulationTime = withClockAndReset(clock, reset)(RegInit(0.U(64.W)))
  simulationTime := simulationTime + 1.U

  // simulation env
  withClockAndReset(clock, reset) {
    // TODO: this initial way cannot happen before reset...
    val initFlag = RegInit(true.B)
    val callInit: Bool = RawUnlockedNonVoidFunctionCall("cosim_init", Bool())(initFlag)
    when(callInit) {
      initFlag := false.B
      printf(cf"""{"event":"simulationStart","parameter":{"cycle": ${simulationTime}}}\n""")
    }
    val watchdog: UInt = RawUnlockedNonVoidFunctionCall("cosim_watchdog", UInt(8.W))(simulationTime.tail(10) === 0.U)
    when(watchdog =/= 0.U) {
      stop(cf"""{"event":"simulationStop","parameter":{"reason": ${watchdog},"cycle": ${simulationTime}}}\n""")
    }
  }

  // Instruction Drivers
  withClockAndReset(clock, reset) {
    // Instantiate a counter to avoid sending instruction for each cycle.
    // by recording a scoreboard in the TB
    val outstandingInstructions: UInt = RegInit(0.U)
    outstandingInstructions := outstandingInstructions + dut.request.fire.asUInt - dut.response.fire.asUInt

    class Issue extends Bundle {
      val request = new VRequest(32)
      val csr = new CSRInterface(dut.parameter.laneParam.vlMaxBits)
    }
    val issue: Issue = RawClockedNonVoidFunctionCall("issue_vector_instruction", new Issue)(
      clock, outstandingInstructions <= dut.parameter.chainingSize.U,
    )
    // always valid to speed up simulation.
    dut.request.bits := issue.request
    dut.csrInterface := issue.csr
    dut.storeBufferClear := true.B
    dut.request.valid := true.B
    RawClockedVoidFunctionCall("retire_vector_instruction")(clock, dut.response.valid, dut.response.bits)
  }

  // Memory Drivers
  Seq(
    dut.highBandwidthLoadStorePort,
    dut.indexedLoadStorePort
  ).map(_.viewAs[AXI4RWIrrevocableVerilog]).zip(
    Seq("highBandwidthPort", "indexedAccessPort")
  ).foreach {
    case (bundle: AXI4RWIrrevocableVerilog, channelName: String) =>
      val agent = Module(new AXI4SlaveAgent(
        AXI4SlaveAgentParameter(
          name= channelName,
          axiParameter = bundle.parameter,
          outstanding = 4
        )
      ))
      agent.io.channel match {
        case io: AXI4RWIrrevocableVerilog => io :<>= bundle
      }
      agent.io.clock := clock
      agent.io.reset := reset
  }

  // Events for difftest and performance modeling

  val laneProbes = dut.laneProbes.zipWithIndex.map{case (p, idx) =>
    val wire = Wire(p.cloneType).suggestName(s"lane${idx}Probe")
    wire := probe.read(p)
  }

  val lsuProbe = probe.read(dut.lsuProbe).suggestName("lsuProbe")

  val laneVrfProbes = dut.laneVrfProbes.zipWithIndex.map{ case (p, idx) =>
    val wire = Wire(p.cloneType).suggestName(s"lane${idx}VrfProbe")
    wire := probe.read(p)
    wire
  }

  val t1Probe = probe.read(dut.t1Probe)

  withClockAndReset(clock, reset) {
    // memory write
    lsuProbe.slots.zipWithIndex.foreach { case (mshr, i) => when(mshr.writeValid)(printf(cf"""{"event":"vrfWriteFromLsu","parameter":{"idx":$i,"vd":${mshr.dataVd},"offset":${mshr.dataOffset},"mask":${mshr.dataMask},"data":${mshr.dataData},"instruction":${mshr.dataInstruction},"lane":${mshr.targetLane},"cycle": ${simulationTime}}}\n""")) }
    // vrf write
    laneVrfProbes.zipWithIndex.foreach { case (lane, i) => when(lane.valid)(printf(cf"""{"event":"vrfWriteFromLane","parameter":{"idx":$i,"vd":${lane.requestVd},"offset":${lane.requestOffset},"mask":${lane.requestMask},"data":${lane.requestData},"instruction":${lane.requestInstruction},"cycle": ${simulationTime}}}\n""")) }
    // issue
    when(dut.request.fire)(printf(cf"""{"event":"issue","parameter":{"idx":${t1Probe.instructionCounter},"cycle": ${simulationTime}}}\n"""))
    // inst
    when(dut.response.valid)(printf(cf"""{"event":"inst","parameter":{"data":${dut.response.bits.data},"vxsat":${dut.response.bits.vxsat},"rd_valid":${dut.response.bits.rd.valid},"rd":${dut.response.bits.rd.bits},"mem":${dut.response.bits.mem},"cycle": ${simulationTime}}}\n"""))
    // peekTL
    //    dut.memoryPorts.zipWithIndex.foreach { case (bundle, i) => when(bundle.a.valid)(printf(cf"""{"event":"peekTL","parameter":{"idx":$i,"opcode":${bundle.a.bits.opcode},"param":${bundle.a.bits.param},"size":${bundle.a.bits.size},"source":${bundle.a.bits.source},"address":${bundle.a.bits.address},"mask":${bundle.a.bits.mask},"data":${bundle.a.bits.data},"corrupt":${bundle.a.bits.corrupt},"dready":${bundle.d.ready},"cycle": ${simulationTime}}}\n""")) }
    // lsu enq
    when(lsuProbe.reqEnq.orR)(printf(cf"""{"event":"lsuEnq","parameter":{"enq":${lsuProbe.reqEnq},"cycle": ${simulationTime}}}\n"""))
  }
}
