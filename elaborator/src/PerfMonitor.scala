package tests.elaborate

import chisel3._

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

