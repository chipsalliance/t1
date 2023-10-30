// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package tests.elaborate

import chisel3._
import chisel3.experimental.SerializableModuleGenerator
import v.{V, VParameter}
import chisel3.probe._
import chisel3.util.experimental.BoringUtils.bore
import chisel3.util.DecoupledIO
import tilelink.TLChannelD


class TestBench(generator: SerializableModuleGenerator[V, VParameter]) extends RawModule {
  val clock = Wire(Clock())
  val reset = Wire(Bool())
  val dut: V = withClockAndReset(clock, reset)(Module(generator.module()))
  withClockAndReset(clock, reset) {
//    val coverModule = Module(new CoverModule(dut))
    val monitor = Module(new Monitor(dut))
    monitor.clock := clock
    monitor.reset := reset
  }

  val verificationModule = Module(new VerificationModule(dut))
  dut.request <> verificationModule.req
  dut.response <> verificationModule.resp
  dut.csrInterface <> verificationModule.csrInterface
  dut.storeBufferClear <> verificationModule.storeBufferClear
  dut.memoryPorts <> verificationModule.tlPort
  clock := verificationModule.clock
  reset := verificationModule.reset

  /**
    * [[v.LoadUnit]] related probe connection
    */
  val loadUnitMonitor = Module(new LoadUnitMonitor(LSUParam(generator.parameter.memoryBankSize, generator.parameter.laneNumber)))
  loadUnitMonitor.clock.ref := clock.asBool
  loadUnitMonitor.lsuRequestValid.ref := read(bore(dut.lsu.loadUnit.lsuRequestValidProbe))
  loadUnitMonitor.statusIdle.ref := read(bore(dut.lsu.loadUnit.idleProbe))
  loadUnitMonitor.tlPortAIsValid.ref := read(bore(dut.lsu.loadUnit.tlPortAValidProbe))
  loadUnitMonitor.tlPortAIsReady.ref := read(bore(dut.lsu.loadUnit.tlPortAReadyProbe))
  loadUnitMonitor.addressConflict.ref := read(bore(dut.lsu.loadUnit.addressConflictProbe))

  loadUnitMonitor.tlPortDIsReady
    .zip(loadUnitMonitor.tlPortDIsValid)
    .zipWithIndex.foreach { case ((ready, valid), index) =>
      ready.ref := read(bore(dut.lsu.loadUnit.tlPortDReadyProbe(index)))
      valid.ref := read(bore(dut.lsu.loadUnit.tlPortDValidProbe(index)))
    }

  loadUnitMonitor.queueReady
    .zip(loadUnitMonitor.queueValid)
    .zipWithIndex.foreach { case ((ready, valid), index) =>
      ready.ref := read(bore(dut.lsu.loadUnit.queueValidProbe(index)))
      valid.ref := read(bore(dut.lsu.loadUnit.queueReadyProbe(index)))
    }

  loadUnitMonitor.cacheLineDequeueReady
    .zip(loadUnitMonitor.cacheLineDequeueValid)
    .zipWithIndex.foreach { case ((ready, valid), index) =>
      ready.ref := read(bore(dut.lsu.loadUnit.cacheLineDequeueReadyProbe(index)))
      valid.ref := read(bore(dut.lsu.loadUnit.cacheLineDequeueValidProbe(index)))
    }

  loadUnitMonitor.unalignedCacheLine.ref := read(bore(dut.lsu.loadUnit.unalignedCacheLineProbe))
  loadUnitMonitor.alignedDequeueReady.ref := read(bore(dut.lsu.loadUnit.alignedDequeueReadyProbe))
  loadUnitMonitor.alignedDequeueValid.ref := read(bore(dut.lsu.loadUnit.alignedDequeueValidProbe))
  loadUnitMonitor.writeReadyForLSU.ref := read(bore(dut.lsu.loadUnit.writeReadyForLSUProbe))

  loadUnitMonitor.vrfWritePortReady
    .zip(loadUnitMonitor.vrfWritePortValid)
    .zipWithIndex.foreach { case ((ready, valid), index) =>
      ready.ref := read(bore(dut.lsu.loadUnit.vrfWriteReadyProbe(index)))
      valid.ref := read(bore(dut.lsu.loadUnit.vrfWriteValidProbe(index)))
    }

