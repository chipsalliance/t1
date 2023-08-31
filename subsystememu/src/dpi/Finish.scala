package verdes.dpi

import chisel3._

class Finish extends DPIModule {
  val isImport: Boolean = false

  // TODO: think about `chisel3.properties.Property`?
  override val exportBody = s"""
                               |function $desiredName();
                               |   $$finish;
                               |endfunction;
                               |""".stripMargin
}
