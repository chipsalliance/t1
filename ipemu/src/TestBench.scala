// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu

import chisel3._
import chisel3.experimental.SerializableModuleGenerator
import chisel3.experimental.hierarchy.{Instance, Instantiate, instantiable, public}
import chisel3.properties.{AnyClassType, Class, ClassType, Property}
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

class TestBench(generator: SerializableModuleGenerator[T1, T1Parameter]) extends RawModule {
  val omInstance: Instance[TestBenchOM] = Instantiate(new TestBenchOM)
  val omType: ClassType = omInstance.toDefinition.getClassType
  @public
  val om: Property[ClassType] = IO(Output(Property[omType.Type]()))
  om := omInstance.getPropertyReference

  // Scheduler to schedule different DPI calls for online difftest,
  // TODO: after switching to offline version, everything should be cleaned up.
  val clockRate = 5
  val latPokeInst = 1
  val negLatPeekWriteQueue = 1
  val latPeekLsuEnq = 1
  val latPeekIssue = 2
  val latPeekTL = 2
  val latPokeTL = 1

  val clockGen = Module(new ClockGen(ClockGenParameter(clockRate)))
  val dpiDumpWave = Module(new DpiDumpWave)
  val dpiFinish = Module(new DpiFinish)
  val dpiError = Module(new DpiError)
  val dpiInit = Module(new DpiInitCosim)
  val dpiTimeoutCheck = Module(new TimeoutCheck(TimeoutCheckParameter(clockRate)))

  val clock: Clock = clockGen.clock.asClock
  val reset: Bool = clockGen.reset

  val dut: T1 = withClockAndReset(clock, reset)(Module(generator.module()))
  dut.storeBufferClear := true.B
  omInstance.t1In := Property(dut.om.asAnyClassType)

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
    // count cycle for peek tl
    val cycleCounter = RegInit(0.U(64.W))
    cycleCounter := cycleCounter + 1.U

