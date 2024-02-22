// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu

import chisel3._
import chisel3.experimental.SerializableModuleGenerator
import chisel3.probe._
import chisel3.util.experimental.BoringUtils.bore
import org.chipsalliance.t1.rtl.{T1, T1Parameter}


class TestBench(generator: SerializableModuleGenerator[T1, T1Parameter]) extends RawModule {
  val clock = Wire(Clock())
  val reset = Wire(Bool())
  val dut: T1 = withClockAndReset(clock, reset)(Module(generator.module()))
  withClockAndReset(clock, reset) {
    val monitor = Module(new Monitor(dut))
    monitor.clock := clock
    monitor.reset := reset
  }

  val verificationModule = Module(new VerificationModule(dut))
  dut.request <> verificationModule.req
  dut.response <> verificationModule.resp
  dut.csrInterface <> verificationModule.csrInterface
  dut.storeBufferClear <> verificationModule.storeBufferClear
  dut.memoryPorts.head <> verificationModule.tlPort
  clock := verificationModule.clock
  reset := verificationModule.reset

  /**
    * [[v.LoadUnit]] related probe connection
    */
  // val loadUnitMonitor = Module(new LoadUnitMonitor(LSUParam(generator.parameter.memoryBankSize, generator.parameter.laneNumber)))
  // loadUnitMonitor.clock.ref := clock.asBool
  // loadUnitMonitor.lsuRequestValid.ref := read(bore(dut.lsu.loadUnit.lsuRequestValidProbe))
  // loadUnitMonitor.statusIdle.ref := read(bore(dut.lsu.loadUnit.idleProbe))
  // loadUnitMonitor.tlPortAIsValid.ref := read(bore(dut.lsu.loadUnit.tlPortAValidProbe))
  // loadUnitMonitor.tlPortAIsReady.ref := read(bore(dut.lsu.loadUnit.tlPortAReadyProbe))
  // loadUnitMonitor.addressConflict.ref := read(bore(dut.lsu.loadUnit.addressConflictProbe))
  //
  // loadUnitMonitor.tlPortDIsReady
  //   .zip(loadUnitMonitor.tlPortDIsValid)
  //   .zipWithIndex.foreach { case ((ready, valid), index) =>
  //     ready.ref := read(bore(dut.lsu.loadUnit.tlPortDReadyProbe(index)))
  //     valid.ref := read(bore(dut.lsu.loadUnit.tlPortDValidProbe(index)))
  //   }
  //
  // loadUnitMonitor.queueReady
  //   .zip(loadUnitMonitor.queueValid)
  //   .zipWithIndex.foreach { case ((ready, valid), index) =>
  //     ready.ref := read(bore(dut.lsu.loadUnit.queueValidProbe(index)))
  //     valid.ref := read(bore(dut.lsu.loadUnit.queueReadyProbe(index)))
  //   }
  //
  // loadUnitMonitor.cacheLineDequeueReady
  //   .zip(loadUnitMonitor.cacheLineDequeueValid)
  //   .zipWithIndex.foreach { case ((ready, valid), index) =>
  //     ready.ref := read(bore(dut.lsu.loadUnit.cacheLineDequeueReadyProbe(index)))
  //     valid.ref := read(bore(dut.lsu.loadUnit.cacheLineDequeueValidProbe(index)))
  //   }
  //
  // loadUnitMonitor.unalignedCacheLine.ref := read(bore(dut.lsu.loadUnit.unalignedCacheLineProbe))
  // loadUnitMonitor.alignedDequeueReady.ref := read(bore(dut.lsu.loadUnit.alignedDequeueReadyProbe))
  // loadUnitMonitor.alignedDequeueValid.ref := read(bore(dut.lsu.loadUnit.alignedDequeueValidProbe))
  // loadUnitMonitor.writeReadyForLSU.ref := read(bore(dut.lsu.loadUnit.writeReadyForLSUProbe))
  //
  // loadUnitMonitor.vrfWritePortReady
  //   .zip(loadUnitMonitor.vrfWritePortValid)
  //   .zipWithIndex.foreach { case ((ready, valid), index) =>
  //     ready.ref := read(bore(dut.lsu.loadUnit.vrfWriteReadyProbe(index)))
  //     valid.ref := read(bore(dut.lsu.loadUnit.vrfWriteValidProbe(index)))
  //   }

