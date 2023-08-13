package tests.elaborate

class DPIFinish extends DPIModule {
  override val dpiModuleParameter: DPIModuleParameter = DPIModuleParameter(
    isImport = false,
    dpiName = "dpiFinish"
  )
  override val body: String =
    s"""function dpiError(input string what);
       | $$error(what);
       |endfunction;
       |""".stripMargin
}
