// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu

import chisel3._

abstract class PerfMonitor extends DPIModuleLegacy {
  override val isImport = true;

  val clock = dpiTrigger("clock", Input(Bool()))
  override val trigger: String = s"always @(posedge ${clock.name})";
}

/**
  * Monitor signals in [[v.LoadUnit]] [[v.StoreUnit]]
  */
case class LSUParam(memoryBankSize: Int, laneNumber: Int)

class LoadUnitMonitor(param: LSUParam) extends PerfMonitor {
  val lsuRequestValid = dpiIn("LSURequestValid", Input(Bool()))

  val statusIdle = dpiIn("idle", Input(Bool()))

  val tlPortAIsValid = dpiIn("tlPortAIsValid", Input(Bool()))
  val tlPortAIsReady = dpiIn("tlPortAIsReady", Input(Bool()))

  val addressConflict = dpiIn("addressConflict", Input(Bool()))

  val tlPortDIsValid = dpiIn("tlPortDIsValid", Seq.fill(param.memoryBankSize)(Input(Bool())))
  val tlPortDIsReady = dpiIn("tlPortDIsReady", Seq.fill(param.memoryBankSize)(Input(Bool())))

  val queueValid = dpiIn("queueValid", Seq.fill(param.memoryBankSize)(Input(Bool())))
  val queueReady = dpiIn("queueReady", Seq.fill(param.memoryBankSize)(Input(Bool())))

  val cacheLineDequeueValid = dpiIn("cacheLineDequeueValid", Seq.fill(param.memoryBankSize)(Input(Bool())))
  val cacheLineDequeueReady = dpiIn("cacheLineDequeueReady", Seq.fill(param.memoryBankSize)(Input(Bool())))

  val unalignedCacheLine = dpiIn("unalignedCacheLine", Input(Bool()))

  val alignedDequeueReady = dpiIn("alignedDequeueReady", Input(Bool()))
  val alignedDequeueValid = dpiIn("alignedDequeueValid", Input(Bool()))

  val writeReadyForLSU = dpiIn("LoadUnitWriteReadyForLSU", Input(Bool()))

  val vrfWritePortValid = dpiIn("vrfWritePortValid", Seq.fill(param.laneNumber)(Input(Bool())))
  val vrfWritePortReady = dpiIn("vrfWritePortReady", Seq.fill(param.laneNumber)(Input(Bool())))
}

class StoreUnitMonitor(param: LSUParam) extends PerfMonitor {
  val idle = dpiIn("idle", Input(Bool()))
  val lsuRequestIsValid = dpiIn("lsuRequestIsValid", Input(Bool()))
  val tlPortAIsValid = dpiIn("tlPortAIsValid", Seq.fill(param.memoryBankSize)(Input(Bool())))
  val tlPortAIsReady = dpiIn("tlPortAIsReady", Seq.fill(param.memoryBankSize)(Input(Bool())))
  val addressConflict = dpiIn("addressConflict", Input(Bool()))
  val vrfReadDataPortIsValid = dpiIn("vrfReadDataPortIsValid", Seq.fill(param.laneNumber)(Input(Bool())))
  val vrfReadDataPortIsReady = dpiIn("vrfReadDataPortIsReady", Seq.fill(param.laneNumber)(Input(Bool())))
  val vrfReadyToStore = dpiIn("vrfReadyToStore", Input(Bool()))
  val alignedDequeueReady = dpiIn("alignedDequeueReady", Input(Bool()))
  val alignedDequeueValid = dpiIn("alignedDequeueValid", Input(Bool()))
}

case class VParam(chainingSize: Int)
class VMonitor(param: VParam) extends PerfMonitor {
  val requestValid = dpiIn("requestValid", Input(Bool()))
  val requestReady = dpiIn("requestReady", Input(Bool()))

  val requestRegValid = dpiIn("requestRegValid", Input(Bool()))

  val requestRegDequeueValid = dpiIn("requestRegDequeueValid", Input(Bool()))
  val requestRegDequeueReady = dpiIn("requestRegDequeueReady", Input(Bool()))
  val executionReady = dpiIn("executionReady", Input(Bool()))
  val slotReady = dpiIn("slotReady", Input(Bool()))
  val waitForGather = dpiIn("waitForGather", Input(Bool()))
  // Can't use 'RAW' here cuz it will be parsed as 'r_a_w' at DPI side
  val instructionRawReady = dpiIn("instructionRawReady", Input(Bool()))

  val responseValid = dpiIn("responseValid", Input(Bool()))
  val sMaskUnitExecuted = dpiIn("sMaskUnitExecuted", Seq.fill(param.chainingSize)(Input(Bool())))
  val wLast = dpiIn("wLast", Seq.fill(param.chainingSize)(Input(Bool())))
  val isLastInst = dpiIn("isLastInst", Seq.fill(param.chainingSize)(Input(Bool())))
}

/**
  * Monitor signals in [[v.SimpleAccessUnit]]
  */
class OtherUnitMonitor extends PerfMonitor {
  val lsuRequestIsValid = dpiIn("lsuRequestIsValid", Input(Bool()))

