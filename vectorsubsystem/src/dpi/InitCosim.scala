package verdes.dpi

import chisel3._

class InitCosim extends DPIModule {
  val isImport: Boolean = true
  override val trigger: String = s"initial"
}
