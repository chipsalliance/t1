package elaborate.dpi

import chisel3._

class LoadUnitMonitor extends DPIModule {
  override val isImport = true;

  val clock = dpiTrigger("clock", Input(Bool()))

  val statusIdle = dpiIn("LoadUnitStatusIdle", Input(Bool()))
  val writeReadyForLSU = dpiIn("LoadUnitWriteReadyForLSU", Input(Bool()))

  override val trigger: String = s"always @(posedge ${clock.name})";
}
