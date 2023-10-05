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
  * Monitor signals in [[v.LoadUnit]]
  */
class LoadUnitMonitor extends PerfMonitor {
  val tlPortAIsValid = dpiIn("LoadUnitTlPortAIsValid", Input(Bool()))
  val tlPortAIsReady = dpiIn("LoadUnitTlPortAIsReady", Input(Bool()))

  val statusIdle = dpiIn("LoadUnitStatusIdle", Input(Bool()))

  val writeReadyForLSU = dpiIn("LoadUnitWriteReadyForLSU", Input(Bool()))
}

class LoadUnitPortDMonitor extends IndexedPerfMonitor with ValidMonitor with ReadyMonitor

class LoadUnitVrfWritePortMonitor extends IndexedPerfMonitor with ValidMonitor with ReadyMonitor

class LoadUnitLastCacheLineAckMonitor extends IndexedPerfMonitor {
  val isAck = dpiIn("LoadUnitLastCacheLineIsAck", Input(Bool()))
}

class LoadUnitCacheLineDequeueMonitor extends IndexedPerfMonitor with ValidMonitor with ReadyMonitor
// End of LoadUnit monitor definition


/**
  * Monitor signals in [[v.SimpleAccessUnit]]
  */
class SimpleAccessUnitMonitor extends PerfMonitor {
  val lsuRequestIsValid = dpiIn("SimpleAccessUnitLSURequestIsValid", Input(Bool()))

  val vrfReadDataPortsIsReady = dpiIn("SimpleAccessUnitVRFReadDataPortsIsReady", Input(Bool()))
  val vrfReadDataPortsIsValid = dpiIn("SimpleAccessUnitVRFReadDataPortsIsValid", Input(Bool()))

  val maskSelectIsValid = dpiIn("SimpleAccessUnitMaskSelectIsValid", Input(Bool()))

  val vrfWritePortIsReady = dpiIn("SimpleAccessUnitVRFWritePortIsReady", Input(Bool()))
  val vrfWritePortIsValid = dpiIn("SimpleAccessUnitVRFWritePortIsValid", Input(Bool()))

  val currentLane = dpiIn("SimpleAccessUnitStatusTargetLane", Input(UInt(32.W)))
  val statusIsWaitingFirstResponse = dpiIn("SimpleAccessUnitStatusIsWaitingFirstResponse", Input(Bool()))

  val s0Fire = dpiIn("SimpleAccessUnitS0Fire", Input(Bool()))
  val s1Fire = dpiIn("SimpleAccessUnitS1Fire", Input(Bool()))
  val s2Fire = dpiIn("SimpleAccessUnitS2Fire", Input(Bool()))
}

class SimpleAccessUnitOffsetReadResultMonitor extends IndexedPerfMonitor with ValidMonitor

class SimpleAccessUnitIndexedInsnOffsetsIsValidMonitor extends IndexedPerfMonitor with ValidMonitor
// End of SimpleAccessUnit monitors definition


/**
  * Monitor signals in [[v.StoreUnit]]
  */
class StoreUnitMonitor extends PerfMonitor {
  val vrfReadyToStore = dpiIn("VrfReadyToStore", Input(Bool()))
}

class StoreUnitAlignedDequeueMonitor extends PerfMonitor with ValidMonitor with ReadyMonitor

class StoreUnitTlPortAMonitor extends IndexedPerfMonitor with ValidMonitor with ReadyMonitor

class StoreUnitVrfReadDataPortMonitor extends IndexedPerfMonitor with ValidMonitor with ReadyMonitor

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
  val crossLaneReadValid = dpiIn("crossLaneReadValid", Input(Bool()))
  val crossLaneWriteValid = dpiIn("crossLaneWriteValid", Input(Bool()))
}

class LaneReadBusDataMonitor extends IndexedPerfMonitor with ValidMonitor

class LaneWriteBusDataMonitor extends IndexedPerfMonitor with ValidMonitor
// End of Lane monitor

class VRequestMonitor extends PerfMonitor with ValidMonitor with ReadyMonitor

class VResponseMonitor extends PerfMonitor with ValidMonitor

class VRequestRegMonitor extends PerfMonitor with ValidMonitor

class VRequestRegDequeueMonitor extends PerfMonitor with ValidMonitor with ReadyMonitor

class VMaskUnitWriteValidMonitor extends PerfMonitor with ValidMonitor

class VMaskUnitWriteValidIndexedMonitor extends IndexedPerfMonitor with ValidMonitor

class VMaskUnitReadValidMonitor extends PerfMonitor with ValidMonitor

class VMaskUnitReadValidIndexedMonitor extends IndexedPerfMonitor with ValidMonitor

class VWarReadResultValidMonitor extends PerfMonitor with ValidMonitor

class VDataMonitor extends IndexedPerfMonitor with ValidMonitor

class VSelectffoIndexMonitor extends ValidMonitor

class VDataResultMonitor extends ValidMonitor

class VLaneReadyMonitor extends IndexedPerfMonitor with ReadyMonitor

class VSlotStatIdleMonitor extends IndexedPerfMonitor {
  val idle = dpiIn("idle", Input(Bool()))
}

class VVrfWriteMonitor extends IndexedPerfMonitor with ReadyMonitor with ValidMonitor

