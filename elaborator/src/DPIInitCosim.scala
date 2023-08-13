package tests.elaborate

class DPIInitCosim extends DPIModule {
  override val dpiModuleParameter: DPIModuleParameter = DPIModuleParameter(
    isImport = true,
    dpiName = "dpiInitCosim"
  )
  override val body: String =
    """initial dpiInitCosim();""".stripMargin
}
