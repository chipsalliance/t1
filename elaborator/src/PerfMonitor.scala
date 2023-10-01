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
  val valid = dpiIn("valid", Input(Bool()))
}

trait ReadyMonitor extends PerfMonitor {
  val ready = dpiIn("ready", Input(Bool()))
}

trait IndexedValidMonitor extends ValidMonitor with IndexedPerfMonitor
trait IndexedReadyMonitor extends ReadyMonitor with IndexedPerfMonitor
trait ReadyAndValidMonitor extends ValidMonitor with ReadyMonitor

/**
  * Monitor signals in [[v.LoadUnit]]
  */
class LoadUnitMonitor extends DPIModule {
  override val isImport = true;

  val clock = dpiTrigger("clock", Input(Bool()))

  val tlPortAIsValid = dpiIn("LoadUnitTlPortAIsValid", Input(Bool()))
  val tlPortAIsReady = dpiIn("LoadUnitTlPortAIsReady", Input(Bool()))

  val statusIdle = dpiIn("LoadUnitStatusIdle", Input(Bool()))
  val statusLast = dpiIn("LoadUnitStatusLast", Input(Bool()))

  val writeReadyForLSU = dpiIn("LoadUnitWriteReadyForLSU", Input(Bool()))

  override val trigger: String = s"always @(posedge ${clock.name})";
}

/**
  * Monitor signals in tilelink port D in [[v.LoadUnit]]
  */
class LoadUnitPortDMonitor extends DPIModule {
  override val isImport = true;

  val clock = dpiTrigger("clock", Input(Bool()))

  val portDIndex = dpiIn("VRFPortDIndex", Input(UInt(32.W)))
  val portDIsValid = dpiIn("VRFPortDIsValid", Input(Bool()))
  val portDIsReady = dpiIn("VRFPortDIsReady", Input(Bool()))

  override val trigger: String = s"always @(posedge ${clock.name})";
}

/**
  * Monitor signals in VRF write port in [[v.LoadUnit]]
  */
class LoadUnitVrfWritePortMonitor extends DPIModule {
  override val isImport = true;

  val clock = dpiTrigger("clock", Input(Bool()))

  val index = dpiIn("LoadUnitVrfWritePortIndex", Input(UInt(32.W)))
  val isReady = dpiIn("LoadUnitVrfWritePortIsReady", Input(Bool()))
  val isValid = dpiIn("LoadUnitVrfWritePortIsValid", Input(Bool()))

  override val trigger: String = s"always @(posedge ${clock.name})";
}

/**
  * Monitor lastCacheLineAck status [[v.LoadUnit]]
  */
class LoadUnitLastCacheLineAckMonitor extends DPIModule {
  override val isImport = true;

  val clock = dpiTrigger("clock", Input(Bool()))

  val index = dpiIn("LoadUnitLastCacheLineAckIndex", Input(UInt(32.W)))
  val isAck = dpiIn("LoadUnitLastCacheLineIsAck", Input(Bool()))

  override val trigger: String = s"always @(posedge ${clock.name})";
}

class LoadUnitCacheLineDequeueMonitor extends DPIModule {
  override val isImport = true;

  val clock = dpiTrigger("clock", Input(Bool()))

  val index = dpiIn("LoadUnitCacheLineDequeueIndex", Input(UInt(32.W)))
  val isValid = dpiIn("LoadUnitCacheLineDequeueIsValid", Input(Bool()))
  val isReady = dpiIn("LoadUnitCacheLineDequeueIsReady", Input(Bool()))

  override val trigger: String = s"always @(posedge ${clock.name})";
}

class SimpleAccessUnitMonitor extends DPIModule {
  override val isImport = true;

  val clock = dpiTrigger("clock", Input(Bool()))

  val lsuRequestIsValid = dpiIn("SimpleAccessUnitLSURequestIsValid", Input(Bool()))

  val vrfReadDataPortsIsReady = dpiIn("SimpleAccessUnitVRFReadDataPortsIsReady", Input(Bool()))
  val vrfReadDataPortsIsValid = dpiIn("SimpleAccessUnitVRFReadDataPortsIsValid", Input(Bool()))

  val maskSelectIsValid = dpiIn("SimpleAccessUnitMaskSelectIsValid", Input(Bool()))

  val vrfWritePortIsReady = dpiIn("SimpleAccessUnitVRFWritePortIsReady", Input(Bool()))
  val vrfWritePortIsValid = dpiIn("SimpleAccessUnitVRFWritePortIsValid", Input(Bool()))

