import circt.stage.ChiselStage

object Main extends App {
  val svstr = ChiselStage.emitSystemVerilog(new Matrix(MatrixConfig(
    16,
    16,
    FP32,
    3,
  )), args)
  System.out.print(svstr)
}
