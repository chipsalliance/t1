package elaborate.dpi

import chisel3._

class DpiDumpWave extends DPIModule {
  val isImport: Boolean = false

  // TODO: think about `chisel3.properties.Property`?
  override val exportBody = s"""
     |function $desiredName(input string file);
     |   $$dumpfile(file);
     |   $$dumpvars(0);
     |endfunction;
     |""".stripMargin
}
