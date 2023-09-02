package elaborate.dpi

import chisel3._

case class ChainingMonitorParameter(slotNum: Int, triggerDelay: Int)

class ChainingMonitor(p: ChainingMonitorParameter) extends DPIModule {
  val isImport: Boolean = true

  val clock = dpiTrigger("clock", Input(Bool()))

  val laneIdx = dpiIn("laneIdx", Input(UInt(32.W)))
  val slotOccupied = dpiIn("slotOccupied", Input(UInt(p.slotNum.W)))

  override val trigger = s"always @(posedge ${clock.name}) #(${p.triggerDelay})"
}