    // memory write
    lsuProbe.slots.zipWithIndex.foreach { case (mshr, i) => when(mshr.writeValid)(printf(cf"""{"event":"vrfWriteFromLsu","parameter":{"idx":$i,"vd":${mshr.dataVd},"offset":${mshr.dataOffset},"mask":${mshr.dataMask},"data":${mshr.dataData},"instruction":${mshr.dataInstruction},"lane":${mshr.targetLane},"cycle": ${cycleCounter}}}\n""")) }
    // vrf write
    laneVrfProbes.zipWithIndex.foreach { case (lane, i) => when(lane.valid)(printf(cf"""{"event":"vrfWriteFromLane","parameter":{"idx":$i,"vd":${lane.requestVd},"offset":${lane.requestOffset},"mask":${lane.requestMask},"data":${lane.requestData},"instruction":${lane.requestInstruction},"cycle": ${cycleCounter}}}\n""")) }
    // issue
    when(dut.request.fire)(printf(cf"""{"event":"issue","parameter":{"idx":${t1Probe.instructionCounter},"cycle": ${cycleCounter}}}\n"""))
    // inst
    when(dut.response.valid)(printf(cf"""{"event":"inst","parameter":{"data":${dut.response.bits.data},"vxsat":${dut.response.bits.vxsat},"rd_valid":${dut.response.bits.rd.valid},"rd":${dut.response.bits.rd.bits},"mem":${dut.response.bits.mem},"cycle": ${cycleCounter}}}\n"""))
    // peekTL
    dut.memoryPorts.zipWithIndex.foreach { case (bundle, i) => when(bundle.a.valid)(printf(cf"""{"event":"peekTL","parameter":{"idx":$i,"opcode":${bundle.a.bits.opcode},"param":${bundle.a.bits.param},"size":${bundle.a.bits.size},"source":${bundle.a.bits.source},"address":${bundle.a.bits.address},"mask":${bundle.a.bits.mask},"data":${bundle.a.bits.data},"corrupt":${bundle.a.bits.corrupt},"dready":${bundle.d.ready},"cycle": ${cycleCounter}}}\n""")) }
    // lsu enq
    when(lsuProbe.reqEnq.orR)(printf(cf"""{"event":"lsuEnq","parameter":{"enq":${lsuProbe.reqEnq},"cycle": ${cycleCounter}}}\n"""))
  }

  // Monitors
  // TODO: These monitors should be purged out after offline difftest is landed
  val peekLsuEnq = Module(new PeekLsuEnq(PeekLsuEnqParameter(dut.parameter.lsuParameters.lsuMSHRSize, latPeekLsuEnq)))
  peekLsuEnq.clock.ref := clockGen.clock
  peekLsuEnq.enq.ref := lsuProbe.reqEnq

  lsuProbe.slots.zipWithIndex.foreach {
    case (mshr, i) =>
      val peekWriteQueue = Module(new PeekWriteQueue(PeekWriteQueueParameter(
        dut.parameter.vrfParam.regNumBits,
        dut.parameter.laneNumber,
        dut.parameter.vrfParam.vrfOffsetBits,
        dut.parameter.vrfParam.instructionIndexBits,
        dut.parameter.vrfParam.datapathWidth,
        negLatPeekWriteQueue,
      )))
      peekWriteQueue.mshrIdx.ref := i.U
      peekWriteQueue.clock.ref := clockGen.clock
      peekWriteQueue.data_vd.ref := mshr.dataVd
      peekWriteQueue.data_offset.ref := mshr.dataOffset
      peekWriteQueue.data_mask.ref := mshr.dataMask
      peekWriteQueue.data_data.ref := mshr.dataData
      peekWriteQueue.data_instruction.ref := mshr.dataInstruction
      peekWriteQueue.writeValid.ref := mshr.writeValid
      peekWriteQueue.targetLane.ref := mshr.targetLane
  }

  laneVrfProbes.zipWithIndex.foreach {
    case (lane, i) =>
      val peekVrfWrite = Module(new PeekVrfWrite(PeekVrfWriteParameter(
        dut.parameter.vrfParam.regNumBits,
        dut.parameter.laneNumber,
        dut.parameter.vrfParam.vrfOffsetBits,
        dut.parameter.vrfParam.instructionIndexBits,
        dut.parameter.vrfParam.datapathWidth,
        negLatPeekWriteQueue,
      )))
      peekVrfWrite.landIdx.ref := i.U
      peekVrfWrite.valid.ref := lane.valid
      peekVrfWrite.clock.ref := clockGen.clock
      peekVrfWrite.request_vd.ref := lane.requestVd
      peekVrfWrite.request_offset.ref := lane.requestOffset
      peekVrfWrite.request_mask.ref := lane.requestMask
      peekVrfWrite.request_data.ref := lane.requestData
      peekVrfWrite.request_instruction.ref := lane.requestInstruction
  }

  val pokeInst = Module(new PokeInst(PokeInstParameter(dut.parameter.xLen, dut.parameter.laneParam.vlMaxBits, latPokeInst)))
  pokeInst.clock.ref := clockGen.clock
  pokeInst.respValid.ref := dut.response.valid
  pokeInst.response_data.ref := dut.response.bits.data
  pokeInst.response_vxsat.ref := dut.response.bits.vxsat
  pokeInst.response_rd_valid.ref := dut.response.bits.rd.valid
  pokeInst.response_rd_bits.ref := dut.response.bits.rd.bits
  pokeInst.response_mem.ref := dut.response.bits.mem
  dut.csrInterface.vl := pokeInst.csrInterface_vl.ref
  dut.csrInterface.vStart := pokeInst.csrInterface_vStart.ref
  dut.csrInterface.vlmul := pokeInst.csrInterface_vlMul.ref
  dut.csrInterface.vSew := pokeInst.csrInterface_vSew.ref
  dut.csrInterface.vxrm := pokeInst.csrInterface_vxrm.ref
  dut.csrInterface.vta := pokeInst.csrInterface_vta.ref
  dut.csrInterface.vma := pokeInst.csrInterface_vma.ref
  dut.csrInterface.ignoreException := pokeInst.csrInterface_ignoreException.ref
  dut.request.bits.instruction := pokeInst.request_instruction.ref
  dut.request.bits.src1Data := pokeInst.request_src1Data.ref
  dut.request.bits.src2Data := pokeInst.request_src2Data.ref
  dut.request.valid := pokeInst.instructionValid.ref

  val peekIssue = Module(new PeekIssue(PeekIssueParameter(dut.parameter.instructionIndexBits, latPeekIssue)))
  peekIssue.clock.ref := clockGen.clock
  peekIssue.ready.ref := dut.request.ready
  peekIssue.issueIdx.ref := t1Probe.instructionCounter

  dut.memoryPorts.zipWithIndex.foreach {
    case (bundle, idx) =>
      val peek = Module(new PeekTL(dut.parameter.tlParam.bundle(), latPeekTL))
      peek.clock.ref := clockGen.clock
      peek.channel.ref := idx.U
      peek.aBits_opcode.ref := bundle.a.bits.opcode
      peek.aBits_param.ref := bundle.a.bits.param
      peek.aBits_size.ref := bundle.a.bits.size
      peek.aBits_source.ref := bundle.a.bits.source
      peek.aBits_address.ref := bundle.a.bits.address
      peek.aBits_mask.ref := bundle.a.bits.mask
      peek.aBits_data.ref := bundle.a.bits.data
      peek.aBits_corrupt.ref := bundle.a.bits.corrupt

      peek.aValid.ref := bundle.a.valid
      peek.dReady.ref := bundle.d.ready

      val poke = Module(new PokeTL(dut.parameter.tlParam.bundle(), latPokeTL))
      poke.clock.ref := clockGen.clock
      poke.channel.ref := idx.U
      bundle.d.bits.opcode := poke.dBits_opcode.ref
      bundle.d.bits.param := poke.dBits_param.ref
      bundle.d.bits.sink := poke.dBits_sink.ref
      bundle.d.bits.source := poke.dBits_source.ref
      bundle.d.bits.size := poke.dBits_size.ref
      bundle.d.bits.denied := poke.dBits_denied.ref
      bundle.d.bits.data := poke.dBits_data.ref
      bundle.d.bits.corrupt := poke.dBits_corrupt.ref
      bundle.d.valid := poke.dValid.ref
      poke.dReady.ref := bundle.d.ready
      bundle.a.ready := poke.aReady.ref
  }
}
