// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu

import chisel3._
import chisel3.experimental.SerializableModuleGenerator
import chisel3.experimental.dataview.DataViewable
import chisel3.util.experimental.BoringUtils.bore
import org.chipsalliance.amba.axi4.bundle._
import org.chipsalliance.t1.ipemu.dpi._
import org.chipsalliance.t1.rtl.{T1, T1Parameter}

class TestBench(generator: SerializableModuleGenerator[T1, T1Parameter]) extends RawModule {
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

  val lsuProbe = probe.read(dut.lsuProbe).suggestName("lsuProbe")

  val laneProbes = dut.laneProbes.zipWithIndex.map{case (p, idx) =>
    val wire = Wire(p.cloneType).suggestName(s"lane${idx}Probe")
    wire := probe.read(p)
    wire
  }

  val laneVrfProbes = dut.laneVrfProbes.zipWithIndex.map{case (p, idx) =>
    val wire = Wire(p.cloneType).suggestName(s"lane${idx}VrfProbe")
    wire := probe.read(p)
    wire
  }

  val t1Probe = probe.read(dut.t1Probe).suggestName("instructionCountProbe")

  // Monitor
  withClockAndReset(clock, reset)(Module(new Module {
    // h/t: GrandCentral
    override def desiredName: String = "XiZhiMen"
    val lsuProbeMonitor = bore(lsuProbe)
    dontTouch(lsuProbeMonitor)
    val laneProbesMonitor = laneProbes.map(bore(_))
    laneProbesMonitor.foreach(dontTouch(_))
    val laneVrfProbesMonitor = laneVrfProbes.map(bore(_))
    laneVrfProbesMonitor.foreach(dontTouch(_))
  }))

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

  Seq(
    dut.highBandwidthLoadStorePort,
    dut.indexedLoadStorePort
  ).map(_.viewAs[AXI4RWIrrevocableVerilog]).zip(
    Seq("highBandwidthPort", "indexedAccessPort")
  ).foreach {
    case (bundle: AXI4RWIrrevocableVerilog, channelName: String) =>
      val aw = Module(new awDpi(channelName, chiselTypeOf(bundle), latPeekTL)).suggestName(s"${channelName}_aw_dpi")
      val w = Module(new wDpi(channelName, chiselTypeOf(bundle), latPeekTL)).suggestName(s"${channelName}_w_dpi")
      val b = Module(new bDpi(channelName, chiselTypeOf(bundle), latPeekTL)).suggestName(s"${channelName}_b_dpi")
      val ar = Module(new arDpi(channelName, chiselTypeOf(bundle), latPeekTL)).suggestName(s"${channelName}_ar_dpi")
      val r = Module(new rDpi(channelName, chiselTypeOf(bundle), latPeekTL)).suggestName(s"${channelName}_r_dpi")



      aw.clock.ref := clock.asBool
      w.clock.ref := clock.asBool
      b.clock.ref := clock.asBool
      ar.clock.ref := clock.asBool
      r.clock.ref := clock.asBool
      // IP -> Sim
      aw.AWID.ref := bundle.AWID
      aw.AWADDR.ref := bundle.AWADDR
      aw.AWLEN.ref := bundle.AWLEN
      aw.AWSIZE.ref := bundle.AWSIZE
      aw.AWBURST.ref := bundle.AWBURST
      aw.AWLOCK.ref := bundle.AWLOCK
      aw.AWCACHE.ref := bundle.AWCACHE
      aw.AWPROT.ref := bundle.AWPROT
      aw.AWQOS.ref := bundle.AWQOS
      aw.AWREGION.ref := bundle.AWREGION
      aw.AWUSER.ref := bundle.AWUSER
      aw.AWVALID.ref := bundle.AWVALID
      // Sim -> IP
      bundle.AWREADY := aw.AWVALID.ref

      // IP -> Sim
      w.WDATA.ref := bundle.WDATA
      w.WSTRB.ref := bundle.WSTRB
      w.WLAST.ref := bundle.WLAST
      w.WUSER.ref := bundle.WUSER
      w.WVALID.ref := bundle.WVALID
      // Sim -> IP
      bundle.WREADY := w.WREADY.ref

      // Sim -> IP
      bundle.BID := b.BID.ref
      bundle.BRESP := b.BRESP.ref
      bundle.BUSER := b.BUSER.ref
      bundle.BVALID := b.BVALID.ref

      // IP -> Sim
      b.BREADY.ref := bundle.BREADY

      // IP -> Sim
      ar.ARID.ref := bundle.ARID
      ar.ARADDR.ref := bundle.ARADDR
      ar.ARLEN.ref := bundle.ARLEN
      ar.ARSIZE.ref := bundle.ARSIZE
      ar.ARBURST.ref := bundle.ARBURST
      ar.ARLOCK.ref := bundle.ARLOCK
      ar.ARCACHE.ref := bundle.ARCACHE
      ar.ARPROT.ref := bundle.ARPROT
      ar.ARQOS.ref := bundle.ARQOS
      ar.ARREGION.ref := bundle.ARREGION
      ar.ARUSER.ref := bundle.ARUSER
      ar.ARVALID.ref := bundle.ARVALID
      // IP -> Sim
      bundle.ARREADY := ar.ARREADY.ref

      // Sim -> IP
      bundle.RID := r.RID.ref
      bundle.RDATA := r.RDATA.ref
      bundle.RRESP := r.RRESP.ref
      bundle.RLAST := r.RLAST.ref
      bundle.RUSER := r.RUSER.ref
      bundle.RVALID := r.RVALID.ref
      // IP -> Sim
      r.RREADY.ref := bundle.RREADY
  }
}
