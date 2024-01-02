package elaborate.dpi

import chisel3._

case class PeekIssueParameter(instIdxBits: Int, triggerDelay: Int)

class PeekIssue(p: PeekIssueParameter) extends DPIModule {
  val isImport: Boolean = true
  val clock = dpiTrigger("clock", Input(Bool()))
  val ready = dpiIn("ready", Input(Bool()))
  val issueIdx = dpiIn("issueIdx", Input(UInt(p.instIdxBits.W)))

  override val trigger = s"always @(posedge ${clock.name}) #(${p.triggerDelay})"
}