  // val storeUnitMonitor = Module(new StoreUnitMonitor(LSUParam(generator.parameter.memoryBankSize, generator.parameter.laneNumber)))
  // storeUnitMonitor.clock.ref := clock.asBool
  // storeUnitMonitor.lsuRequestIsValid.ref := read(bore(dut.lsu.storeUnit.lsuRequestValidProbe))
  // storeUnitMonitor.idle.ref := read(bore(dut.lsu.storeUnit.idleProbe))
  // storeUnitMonitor.tlPortAIsReady
  //   .zip(storeUnitMonitor.tlPortAIsValid)
  //   .zipWithIndex.foreach{ case((ready, valid), index) =>
  //     ready.ref := read(bore(dut.lsu.storeUnit.tlPortAIsReadyProbe(index)))
  //     valid.ref := read(bore(dut.lsu.storeUnit.tlPortAIsValidProbe(index)))
  //   }
  // storeUnitMonitor.addressConflict.ref := read(bore(dut.lsu.storeUnit.addressConflictProbe))
  // storeUnitMonitor.vrfReadDataPortIsReady
  //   .zip(storeUnitMonitor.vrfReadDataPortIsValid)
  //   .zipWithIndex.foreach{ case((ready, valid), index) =>
  //     ready.ref := read(bore(dut.lsu.storeUnit.vrfReadDataPortIsReadyProbe(index)))
  //     valid.ref := read(bore(dut.lsu.storeUnit.vrfReadDataPortIsValidProbe(index)))
  //   }
  // storeUnitMonitor.vrfReadyToStore.ref := read(bore(dut.lsu.storeUnit.vrfReadyToStoreProbe))
  // storeUnitMonitor.alignedDequeueReady.ref := read(bore(dut.lsu.storeUnit.alignedDequeueReadyProbe))
  // storeUnitMonitor.alignedDequeueValid.ref := read(bore(dut.lsu.storeUnit.alignedDequeueValidProbe))

  // val vMonitor = Module(new VMonitor(VParam(generator.parameter.chainingSize)))
  // vMonitor.clock.ref := clock.asBool
  // vMonitor.requestValid.ref := read(bore(dut.requestValidProbe))
  // vMonitor.requestReady.ref := read(bore(dut.requestReadyProbe))
  // vMonitor.requestRegValid.ref := read(bore(dut.requestRegValidProbe))
  // vMonitor.requestRegDequeueReady.ref := read(bore(dut.requestRegDequeueReadyProbe))
  // vMonitor.requestRegDequeueValid.ref := read(bore(dut.requestRegDequeueValidProbe))
  // vMonitor.executionReady.ref := read(bore(dut.executionReadyProbe))
  // vMonitor.slotReady.ref := read(bore(dut.slotReadyProbe))
  // vMonitor.waitForGather.ref := (!read(bore(dut.gatherNeedReadProbe))) || read(bore(dut.gatherReadFinishProbe))
  // vMonitor.instructionRawReady.ref := read(bore(dut.instructionRAWReadyProbe))
  // vMonitor.responseValid.ref := read(bore(dut.responseValidProbe))
  // vMonitor.sMaskUnitExecuted
  //   .zip(vMonitor.wLast)
  //   .zip(vMonitor.isLastInst)
  //   .zipWithIndex.foreach { case(((sMaskUnit, wLast), isLastInst), index) =>
  //     sMaskUnit.ref := read(bore(dut.slotStateProbe(index)._1))
  //     wLast.ref := read(bore(dut.slotStateProbe(index)._2))
  //     isLastInst.ref := read(bore(dut.slotStateProbe(index)._3))
  //   }

