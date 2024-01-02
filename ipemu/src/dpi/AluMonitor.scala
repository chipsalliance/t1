package elaborate.dpi

import chisel3._

case class AluMonitorParameter(triggerDelay: Int)

class AluMonitor(p: AluMonitorParameter) extends DPIModule {
  val isImport: Boolean = true

  val clock = dpiTrigger("clock", Input(Bool()))

  val laneIdx = dpiIn("laneIdx", Input(UInt(32.W)))
  val isAdderOccupied = dpiIn("isAdderOccupied", Input(Bool()))
  val isShifterOccupied = dpiIn("isShifterOccupied", Input(Bool()))
  val isMultiplierOccupied = dpiIn("isMultiplierOccupied", Input(Bool()))
  val isDividerOccupied = dpiIn("isDividerOccupied", Input(Bool()))

  override val trigger = s"always @(posedge ${clock.name}) #(${p.triggerDelay})"
}