  val currentLane = dpiIn("SimpleAccessUnitStatusTargetLane", Input(UInt(32.W)))
  val statusIsOffsetGroupEnd = dpiIn("SimpleAccessUnitStatusIsOffsetGroupEnd", Input(Bool()))
  val statusIsWaitingFirstResponse = dpiIn("SimpleAccessUnitStatusIsWaitingFirstResponse", Input(Bool()))

  val s0Fire = dpiIn("SimpleAccessUnitS0Fire", Input(Bool()))
  val s1Fire = dpiIn("SimpleAccessUnitS1Fire", Input(Bool()))
  val s2Fire = dpiIn("SimpleAccessUnitS2Fire", Input(Bool()))

  override val trigger: String = s"always @(posedge ${clock.name})";
}

class SimpleAccessUnitOffsetReadResultMonitor extends DPIModule {
  override val isImport = true;

  val clock = dpiTrigger("clock", Input(Bool()))

  val index = dpiIn("SimpleAccessUnitOffSetReadResultIndex", Input(UInt(32.W)))
  val offsetReadResultIsValid = dpiIn("SimpleAccessUnitOffsetReadResultIsValid", Input(Bool()))

  override val trigger: String = s"always @(posedge ${clock.name})";
}

class SimpleAccessUnitIndexedInsnOffsetsIsValidMonitor extends DPIModule {
  override val isImport = true;
  val clock = dpiTrigger("clock", Input(Bool()))

  val index = dpiIn("SimpleAccessUnitIndexedInsnOffsetsIndex", Input(UInt(32.W)))
  val isValid = dpiIn("SimpleAccessUnitIndexedInsnOffsetsIsValid", Input(Bool()))

  override val trigger: String = s"always @(posedge ${clock.name})";
}

class StoreUnitMonitor extends DPIModule {
  override val isImport: Boolean = true;
  val clock = dpiTrigger("clock", Input(Bool()))
  override val trigger: String = s"always @(posedge ${clock.name})";

  val vrfReadyToStore = dpiIn("VrfReadyToStore", Input(Bool()))
  val alignedDequeueValid = dpiIn("AlignedDequeueValid", Input(Bool()))
  val alignedDequeueReady = dpiIn("AlignedDequeueReady", Input(Bool()))
}

// Monitor tlPortA in [[v.StoreUnit]]
class StoreUnitTlPortAValidMonitor extends DPIModule {
  override val isImport: Boolean = true;
  val clock = dpiTrigger("clock", Input(Bool()))
  override val trigger: String = s"always @(posedge ${clock.name})";

  val index = dpiIn("index", Input(UInt(32.W)))
  val valid = dpiIn("valid", Input(Bool()))
}

class StoreUnitTlPortAReadyMonitor extends DPIModule {
  override val isImport: Boolean = true;
  val clock = dpiTrigger("clock", Input(Bool()))
  override val trigger: String = s"always @(posedge ${clock.name})";

  val index = dpiIn("index", Input(UInt(32.W)))
  val ready = dpiIn("ready", Input(Bool()))
}

class StoreUnitVrfReadDataPortValidMonitor extends DPIModule {
  override val isImport: Boolean = true;
  val clock = dpiTrigger("clock", Input(Bool()))
  override val trigger: String = s"always @(posedge ${clock.name})";

  val index = dpiIn("index", Input(UInt(32.W)))
  val valid = dpiIn("valid", Input(Bool()))
}

class StoreUnitVrfReadDataPortReadyMonitor extends DPIModule {
  override val isImport: Boolean = true;
  val clock = dpiTrigger("clock", Input(Bool()))
  override val trigger: String = s"always @(posedge ${clock.name})";

  val index = dpiIn("index", Input(UInt(32.W)))
  val ready = dpiIn("ready", Input(Bool()))
}

abstract class LaneMonitor extends PerfMonitor {
  val laneIndex = dpiIn("laneIndex", Input(UInt(32.W)))
}

class LaneReadBusPortMonitor extends LaneMonitor {
  val readBusPortEnqReady = dpiIn("readBusPortEnqReady", Input(Bool()))
  val readBusPortEnqValid = dpiIn("readBusPortEnqValid", Input(Bool()))
  val readBusPortDeqReady = dpiIn("readBusPortDeqReady", Input(Bool()))
  val readBusPortDeqValid = dpiIn("readBusPortDeqValid", Input(Bool()))
}