  /**
    * [[v.SimpleAccessUnit]] related probe connection
    */
  // val otherUnitMonitor = Module(new OtherUnitMonitor)
  // otherUnitMonitor.clock.ref := clock.asBool
  // otherUnitMonitor.lsuRequestIsValid.ref := read(bore(dut.lsu.otherUnit.lsuRequestValidProbe))
  // otherUnitMonitor.s0EnqueueValid.ref := read(bore(dut.lsu.otherUnit.s0EnqueueValidProbe))
  // otherUnitMonitor.stateIsRequest.ref := read(bore(dut.lsu.otherUnit.stateIsRequestProbe))
  // otherUnitMonitor.maskCheck.ref := read(bore(dut.lsu.otherUnit.maskCheckProbe))
  // otherUnitMonitor.indexCheck.ref := read(bore(dut.lsu.otherUnit.indexCheckProbe))
  // otherUnitMonitor.fofCheck.ref := read(bore(dut.lsu.otherUnit.fofCheckProbe))
  // otherUnitMonitor.s0Fire.ref := read(bore(dut.lsu.otherUnit.s0FireProbe))
  // otherUnitMonitor.s1Fire.ref := read(bore(dut.lsu.otherUnit.s1FireProbe))
  // otherUnitMonitor.s2Fire.ref := read(bore(dut.lsu.otherUnit.s2FireProbe))
  // otherUnitMonitor.tlPortAIsReady.ref := read(bore(dut.lsu.otherUnit.tlPortAReadyProbe))
  // otherUnitMonitor.tlPortAIsValid.ref := read(bore(dut.lsu.otherUnit.tlPortAValidProbe))
  // otherUnitMonitor.s1Valid.ref := read(bore(dut.lsu.otherUnit.s1ValidProbe))
  // otherUnitMonitor.sourceFree.ref := read(bore(dut.lsu.otherUnit.sourceFreeProbe))
  // otherUnitMonitor.tlPortDIsReady.ref := read(bore(dut.lsu.otherUnit.tlPortDReadyProbe))
  // otherUnitMonitor.tlPortDIsValid.ref := read(bore(dut.lsu.otherUnit.tlPortDValidProbe))
  // otherUnitMonitor.vrfWritePortIsReady.ref := read(bore(dut.lsu.otherUnit.vrfWritePortIsReadyProbe))
  // otherUnitMonitor.vrfWritePortIsValid.ref := read(bore(dut.lsu.otherUnit.vrfWritePortIsValidProbe))
  // otherUnitMonitor.stateValue.ref := read(bore(dut.lsu.otherUnit.stateValueProbe))
  // End of [[v.SimpleAccessUnit]] related probe connection