  val s0EnqueueValid = dpiIn("s0EnqueueValid", Input(Bool()))
  val stateIsRequest = dpiIn("stateIsRequest", Input(Bool()))
  val maskCheck = dpiIn("maskCheck", Input(Bool()))
  val indexCheck = dpiIn("indexCheck", Input(Bool()))
  val fofCheck = dpiIn("fofCheck", Input(Bool()))

  val s0Fire = dpiIn("s0Fire", Input(Bool()))
  val s1Fire = dpiIn("s1Fire", Input(Bool()))
  val s2Fire = dpiIn("s2Fire", Input(Bool()))

  val tlPortAIsReady = dpiIn("tlPortAIsReady", Input(Bool()))
  val tlPortAIsValid = dpiIn("tlPortAIsValid", Input(Bool()))
  val s1Valid = dpiIn("s1Valid", Input(Bool()))
  val sourceFree = dpiIn("sourceFree", Input(Bool()))

  val tlPortDIsValid = dpiIn("tlPortDIsValid", Input(Bool()))
  val tlPortDIsReady = dpiIn("tlPortDIsReady", Input(Bool()))

  // Can't use 'VRF' here cuz it will be parsed as 'v_r_f' at DPI side
  val vrfWritePortIsReady = dpiIn("VrfWritePortIsReady", Input(Bool()))
  val vrfWritePortIsValid = dpiIn("VrfWritePortIsValid", Input(Bool()))

  val stateValue = dpiIn("stateValue", Input(UInt(32.W)))
}
// End of SimpleAccessUnit monitors definition

case class LaneParam(slot: Int)
class LaneMonitor(param: LaneParam) extends PerfMonitor {
  val index = dpiIn("index", Input(UInt(32.W)))
  val laneRequestValid = dpiIn("laneRequestValid", Input(Bool()))
  val laneRequestReady = dpiIn("laneRequestReady", Input(Bool()))
  val lastSlotOccupied = dpiIn("lastSlotOccupied", Input(Bool()))
  val vrfInstructionWriteReportReady = dpiIn("vrfInstructionWriteReportReady", Input(Bool()))
  val slotOccupied = dpiIn("slotOccupied", Seq.fill(param.slot)(Input(Bool())))
  val instructionFinished = dpiIn("instructionFinished", Input(UInt(32.W)))
}

class LaneSlotMonitor extends PerfMonitor {
  val laneIndex = dpiIn("laneIndex", Input(UInt(32.W)))
  val slotIndex = dpiIn("slotIndex", Input(UInt(32.W)))

  val stage0EnqueueReady = dpiIn("stage0EnqueueReady", Input(Bool()))
  val stage0EnqueueValid = dpiIn("stage0EnqueueValid", Input(Bool()))

  val changingMaskSet = dpiIn("changingMaskSet", Input(Bool()))

  val slotActive = dpiIn("slotActive", Input(Bool()))
  val slotOccupied = dpiIn("slotOccupied", Input(Bool()))
  val pipeFinish = dpiIn("pipeFinish", Input(Bool()))

  val stage1DequeueReady = dpiIn("stage1DequeueReady", Input(Bool()))
  val stage1DequeueValid = dpiIn("stage1DequeueValid", Input(Bool()))

  val stage1HasDataOccupied = dpiIn("stage1HasDataOccpied", Input(Bool()))
  val stage1Finishing = dpiIn("stage1Finishing", Input(Bool()))

  val stage1VrfReadReadyRequest = dpiIn("stage1VrfReadReadyRequest", Seq.fill(3)(Input(Bool())))
  val stage1VrfReadValidRequest = dpiIn("stage1VrfReadValidRequest", Seq.fill(3)(Input(Bool())))

  val executionUnitVfuRequestReady = dpiIn("executionUnitVfuRequestReady", Input(Bool()))
  val executionUnitVfuRequestValid = dpiIn("executionUnitVfuRequestValid", Input(Bool()))

  val stage3VrfWriteReady = dpiIn("stage3VrfWriteReady", Input(Bool()))
  val stage3VrfWriteValid = dpiIn("stage3VrfWriteValid", Input(Bool()))
}

class LaneLastSlotMonitor() extends LaneSlotMonitor {
  val slotShiftValid = dpiIn("slotShiftValid", Input(Bool()))
  val decodeResultIsCrossReadOrWrite = dpiIn("decodeResultIsCrossReadOrWrite", Input(Bool()))
  val decodeResultIsScheduler = dpiIn("decodeResultIsScheduler", Input(Bool()))

  val stage1ReadFinish = dpiIn("stage1ReadFinish", Input(Bool()))

  val stage1sSendCrossReadResultLSB = dpiIn("sSendCrossReadResultLSB", Input(Bool()))
  val stage1sSendCrossReadResultMSB = dpiIn("sSendCrossReadResultMSB", Input(Bool()))
  val stage1wCrossReadLSB = dpiIn("wCrossReadLSB", Input(Bool()))
  val stage1wCrossReadMSB = dpiIn("wCrossReadMSB", Input(Bool()))
}
// End of Lane monitor
