package tests.elaborate

case class DPITimeoutCheckParameter(clockRate: Int)

class DPITimeoutCheck(val parameter: DPITimeoutCheckParameter) extends DPIModule {
  override val dpiModuleParameter: DPIModuleParameter = DPIModuleParameter(
    isImport = true,
    dpiName = "dpiTimeoutCheck"
  )
  override val body: String =
    s"""always #(${2 * parameter.clockRate + 1}) dpiTimeoutCheck();""".stripMargin
}
