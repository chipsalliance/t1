// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu

import chisel3._
import chisel3.experimental.SerializableModuleGenerator
import chisel3.experimental.dataview.DataViewable
import chisel3.util.circt.dpi.{RawClockedNonVoidFunctionCall, RawClockedVoidFunctionCall, RawUnlockedNonVoidFunctionCall}
import org.chipsalliance.amba.axi4.bundle._
import org.chipsalliance.t1.ipemu.dpi._
import org.chipsalliance.t1.rtl.{T1, T1Parameter}

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
    val callInit = RawUnlockedNonVoidFunctionCall("cosim_init", Bool())(initFlag).asInstanceOf[Bool]
    when(callInit) {
      initFlag := false.B
      printf(cf"""{"event":"simulationStart","parameter":{"cycle": ${simulationTime}}}\n""")
    }
    val watchdog = RawUnlockedNonVoidFunctionCall("cosim_watchdog", UInt(8.W))(simulationTime.tail(10) === 0.U).asInstanceOf[UInt]
    when(watchdog =/= 0.U) {
      stop(cf"""{"event":"simulationStop","parameter":{"reason": ${watchdog},"cycle": ${simulationTime}}}\n""")
    }
  }

  // Instruction Drivers
  withClockAndReset(clock, reset) {
    // uint32_t -> svBitVecVal -> reference type with 7 length.
    class Issue extends Bundle {
      val instruction: UInt = UInt(32.W)
      val src1Data: UInt = UInt(32.W)
      val src2Data: UInt = UInt(32.W)
      // mstatus, vstatus?
      val vtype: UInt = UInt(32.W)
      val vl: UInt = UInt(32.W)
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
      val rd: UInt = UInt(32.W)
      val data: UInt = UInt(32.W)
      val writeRd: UInt = UInt(32.W)
      val vxsat: UInt = UInt(32.W)
    }
    val issue = WireDefault(0.U.asTypeOf(new Issue))
    val fence = RegInit(false.B)
    val doIssue: Bool = dut.request.ready && (!fence || dut.response.valid)
    issue := RawClockedNonVoidFunctionCall("issue_vector_instruction", new Issue)(
      clock,
      doIssue,
    ).asInstanceOf[Issue]
    dut.request.bits.instruction := issue.instruction
    dut.request.bits.src1Data := issue.src1Data
    dut.request.bits.src2Data := issue.src2Data
    dut.csrInterface.vlmul := issue.vtype(2, 0)
    dut.csrInterface.vSew := issue.vtype(5, 3)
    dut.csrInterface.vta := issue.vtype(6)
    dut.csrInterface.vma := issue.vtype(7)
    dut.csrInterface.vl := issue.vl
    dut.csrInterface.vStart := issue.vstart
    dut.csrInterface.vxrm := issue.vcsr(2, 1)

    dut.csrInterface.ignoreException := 0.U
    dut.storeBufferClear := true.B
    dut.request.valid := issue.meta === 1.U
    fence := Mux(doIssue, issue.meta === 2.U, fence)
    when(issue.meta =/= 0.U && issue.meta =/= 1.U && issue.meta =/= 2.U) {
      stop(cf"""{"event":"simulationStop","parameter":{"reason": ${issue.meta},"cycle": ${simulationTime}}}\n""")
    }
    val retire = Wire(new Retire)
    retire.rd := dut.response.bits.rd.bits
    retire.data := dut.response.bits.data
    retire.writeRd := dut.response.bits.rd.valid
    retire.vxsat := dut.response.bits.vxsat
    RawClockedVoidFunctionCall("retire_vector_instruction")(clock, dut.response.valid, retire)
  }

  // Memory Drivers
  Seq(
    dut.highBandwidthLoadStorePort,
    dut.indexedLoadStorePort
  ).map(_.viewAs[AXI4RWIrrevocableVerilog]).lazyZip(
    Seq("highBandwidthPort", "indexedAccessPort")
  ).zipWithIndex.foreach {
    case ((bundle: AXI4RWIrrevocableVerilog, channelName: String), index: Int) =>
      val agent = Module(new AXI4SlaveAgent(
        AXI4SlaveAgentParameter(
          name= channelName,
          axiParameter = bundle.parameter,
          outstanding = 4
        )
      )).suggestName(s"axi4_channel${index}_${channelName}")
      agent.io.channel match {
        case io: AXI4RWIrrevocableVerilog => io :<>= bundle
      }
      agent.io.clock := clock
      agent.io.reset := reset
      agent.io.channelId := index.U
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
