package tests.elaborate

class DPIError extends DPIModule {
  override val dpiModuleParameter: DPIModuleParameter = DPIModuleParameter(
    isImport = false,
    dpiName = "dpiError"
  )
  override val body: String =
    s"""function dpiFinish();
       | $$finish;
       |endfunction;
       |""".stripMargin
}
