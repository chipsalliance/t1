package elaborate.dpi

import chisel3._

case class TimeoutCheckParameter(clockRate: Int)

class TimeoutCheck(p: TimeoutCheckParameter) extends DPIModule{
  val isImport: Boolean = true
  override val trigger = s"always #(${2 * p.clockRate + 1})"
}
