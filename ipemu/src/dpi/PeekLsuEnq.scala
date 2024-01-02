package elaborate.dpi

import chisel3._

case class PeekLsuEnqParameter(mshrSize: Int, triggerDelay: Int)

class PeekLsuEnq(p: PeekLsuEnqParameter) extends DPIModule {
  val isImport: Boolean = true
  val clock = dpiTrigger("clock", Input(Bool()))
  val enq = dpiIn("enq", Input(UInt(p.mshrSize.W)))

  override val trigger = s"always @(posedge ${clock.name}) #(${p.triggerDelay})"
}
