import chisel3._
import chisel3.util._

sealed trait MatrixElementType {
  def gen : Data
}
case object FP32 extends MatrixElementType {
  override def gen: UInt = UInt(32.W)
}
case object FP16 extends MatrixElementType {
  override def gen: UInt = UInt(16.W)
}

case class MatrixConfig(
  n: Int, // Height for the first matrix
  m: Int, // Width for the second matrix,
  elementType: MatrixElementType,
  fmaDepth: Int,
)

class InputSlice(et: MatrixElementType, dim: Int) extends Bundle {
  val els = Vec(dim, et.gen)
  val last = Bool()
}
class MatrixIO(cfg: MatrixConfig) extends Bundle {
  val aCol = Flipped(Decoupled(new InputSlice(cfg.elementType, cfg.n)))
  val bRow = Flipped(Decoupled(new InputSlice(cfg.elementType, cfg.n)))
  val cOut = Decoupled(new InputSlice(cfg.elementType, math.max(cfg.n, cfg.m)))

  // when cColMajor = 1, output in col major. otherwise, output in row major.
  val cColMajor = Input(Bool())
}


// Total delay from first input to first output is:
// 3 * n + k * fmaDepth
class Matrix(cfg: MatrixConfig) extends Module {
  val io = IO(new MatrixIO(cfg))

  io.aCol.nodeq()
  io.bRow.nodeq()
  io.cOut.noenq()
}