package elaborate.dpi

import chisel3._

class DpiInitCosim extends DPIModule {
  val isImport: Boolean = true
  override val trigger: String = s"initial"
}
