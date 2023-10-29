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
  val loadUnitMonitor = Module(new LoadUnitMonitor(LoadUnitMonitorParam(generator.parameter.memoryBankSize, generator.parameter.laneNumber)))
  loadUnitMonitor.clock.ref := clock.asBool
  loadUnitMonitor.lsuRequestValid.ref := read(bore(dut.lsu.loadUnit.lsuRequestValidProbe))
  loadUnitMonitor.statusIdle.ref := read(bore(dut.lsu.loadUnit.idleProbe))
  loadUnitMonitor.tlPortAIsValid.ref := read(bore(dut.lsu.loadUnit.tlPortAValidProbe))
  loadUnitMonitor.tlPortAIsReady.ref := read(bore(dut.lsu.loadUnit.tlPortAReadyProbe))
  loadUnitMonitor.addressConflict.ref := read(bore(dut.lsu.loadUnit.addressConflictProbe))

  loadUnitMonitor.tlPortDIsReady.zipWithIndex.foreach {
    case (dpi, index) => dpi.ref := read(bore(dut.lsu.loadUnit.tlPortDReadyProbe(index)))
  }
  loadUnitMonitor.tlPortDIsValid.zipWithIndex.foreach {
    case (dpi, index) => dpi.ref := read(bore(dut.lsu.loadUnit.tlPortDValidProbe(index)))
  }

  loadUnitMonitor.queueValid.zipWithIndex.foreach {
    case (dpi, index) => dpi.ref := read(bore(dut.lsu.loadUnit.queueValidProbe(index)))
  }
  loadUnitMonitor.queueReady.zipWithIndex.foreach {
    case (dpi, index) => dpi.ref := read(bore(dut.lsu.loadUnit.queueReadyProbe(index)))
  }
  loadUnitMonitor.cacheLineDequeueValid.zipWithIndex.foreach {
    case (dpi, index) => dpi.ref := read(bore(dut.lsu.loadUnit.cacheLineDequeueValidProbe(index)))
  }
  loadUnitMonitor.cacheLineDequeueReady.zipWithIndex.foreach {
    case (dpi, index) => dpi.ref := read(bore(dut.lsu.loadUnit.cacheLineDequeueReadyProbe(index)))
  }
  loadUnitMonitor.unalignedCacheLine.ref := read(bore(dut.lsu.loadUnit.unalignedCacheLineProbe))
  loadUnitMonitor.alignedDequeueReady.ref := read(bore(dut.lsu.loadUnit.alignedDequeueReadyProbe))
  loadUnitMonitor.alignedDequeueValid.ref := read(bore(dut.lsu.loadUnit.alignedDequeueValidProbe))
  loadUnitMonitor.writeReadyForLSU.ref := read(bore(dut.lsu.loadUnit.writeReadyForLSUProbe))

  loadUnitMonitor.vrfWritePortReady.zipWithIndex.foreach {
    case (dpi, index) => dpi.ref := read(bore(dut.lsu.loadUnit.vrfWriteReadyProbe(index)))
  }
  loadUnitMonitor.vrfWritePortValid.zipWithIndex.foreach {
    case (dpi, index) => dpi.ref := read(bore(dut.lsu.loadUnit.vrfWriteValidProbe(index)))
  }


  /**
    * [[v.SimpleAccessUnit]] related probe connection
    */
  val otherUnitMonitor = Module(new OtherUnitMonitor)
  otherUnitMonitor.clock.ref := clock.asBool
  otherUnitMonitor.lsuRequestIsValid.ref := read(bore(dut.lsu.otherUnit.lsuRequestValidProbe))
  otherUnitMonitor.vrfReadDataPortsIsReady.ref := read(bore(dut.lsu.otherUnit.vrfReadDataPortsValidProbe))
  otherUnitMonitor.vrfReadDataPortsIsValid.ref := read(bore(dut.lsu.otherUnit.vrfReadDataPortsReadyProbe))
  otherUnitMonitor.maskSelectIsValid.ref := read(bore(dut.lsu.otherUnit.maskSelectValidProbe))
  otherUnitMonitor.vrfWritePortIsReady.ref := read(bore(dut.lsu.otherUnit.vrfWritePortIsReadyProbe))
  otherUnitMonitor.vrfWritePortIsValid.ref := read(bore(dut.lsu.otherUnit.vrfWritePortIsValidProbe))
  otherUnitMonitor.targetLane.ref := read(bore(dut.lsu.otherUnit.targetLaneProbe))
  otherUnitMonitor.idle.ref := read(bore(dut.lsu.otherUnit.statusIdleProbe))
  otherUnitMonitor.s0Fire.ref := read(bore(dut.lsu.otherUnit.s0FireProbe))
  otherUnitMonitor.s1Fire.ref := read(bore(dut.lsu.otherUnit.s1FireProbe))
  otherUnitMonitor.s2Fire.ref := read(bore(dut.lsu.otherUnit.s2FireProbe))

  val otherUnitAccessTileLinkMonitor = Module(new OtherUnitAccessTileLinkMonitor)
  otherUnitAccessTileLinkMonitor.clock.ref := clock.asBool
  otherUnitAccessTileLinkMonitor.isValid.ref := read(bore(dut.lsu.otherUnit.accessTileLinkValidProbe))
  otherUnitAccessTileLinkMonitor.isReady.ref := read(bore(dut.lsu.otherUnit.accessTileLinkReadyProbe))

  val otherUnitTileLinkAckMonitor = Module(new OtherUnitTileLinkAckMonitor)
  otherUnitTileLinkAckMonitor.clock.ref := clock.asBool
  otherUnitTileLinkAckMonitor.isValid.ref := read(bore(dut.lsu.otherUnit.tileLinkAckValidProbe))
  otherUnitTileLinkAckMonitor.isReady.ref := read(bore(dut.lsu.otherUnit.tileLinkAckReadyProbe))

  dut.lsu.otherUnit.offsetReadResultValidProbe.zipWithIndex.foreach({ case(probe, i) =>
    val monitor = Module(new OtherUnitOffsetReadResultMonitor)
    monitor.clock.ref := clock.asBool
    monitor.index.ref := i.U
    monitor.isValid.ref := read(bore(probe))
  })

  dut.lsu.otherUnit.indexedInsturctionOffsetsIsValidProbe.zipWithIndex.foreach({ case(probe, i) =>
    val monitor = Module(new OtherUnitIndexedInsnOffsetsIsValidMonitor)
    monitor.clock.ref := clock.asBool
    monitor.index.ref := i.U
    monitor.isValid.ref := read(bore(probe))
  })
  // End of [[v.SimpleAccessUnit]] related probe connection

  /**
   * [[v.StoreUnit]] related probe connection
   */
  val storeUnitMonitor = Module(new StoreUnitMonitor)
  storeUnitMonitor.clock.ref := clock.asBool
  storeUnitMonitor.vrfReadyToStore.ref := read(bore(dut.lsu.storeUnit.vrfReadyToStoreProbe))

  val storeUnitAlignedDequeueMonitor = Module(new StoreUnitAlignedDequeueMonitor)
  storeUnitAlignedDequeueMonitor.clock.ref := clock.asBool
  storeUnitAlignedDequeueMonitor.isValid.ref := read(bore(dut.lsu.storeUnit.alignedDequeueValidProbe))
  storeUnitAlignedDequeueMonitor.isReady.ref := read(bore(dut.lsu.storeUnit.alignedDequeueReadyProbe))

  dut.lsu.storeUnit.tlPortAIsReadyProbe
    .zip(dut.lsu.storeUnit.tlPortAIsValidProbe)
    .zipWithIndex.foreach({ case((ready, valid), i) =>
      val monitor = Module(new StoreUnitTlPortAMonitor)
      monitor.clock.ref := clock.asBool
      monitor.index.ref := i.U
      monitor.isReady.ref := read(bore(ready))
      monitor.isValid.ref := read(bore(valid))
  })

  dut.lsu.storeUnit.vrfReadDataPortIsReadyProbe
    .zip(dut.lsu.storeUnit.vrfReadDataPortIsValidProbe)
    .zipWithIndex.foreach({ case((ready, valid), i) =>
      val monitor = Module(new StoreUnitVrfReadDataPortMonitor)
      monitor.clock.ref := clock.asBool
      monitor.index.ref := i.U
      monitor.isReady.ref := read(bore(ready))
      monitor.isValid.ref := read(bore(valid))
  })

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

  val vRequestMonitor = Module(new VRequestMonitor)
  vRequestMonitor.clock.ref := clock.asBool
  vRequestMonitor.isValid.ref := read(bore(dut.requestValidProbe))
  vRequestMonitor.isReady.ref := read(bore(dut.requestReadyProbe))

  val vResponseMonitor = Module(new VResponseMonitor)
  vResponseMonitor.clock.ref := clock.asBool
  vResponseMonitor.isValid.ref := read(bore(dut.responseValidProbe))

  val vRequestRegMonitor = Module(new VRequestRegMonitor)
  vRequestRegMonitor.clock.ref := clock.asBool
  vRequestRegMonitor.isValid.ref := read(bore(dut.requestValidProbe))

  val vRequestRegDequeueMonitor = Module(new VRequestRegDequeueMonitor)
  vRequestRegDequeueMonitor.clock.ref := clock.asBool
  vRequestRegDequeueMonitor.isValid.ref := read(bore(dut.requestRegDequeueValidProbe))
  vRequestRegDequeueMonitor.isReady.ref := read(bore(dut.requestRegDequeueReadyProbe))

  val vMaskedUnitWriteValid = Module(new VMaskUnitWriteValidMonitor)
  vMaskedUnitWriteValid.clock.ref := clock.asBool
  vMaskedUnitWriteValid.isValid.ref := read(bore(dut.maskUnitWriteValidProbe))

  dut.maskUnitWriteValidProbesVec.zipWithIndex.foreach({ case(probe, i) =>
    val monitor = Module(new VMaskUnitWriteValidIndexedMonitor)
    monitor.clock.ref := clock.asBool
    monitor.index.ref := i.U
    monitor.isValid.ref := read(bore(probe))
  })

  val vMaskUnitWriteValid = Module(new VMaskUnitReadValidMonitor)
  vMaskUnitWriteValid.clock.ref := clock.asBool
  vMaskUnitWriteValid.isValid.ref := read(bore(dut.maskUnitReadValidProbe))

  dut.maskUnitReadValidProbeVec.zipWithIndex.foreach({ case(probe, i) =>
    val monitor = Module(new VMaskUnitReadValidIndexedMonitor)
    monitor.clock.ref := clock.asBool
    monitor.index.ref := i.U
    monitor.isValid.ref := read(bore(probe))
  })

  val vWARRedResultMonitor = Module(new VWarReadResultValidMonitor)
  vWARRedResultMonitor.clock.ref := clock.asBool
  vWARRedResultMonitor.isValid.ref := read(bore(dut.WARRedResultValidProbe))

  dut.dataValidProbes.zipWithIndex.foreach({ case(probe, i) =>
    val vDataMonitor = Module(new VDataMonitor)
    vDataMonitor.clock.ref := clock.asBool
    vDataMonitor.index.ref := i.U
    vDataMonitor.isValid.ref := read(bore(probe))
  })

  val vDataResultMonitor = Module(new VDataResultMonitor)
  vDataResultMonitor.clock.ref := clock.asBool
  vDataResultMonitor.isValid.ref := read(bore(dut.dataResultValidProbe))

  val vSelectffoIndexMonitor = Module(new VSelectffoIndexMonitor)
  vSelectffoIndexMonitor.clock.ref := clock.asBool
  vSelectffoIndexMonitor.isValid.ref := read(bore(dut.selectffoIndexValidProbe))

  val vExecutionReadyMonitor = Module(new VExecutionReadyMonitor)
  vExecutionReadyMonitor.clock.ref := clock.asBool
  vExecutionReadyMonitor.isReady.ref := read(bore(dut.executionReadyProbe))

  val vInsnRawReadyMonitor = Module(new VInsnRawReadyMonitor)
  vInsnRawReadyMonitor.clock.ref := clock.asBool
  vInsnRawReadyMonitor.isReady.ref := read(bore(dut.insnRAWReadyProbe))

  val vSlotReadyMonitor = Module(new VSlotReadyMonitor)
  vSlotReadyMonitor.clock.ref := clock.asBool
  vSlotReadyMonitor.isReady.ref := read(bore(dut.slotReadyProbe))

  dut.laneReadyProbe.zipWithIndex.foreach({ case(probe, i) =>
    val monitor = Module(new VLaneReadyMonitor)
    monitor.clock.ref := clock.asBool
    monitor.index.ref := i.U
    monitor.isReady.ref := read(bore(probe))
  })

  dut.slotStateIdleProbe.zipWithIndex.foreach({ case(state, i) =>
    val monitor = Module(new VSlotStatIdleMonitor)
    monitor.clock.ref := clock.asBool
    monitor.index.ref := i.U
    monitor.idle.ref := read(bore(state))
  })

  dut.vrfWriteReadyProbe.zip(dut.vrfWriteValidProbe)
    .zipWithIndex.foreach({ case((readyProbe, validProbe), i) =>
      val monitor = Module(new VVrfWriteMonitor)
      monitor.clock.ref := clock.asBool
      monitor.index.ref := i.U
      monitor.isReady.ref := read(bore(readyProbe))
      monitor.isValid.ref := read(bore(validProbe))
    })
 }
