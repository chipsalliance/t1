import chisel3._
import chisel3.util._
import chisel3.util.circt.ClockGate
import chisel3.experimental.BundleLiterals._
import hardfloat.MulAddRecFN

sealed trait MatrixElementType {
  def prec : (Int, Int)
  def gen : Bits = UInt((prec._1 + prec._2).W)
  def recgen : Bits = UInt((prec._1 + prec._2 + 1).W)
}

case object FP64 extends MatrixElementType {
  override def prec = (11, 53)
}
case object FP32 extends MatrixElementType {
  override def prec = (8, 24)
}
case object FP16 extends MatrixElementType {
  override def prec = (5, 11)
}

case class MatrixConfig(
  n: Int, // Height for the first matrix
  m: Int, // Width for the second matrix,
  elementType: MatrixElementType,
)

class InputSlice(et: MatrixElementType, dim: Int) extends Bundle {
  val els = Vec(dim, et.gen)
  val last = Bool()
}
class MatrixIO(cfg: MatrixConfig) extends Bundle {
  val aCol = Flipped(Decoupled(new InputSlice(cfg.elementType, cfg.n)))
  val bRow = Flipped(Decoupled(new InputSlice(cfg.elementType, cfg.m)))
  val cOut = Decoupled(new InputSlice(cfg.elementType, math.max(cfg.n, cfg.m)))

  // when cColMajor = 1, output in col major. otherwise, output in row major.
  val cColMajor = Input(Bool())
}

// Total delay from first input to first output is:
// 3 * n + k * fmaDepth
class Matrix(cfg: MatrixConfig) extends Module {
  val io = IO(new MatrixIO(cfg))

  val aBuf = Queue(io.aCol)
  val bBuf = Queue(io.bRow)

  val consume = aBuf.valid && bBuf.valid
  val draining = RegInit(false.B)
  val flushing = RegInit(false.B)

  when(consume && aBuf.bits.last) {
    draining := true.B
  }

  val compCnt = Counter(cfg.n + cfg.m - 1)
  when(draining) {
    when(compCnt.inc()) {
      flushing := true.B
      draining := false.B
    }
  }

  val peOut = Wire(io.cOut.cloneType)
  val flushCnt = UInt(log2Up(math.max(cfg.n, cfg.m)).W)
  val flushingStep = peOut.ready && flushing
  when(peOut.fire) {
    flushCnt := flushCnt + 1.U
    when(flushCnt === Mux(io.cColMajor, (cfg.m - 1).U, (cfg.n - 1).U)) {
      flushCnt := 0.U
      flushing := false.B
    }
  }

  withClock(ClockGate(clock, consume || draining || flushingStep)) {
    val pes = Seq.fill(cfg.n, cfg.m) { Module(new PE(cfg.elementType)) }

    for(i <- 0 until cfg.n) for(j <- 0 until cfg.m) {
      val cur = pes(i)(j)
      cur.flush := flushing
      cur.flushColMajor := io.cColMajor

      if (j == 0) {
        cur.links.left.i := Pipe(
          Valid(cfg.elementType.recgen).Lit(
            _.valid -> aBuf.valid,
            _.bits -> hardfloat.recFNFromFN(cfg.elementType.prec._1, cfg.elementType.prec._2, aBuf.bits.els(i))
          ),
          i,
        )
      } else {
        pes(i)(j - 1).links.right.o <> cur.links.left.i
        pes(i)(j - 1).links.right.i <> cur.links.left.o
      }

      if(i == 0) {
        cur.links.top.i := bBuf.map(_.els(j))
      } else {
        pes(i - 1)(j).links.bottom.o <> cur.links.top.i
        pes(i - 1)(j).links.bottom.i <> cur.links.top.o
      }

      if(i == cfg.n - 1) cur.links.bottom.i := DontCare
      if(j == cfg.m - 1) cur.links.right.i := DontCare
    }

    for((oel, oidx) <- peOut.bits.els.zipWithIndex) {
      if(oidx < cfg.n && oidx < cfg.m) oel := Mux(io.cColMajor, pes(oidx)(0).links.left.o.bits, pes(0)(oidx).links.top.o.bits)
      else if(oidx < cfg.n) oel := pes(oidx)(0).links.left.o.bits
      else oel := pes(0)(oidx).links.top.o.bits
    }
  }

  val cBuf = Queue(peOut)
  io.cOut <> cBuf
}

/**
 * PE Implementations
 */
private class Link(et: MatrixElementType) extends Bundle {
  // For output links, this valid is not used, and should be automatically optimized away
  val i = Flipped(Valid(et.recgen))
  val o = Valid(et.recgen)
}

private class Links(et: MatrixElementType) extends Bundle {
  val right = new Link(et)
  val bottom = new Link(et)

  val left = new Link(et)
  val top = new Link(et)
}

private class PE(et: MatrixElementType) extends Module {
  val links = IO(new Links(et))

  val flush = IO(Input(new Bool()))
  val flushColMajor = IO(Input(new Bool()))

  val a = RegNext(links.left.i)
  val b = RegNext(links.top.i)

  links.right.o := a
  links.bottom.o := b

  val cComp = Wire(et.recgen)
  val cNext = Mux(flush, Mux(flushColMajor, links.right.i.bits, links.bottom.i.bits), cComp)
  val c = RegNext(cNext)
  links.top.o.bits := c
  links.left.o.bits := c
  links.top.o.valid := DontCare
  links.left.o.valid := DontCare

  val fma = Module(new MulAddRecFN(et.prec._1, et.prec._2))
  fma.io.a := a.bits
  fma.io.b := b.bits
  fma.io.c := c
  fma.io.op := 0.U
  fma.io.roundingMode := hardfloat.consts.round_odd
  fma.io.detectTininess := hardfloat.consts.tininess_beforeRounding
  cComp := Mux(a.valid, fma.io.out, 0.U)
}