  val storeUnitMonitor = Module(new StoreUnitMonitor(LSUParam(generator.parameter.memoryBankSize, generator.parameter.laneNumber)))
  storeUnitMonitor.clock.ref := clock.asBool
  storeUnitMonitor.lsuRequestIsValid.ref := read(bore(dut.lsu.storeUnit.lsuRequestValidProbe))
  storeUnitMonitor.idle.ref := read(bore(dut.lsu.storeUnit.idleProbe))
  storeUnitMonitor.tlPortAIsReady
    .zip(storeUnitMonitor.tlPortAIsValid)
    .zipWithIndex.foreach{ case((ready, valid), index) =>
      ready.ref := read(bore(dut.lsu.storeUnit.tlPortAIsReadyProbe(index)))
      valid.ref := read(bore(dut.lsu.storeUnit.tlPortAIsValidProbe(index)))
    }
  storeUnitMonitor.addressConflict.ref := read(bore(dut.lsu.storeUnit.addressConflictProbe))
  storeUnitMonitor.vrfReadDataPortIsReady
    .zip(storeUnitMonitor.vrfReadDataPortIsValid)
    .zipWithIndex.foreach{ case((ready, valid), index) =>
      ready.ref := read(bore(dut.lsu.storeUnit.vrfReadDataPortIsReadyProbe(index)))
      valid.ref := read(bore(dut.lsu.storeUnit.vrfReadDataPortIsValidProbe(index)))
    }
  storeUnitMonitor.vrfReadyToStore.ref := read(bore(dut.lsu.storeUnit.vrfReadyToStoreProbe))
  storeUnitMonitor.alignedDequeueReady.ref := read(bore(dut.lsu.storeUnit.alignedDequeueReadyProbe))
  storeUnitMonitor.alignedDequeueValid.ref := read(bore(dut.lsu.storeUnit.alignedDequeueValidProbe))

  val vMonitor = Module(new VMonitor(VParam(generator.parameter.chainingSize)))
  vMonitor.clock.ref := clock.asBool
  vMonitor.requestValid.ref := read(bore(dut.requestValidProbe))
  vMonitor.requestReady.ref := read(bore(dut.requestReadyProbe))
  vMonitor.requestRegValid.ref := read(bore(dut.requestRegValidProbe))
  vMonitor.requestRegDequeueReady.ref := read(bore(dut.requestRegDequeueReadyProbe))
  vMonitor.requestRegDequeueValid.ref := read(bore(dut.requestRegDequeueValidProbe))
  vMonitor.executionReady.ref := read(bore(dut.executionReadyProbe))
  vMonitor.slotReady.ref := read(bore(dut.slotReadyProbe))
  vMonitor.waitForGather.ref := (!read(bore(dut.gatherNeedReadProbe))) || read(bore(dut.gatherReadFinishProbe))
  vMonitor.instructionRawReady.ref := read(bore(dut.instructionRAWReadyProbe))
  vMonitor.responseValid.ref := read(bore(dut.responseValidProbe))
  vMonitor.sMaskUnitExecuted
    .zip(vMonitor.wLast)
    .zip(vMonitor.isLastInst)
    .zipWithIndex.foreach { case(((sMaskUnit, wLast), isLastInst), index) =>
      sMaskUnit.ref := read(bore(dut.slotStateProbe(index)._1))
      wLast.ref := read(bore(dut.slotStateProbe(index)._2))
      isLastInst.ref := read(bore(dut.slotStateProbe(index)._3))
    }

  /**
    * [[v.SimpleAccessUnit]] related probe connection
    */
  val otherUnitMonitor = Module(new OtherUnitMonitor)
  otherUnitMonitor.clock.ref := clock.asBool
  otherUnitMonitor.lsuRequestIsValid.ref := read(bore(dut.lsu.otherUnit.lsuRequestValidProbe))
  otherUnitMonitor.s0EnqueueValid.ref := read(bore(dut.lsu.otherUnit.s0EnqueueValidProbe))
  otherUnitMonitor.stateIsRequest.ref := read(bore(dut.lsu.otherUnit.stateIsRequestProbe))
  otherUnitMonitor.maskCheck.ref := read(bore(dut.lsu.otherUnit.maskCheckProbe))
  otherUnitMonitor.indexCheck.ref := read(bore(dut.lsu.otherUnit.indexCheckProbe))
  otherUnitMonitor.fofCheck.ref := read(bore(dut.lsu.otherUnit.fofCheckProbe))
  otherUnitMonitor.s0Fire.ref := read(bore(dut.lsu.otherUnit.s0FireProbe))
  otherUnitMonitor.s1Fire.ref := read(bore(dut.lsu.otherUnit.s1FireProbe))
  otherUnitMonitor.s2Fire.ref := read(bore(dut.lsu.otherUnit.s2FireProbe))
  otherUnitMonitor.tlPortAIsReady.ref := read(bore(dut.lsu.otherUnit.tlPortAReadyProbe))
  otherUnitMonitor.tlPortAIsValid.ref := read(bore(dut.lsu.otherUnit.tlPortAValidProbe))
  otherUnitMonitor.s1Valid.ref := read(bore(dut.lsu.otherUnit.s1ValidProbe))
  otherUnitMonitor.sourceFree.ref := read(bore(dut.lsu.otherUnit.sourceFreeProbe))
  otherUnitMonitor.tlPortDIsReady.ref := read(bore(dut.lsu.otherUnit.tlPortDReadyProbe))
  otherUnitMonitor.tlPortDIsValid.ref := read(bore(dut.lsu.otherUnit.tlPortDValidProbe))
  otherUnitMonitor.vrfWritePortIsReady.ref := read(bore(dut.lsu.otherUnit.vrfWritePortIsReadyProbe))
  otherUnitMonitor.vrfWritePortIsValid.ref := read(bore(dut.lsu.otherUnit.vrfWritePortIsValidProbe))
  otherUnitMonitor.stateValue.ref := read(bore(dut.lsu.otherUnit.stateValueProbe))
  // End of [[v.SimpleAccessUnit]] related probe connection

