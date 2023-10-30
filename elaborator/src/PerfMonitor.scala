package tests.elaborate

import chisel3._

abstract class PerfMonitor extends DPIModule {
  override val isImport = true;

  val clock = dpiTrigger("clock", Input(Bool()))
  override val trigger: String = s"always @(posedge ${clock.name})";
}

trait IndexedPerfMonitor extends PerfMonitor {
  val index = dpiIn("index", Input(UInt(32.W)))
}

trait ValidMonitor extends PerfMonitor {
  val isValid = dpiIn("isValid", Input(Bool()))
}

trait ReadyMonitor extends PerfMonitor {
  val isReady = dpiIn("isReady", Input(Bool()))
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
  val lsuRequestIsValid = dpiIn("SimpleAccessUnitLSURequestIsValid", Input(Bool()))

  val vrfReadDataPortsIsReady = dpiIn("SimpleAccessUnitVRFReadDataPortsIsReady", Input(Bool()))
  val vrfReadDataPortsIsValid = dpiIn("SimpleAccessUnitVRFReadDataPortsIsValid", Input(Bool()))

  val maskSelectIsValid = dpiIn("SimpleAccessUnitMaskSelectIsValid", Input(Bool()))

  val vrfWritePortIsReady = dpiIn("SimpleAccessUnitVRFWritePortIsReady", Input(Bool()))
  val vrfWritePortIsValid = dpiIn("SimpleAccessUnitVRFWritePortIsValid", Input(Bool()))

  val targetLane = dpiIn("SimpleAccessUnitStatusTargetLane", Input(UInt(32.W)))
  val idle = dpiIn("SimpleAccessUnitIsIdle", Input(Bool()))

  val s0Fire = dpiIn("SimpleAccessUnitS0Fire", Input(Bool()))
  val s1Fire = dpiIn("SimpleAccessUnitS1Fire", Input(Bool()))
  val s2Fire = dpiIn("SimpleAccessUnitS2Fire", Input(Bool()))
}

class OtherUnitAccessTileLinkMonitor extends ValidMonitor with ReadyMonitor

class OtherUnitTileLinkAckMonitor extends ValidMonitor with ReadyMonitor

class OtherUnitOffsetReadResultMonitor extends IndexedPerfMonitor with ValidMonitor

class OtherUnitIndexedInsnOffsetsIsValidMonitor extends IndexedPerfMonitor with ValidMonitor
// End of SimpleAccessUnit monitors definition

class LaneReadBusPortMonitor extends IndexedPerfMonitor {
  val readBusPortEnqReady = dpiIn("readBusPortEnqReady", Input(Bool()))
  val readBusPortEnqValid = dpiIn("readBusPortEnqValid", Input(Bool()))
  val readBusPortDeqReady = dpiIn("readBusPortDeqReady", Input(Bool()))
  val readBusPortDeqValid = dpiIn("readBusPortDeqValid", Input(Bool()))
}

class LaneWriteBusPortMonitor extends IndexedPerfMonitor {
  val writeBusPortEnqReady = dpiIn("writeBusPortEnqReady", Input(Bool()))
  val writeBusPortEnqValid = dpiIn("writeBusPortEnqValid", Input(Bool()))
  val writeBusPortDeqReady = dpiIn("writeBusPortDeqReady", Input(Bool()))
  val writeBusPortDeqValid = dpiIn("writeBusPortDeqValid", Input(Bool()))
}

class LaneRequestMonitor extends IndexedPerfMonitor with ValidMonitor with ReadyMonitor

class LaneResponseMonitor extends IndexedPerfMonitor with ValidMonitor {
  val laneResponseFeedbackValid = dpiIn("laneResponseFeedbackValid", Input(Bool()))
}

class LaneVrfReadMonitor extends IndexedPerfMonitor with ValidMonitor with ReadyMonitor

class LaneVrfWriteMonitor extends IndexedPerfMonitor with ValidMonitor with ReadyMonitor

class LaneStatusMonitor extends IndexedPerfMonitor {
  val v0UpdateValid = dpiIn("v0UpdateValid", Input(Bool()))
  val writeReadyForLsu = dpiIn("writeReadyForLsu", Input(Bool()))
  val vrfReadyToStore = dpiIn("vrfReadyToStore", Input(Bool()))
}

class LaneWriteQueueMonitor extends IndexedPerfMonitor with ValidMonitor

class LaneReadBusDequeueMonitor extends IndexedPerfMonitor with ValidMonitor

class CrossLaneMonitor extends IndexedPerfMonitor {
  val readValid = dpiIn("crossLaneReadValid", Input(Bool()))
  val writeValid = dpiIn("crossLaneWriteValid", Input(Bool()))
}

class LaneReadBusDataMonitor extends IndexedPerfMonitor with ValidMonitor

class LaneWriteBusDataMonitor extends IndexedPerfMonitor with ValidMonitor
// End of Lane monitor