class LaneWriteBusPortMonitor extends LaneMonitor {
  val writeBusPortEnqReady = dpiIn("writeBusPortEnqReady", Input(Bool()))
  val writeBusPortEnqValid = dpiIn("writeBusPortEnqValid", Input(Bool()))
  val writeBusPortDeqReady = dpiIn("writeBusPortDeqReady", Input(Bool()))
  val writeBusPortDeqValid = dpiIn("writeBusPortDeqValid", Input(Bool()))
}

class LaneRequestMonitor extends LaneMonitor {
  val laneRequestValid = dpiIn("laneRequestValid", Input(Bool()))
  val laneRequestReady = dpiIn("laneRequestReady", Input(Bool()))
}

class LaneResponseMonitor extends LaneMonitor {
  val laneResponseValid = dpiIn("laneResponseValid", Input(Bool()))
  val laneResponseFeedbackValid = dpiIn("laneResponseFeedbackValid", Input(Bool()))
}

class LaneVrfReadMonitor extends LaneMonitor {
  val vrfReadAddressChannelValid = dpiIn("vrfReadAddressChannelValid", Input(Bool()))
  val vrfReadAddressChannelReady = dpiIn("vrfReadAddressChannelReady", Input(Bool()))
}

class LaneVrfWriteMonitor extends LaneMonitor {
  val vrfWriteChannelValid = dpiIn("vrfWriteChannelValid", Input(Bool()))
  val vrfWriteChannelReady = dpiIn("vrfWriteChannelReady", Input(Bool()))
}

class LaneStatusMonitor extends LaneMonitor {
  val v0UpdateValid = dpiIn("v0UpdateValid", Input(Bool()))
  val writeReadyForLsu = dpiIn("writeReadyForLsu", Input(Bool()))
  val vrfReadyToStore = dpiIn("vrfReadyToStore", Input(Bool()))
}

class LaneWriteQueueMonitor extends LaneMonitor {
  val writeQueueValid = dpiIn("writeQueueValid", Input(Bool()))
}

class LaneReadBusDequeueMonitor extends LaneMonitor {
  val readBusDequeueValid = dpiIn("readBusDequeueValid", Input(Bool()))
}

class CrossLaneMonitor extends LaneMonitor {
  val crossLaneReadValid = dpiIn("crossLaneReadValid", Input(Bool()))
  val crossLaneWriteValid = dpiIn("crossLaneWriteValid", Input(Bool()))
}

class LaneReadBusDataMonitor extends LaneMonitor {
  val readBusDataReqValid = dpiIn("readBusDataReqValid", Input(Bool()))
}

class LaneWriteBusDataMonitor extends LaneMonitor {
  val writeBusDataReqValid = dpiIn("writeBusDataReqValid", Input(Bool()))
}

class VRequestMonitor extends PerfMonitor {
  val valid = dpiIn("VRequestValid", Input(Bool()))
  val ready = dpiIn("VRequestReady", Input(Bool()))
}

class VResponseMonitor extends PerfMonitor {
  val valid = dpiIn("VResponseValid", Input(Bool()))
}

class VRequestRegMonitor extends PerfMonitor {
  val valid = dpiIn("VRequestRegValid", Input(Bool()))
}

class VRequestRegDequeueMonitor extends PerfMonitor {
  val valid = dpiIn("VRequestRegDequeueValid", Input(Bool()))
  val ready = dpiIn("VRequestRegDequeueReady", Input(Bool()))
}

class VMaskUnitWriteValidMonitor extends PerfMonitor {
  val valid = dpiIn("VMaskedUnitWriteValid", Input(Bool()))
}

class VMaskUnitWriteValidIndexedMonitor extends PerfMonitor {
  val index = dpiIn("index", Input(UInt(32.W)))
  val valid = dpiIn("valid", Input(Bool()))
}

class VMaskUnitReadValidMonitor extends PerfMonitor {
  val valid = dpiIn("valid", Input(Bool()))
}

class VMaskUnitReadValidIndexedMonitor extends PerfMonitor {
  val index = dpiIn("index", Input(UInt(32.W)))
  val valid = dpiIn("valid", Input(Bool()))
}

class VWarReadResultValidMonitor extends PerfMonitor {
  val valid = dpiIn("valid", Input(Bool()))
}

class VDataMonitor extends IndexedValidMonitor

class VSelectffoIndexMonitor extends ValidMonitor

class VDataResultMonitor extends ValidMonitor

class VLaneReadyMonitor extends IndexedPerfMonitor with ReadyMonitor

class VSlotStatIdleMonitor extends IndexedPerfMonitor {
  val idle = dpiIn("idle", Input(Bool()))
}

class VVrfWriteMonitor extends IndexedPerfMonitor with ReadyMonitor with ValidMonitor

