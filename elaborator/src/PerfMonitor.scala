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
