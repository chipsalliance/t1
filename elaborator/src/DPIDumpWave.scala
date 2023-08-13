package tests.elaborate

class DPIDumpWave extends DPIModule {
  override val dpiModuleParameter: DPIModuleParameter = DPIModuleParameter(
    isImport = false,
    dpiName = "dpiDumpWave"
  )
  override val body: String =
    s"""function dpiDumpWave(input string file);
       | $$dumpfile(file);
       | $$dumpvars(0);
       |endfunction;
       |""".stripMargin
}