  // dut.laneVec.zipWithIndex.foreach({ case (lane, laneIndex) =>
  //   val laneMonitor = Module(new LaneMonitor(LaneParam(generator.parameter.chainingSize)))
  //   laneMonitor.clock.ref := clock.asBool
  //   laneMonitor.index.ref := laneIndex.U
  //   laneMonitor.laneRequestReady.ref := read(bore(lane.laneRequestReadyProbe))
  //   laneMonitor.laneRequestValid.ref := read(bore(lane.laneRequestValidProbe))
  //   laneMonitor.lastSlotOccupied.ref := read(bore(lane.lastSlotOccupiedProbe))
  //   laneMonitor.vrfInstructionWriteReportReady.ref := read(bore(lane.vrfInstructionWriteReportReadyProbe))
  //   laneMonitor.slotOccupied.zipWithIndex.foreach { case(dpi, index) =>
  //     dpi.ref := read(bore(lane.slotOccupiedProbe(index)))
  //   }
  //   laneMonitor.instructionFinished.ref := read(bore(lane.instructionFinishedProbe))
  //
  //   lane.slotProbes.zipWithIndex.foreach({ case(probes, slotIndex) =>
  //     val isLastSlot = probes.stage1Probes.sSendCrossReadResultLSBProbe.isDefined
  //     if (isLastSlot) {
  //       val slotMonitor = Module(new LaneLastSlotMonitor())
  //       slotMonitor.clock.ref := clock.asBool
  //       slotMonitor.laneIndex.ref := laneIndex.U
  //       slotMonitor.slotIndex.ref := slotIndex.U
  //
  //       slotMonitor.stage0EnqueueReady.ref := read(bore(probes.stage0EnqueueReady))
  //       slotMonitor.stage0EnqueueValid.ref := read(bore(probes.stage0EnqueueValid))
  //       slotMonitor.changingMaskSet.ref := read(bore(probes.changingMaskSet))
  //       slotMonitor.slotActive.ref := read(bore(probes.slotActive))
  //       slotMonitor.slotOccupied.ref := read(bore(probes.slotOccupied))
  //       slotMonitor.pipeFinish.ref := read(bore(probes.pipeFinish))
  //
  //       slotMonitor.slotShiftValid.ref := read(bore(probes.slotShiftValid))
  //       slotMonitor.decodeResultIsCrossReadOrWrite.ref := read(bore(probes.decodeResultIsCrossReadOrWrite))
  //       slotMonitor.decodeResultIsScheduler.ref := read(bore(probes.decodeResultIsScheduler))
  //
  //       slotMonitor.stage1DequeueReady.ref := read(bore(probes.stage1Probes.dequeueReadyProbe))
  //       slotMonitor.stage1DequeueValid.ref := read(bore(probes.stage1Probes.dequeueValidProbe))
  //       slotMonitor.stage1HasDataOccupied.ref := read(bore(probes.stage1Probes.hasDataOccupiedProbe))
  //       slotMonitor.stage1Finishing.ref := read(bore(probes.stage1Probes.stageFinishProbe))
  //
  //       probes.stage1Probes.readFinishProbe.map(p => slotMonitor.stage1ReadFinish.ref := read(bore(p)))
  //       probes.stage1Probes.sSendCrossReadResultLSBProbe.map(p => slotMonitor.stage1sSendCrossReadResultLSB.ref := read(bore(p)))
  //       probes.stage1Probes.sSendCrossReadResultMSBProbe.map(p => slotMonitor.stage1sSendCrossReadResultMSB.ref := read(bore(p)))
  //       probes.stage1Probes.wCrossReadLSBProbe.map(p => slotMonitor.stage1wCrossReadLSB.ref := read(bore(p)))
  //       probes.stage1Probes.wCrossReadMSBProbe.map(p => slotMonitor.stage1wCrossReadMSB.ref := read(bore(p)))
  //
  //       slotMonitor.stage1VrfReadReadyRequest
  //         .zip(slotMonitor.stage1VrfReadValidRequest)
  //         .zipWithIndex.foreach{ case((ready, valid), index) =>
  //           val (readyProbe, validProbe) = probes.stage1Probes.vrfReadRequestProbe(index)
  //           ready.ref := read(bore(readyProbe))
  //           valid.ref := read(bore(validProbe))
  //         }
  //
  //       slotMonitor.executionUnitVfuRequestReady.ref := read(bore(probes.executionUnitVfuRequestReady))
  //       slotMonitor.executionUnitVfuRequestValid.ref := read(bore(probes.executionUnitVfuRequestValid))
  //
  //       slotMonitor.stage3VrfWriteReady.ref := read(bore(probes.stage3VrfWriteReady))
  //       slotMonitor.stage3VrfWriteValid.ref := read(bore(probes.stage3VrfWriteValid))
  //     } else {
  //       val slotMonitor = Module(new LaneSlotMonitor())
  //       slotMonitor.clock.ref := clock.asBool
  //       slotMonitor.laneIndex.ref := laneIndex.U
  //       slotMonitor.slotIndex.ref := slotIndex.U
  //
  //       slotMonitor.stage0EnqueueReady.ref := read(bore(probes.stage0EnqueueReady))
  //       slotMonitor.stage0EnqueueValid.ref := read(bore(probes.stage0EnqueueValid))
  //       slotMonitor.changingMaskSet.ref := read(bore(probes.changingMaskSet))
  //       slotMonitor.slotActive.ref := read(bore(probes.slotActive))
  //       slotMonitor.slotOccupied.ref := read(bore(probes.slotOccupied))
  //       slotMonitor.pipeFinish.ref := read(bore(probes.pipeFinish))
  //
  //       slotMonitor.stage1DequeueReady.ref := read(bore(probes.stage1Probes.dequeueReadyProbe))
  //       slotMonitor.stage1DequeueValid.ref := read(bore(probes.stage1Probes.dequeueValidProbe))
  //       slotMonitor.stage1HasDataOccupied.ref := read(bore(probes.stage1Probes.hasDataOccupiedProbe))
  //       slotMonitor.stage1Finishing.ref := read(bore(probes.stage1Probes.stageFinishProbe))
  //
  //       slotMonitor.stage1VrfReadReadyRequest
  //         .zip(slotMonitor.stage1VrfReadValidRequest)
  //         .zipWithIndex.foreach{ case((ready, valid), index) =>
  //           val (readyProbe, validProbe) = probes.stage1Probes.vrfReadRequestProbe(index)
  //           ready.ref := read(bore(readyProbe))
  //           valid.ref := read(bore(validProbe))
  //         }
  //
  //       slotMonitor.executionUnitVfuRequestReady.ref := read(bore(probes.executionUnitVfuRequestReady))
  //       slotMonitor.executionUnitVfuRequestValid.ref := read(bore(probes.executionUnitVfuRequestValid))
  //
  //       slotMonitor.stage3VrfWriteReady.ref := read(bore(probes.stage3VrfWriteReady))
  //       slotMonitor.stage3VrfWriteValid.ref := read(bore(probes.stage3VrfWriteValid))
  //     }
  //   })
  // })
 }