  dut.laneVec.zipWithIndex.foreach({ case (lane, i) =>
    {
      val monitor = Module(new LaneReadBusPortMonitor)
      monitor.clock.ref := clock.asBool
      monitor.index.ref := i.U

      monitor.readBusPortEnqReady.ref := read(bore(lane.readBusPortEnqReadyProbe))
      monitor.readBusPortEnqValid.ref := read(bore(lane.readBusPortEnqValidProbe))
      monitor.readBusPortDeqReady.ref := read(bore(lane.readBusPortDeqReadyProbe))
      monitor.readBusPortDeqValid.ref := read(bore(lane.readBusPortDeqValidProbe))
    }
    {
      val monitor = Module(new LaneWriteBusPortMonitor)
      monitor.clock.ref := clock.asBool
      monitor.index.ref := i.U

      monitor.writeBusPortDeqReady.ref := read(bore(lane.writeBusPortDeqReadyProbe))
      monitor.writeBusPortDeqValid.ref := read(bore(lane.writeBusPortDeqValidProbe))
      monitor.writeBusPortEnqReady.ref := read(bore(lane.writeBusPortEnqReadyProbe))
      monitor.writeBusPortEnqValid.ref := read(bore(lane.writeBusPortEnqValidProbe))
    }
    {
      val monitor = Module(new LaneRequestMonitor)
      monitor.clock.ref := clock.asBool
      monitor.index.ref := i.U

      monitor.isReady.ref := read(bore(lane.laneRequestReadyProbe))
      monitor.isValid.ref := read(bore(lane.laneRequestValidProbe))
    }
    {
      val monitor = Module(new LaneResponseMonitor)
      monitor.clock.ref := clock.asBool
      monitor.index.ref := i.U

      monitor.isValid.ref := read(bore(lane.laneResponseValidProbe))
      monitor.laneResponseFeedbackValid.ref := read(bore(lane.laneResponseFeedbackValidProbe))
    }
    {
      val monitor = Module(new LaneVrfReadMonitor)
      monitor.clock.ref := clock.asBool
      monitor.index.ref := i.U

      monitor.isValid.ref := read(bore(lane.vrfReadAddressChannelValidProbe))
      monitor.isReady.ref := read(bore(lane.vrfReadAddressChannelReadyProbe))
    }
    {
      val monitor = Module(new LaneVrfWriteMonitor)
      monitor.clock.ref := clock.asBool
      monitor.index.ref := i.U

      monitor.isValid.ref := read(bore(lane.vrfWriteChannelValidProbe))
      monitor.isReady.ref := read(bore(lane.vrfWriteChannelReadyProbe))
    }
    {
      val monitor = Module(new LaneStatusMonitor)
      monitor.clock.ref := clock.asBool
      monitor.index.ref := i.U

      monitor.v0UpdateValid.ref := read(bore(lane.v0UpdateValidProbe))
      monitor.writeReadyForLsu.ref := read(bore(lane.writeReadyForLsuProbe))
      monitor.vrfReadyToStore.ref := read(bore(lane.vrfReadyToStoreProbe))
    }
    {
      val monitor = Module(new LaneWriteQueueMonitor)
      monitor.clock.ref := clock.asBool
      monitor.index.ref := i.U

      monitor.isValid.ref := read(bore(lane.writeQueueValidProbe))
    }
    {
      val monitor = Module(new LaneReadBusDequeueMonitor)
      monitor.clock.ref := clock.asBool
      monitor.index.ref := i.U

      monitor.isValid.ref := read(bore(lane.readBusDequeueValidProbe))
    }
    {
      val monitor = Module(new CrossLaneMonitor)
      monitor.clock.ref := clock.asBool
      monitor.index.ref := i.U

      monitor.readValid.ref := read(bore(lane.crossLaneReadValidProbe))
      monitor.writeValid.ref := read(bore(lane.crossLaneWriteValidProbe))
    }
    {
      val monitor = Module(new LaneReadBusDataMonitor)
      monitor.clock.ref := clock.asBool
      monitor.index.ref := i.U

      monitor.isValid.ref := read(bore(lane.readBusDataReqValidProbe))
    }
    {
      val monitor = Module(new LaneWriteBusDataMonitor)
      monitor.clock.ref := clock.asBool
      monitor.index.ref := i.U

      monitor.isValid.ref := read(bore(lane.writeBusDataReqValidProbe))
    }
  })
 }
