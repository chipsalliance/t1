package crypto.modmul

import chisel3._
import chisel3.util.experimental.decode.TruthTable
import chisel3.util.{BitPat, Counter, Mux1H}

class Barrett(val p: BigInt, val mulPipe: Int, val addPipe: Int) extends ModMul {
  val m = ((BigInt(1) << (2 * width)) / p).toInt
  val mul = Module(new DummyMul(width + 1, mulPipe))
  val add = Module(new DummyAdd(width * 2, addPipe))
  val q = RegInit(0.U((width * 2 + 1).W))
  val r = RegInit(0.U((width * 2 + 1).W))
  val r_stable = RegInit(0.U((width * 2).W))

  // Control Path
  object StateType extends ChiselEnum {
    val s0 = Value("b00000001".U)
    val s1 = Value("b00000010".U)
    val s2 = Value("b00000100".U)
    val s3 = Value("b00001000".U)
    val s4 = Value("b00010000".U)
    val s5 = Value("b00100000".U)
    val s6 = Value("b01000000".U)
    val s7 = Value("b10000000".U)
  }
  val state = RegInit(StateType.s0)
  val isMul = (state.asUInt & "b00001110".U).orR
  val isAdd = (state.asUInt & "b01110000".U).orR
  val mulDoneNext = RegInit(false.B)
  mulDoneNext := mulDone
  // var mulDoneNextNext = RegInit(false.B)
  // mulDoneNextNext := mulDoneNext
  val addDoneNext = RegInit(false.B)
  addDoneNext := addDone
  lazy val addDone = if (addPipe != 0) Counter(isAdd && (~addDoneNext), addPipe + 1)._2 else true.B
  lazy val mulDone = if (mulPipe != 0) Counter(isMul && (~mulDoneNext), mulPipe + 1)._2 else true.B
  val addSign = (r_stable < p.asUInt)
  val decodeIn = WireDefault(state.asUInt ## mulDoneNext ## addDoneNext ## addSign ## input.valid ## z.ready)
  state := chisel3.util.experimental.decode.decoder
    .qmc(
      decodeIn, {
        val Y = "1"
        val N = "0"
        val DC = "?"
        def to(
          stateI:     String,
          mulDone:    String = DC,
          addDone:    String = DC,
          addSign:    String = DC,
          inputValid: String = DC,
          zReady:     String = DC
        )(stateO:     String
        ) = BitPat(s"b$stateI$mulDone$addDone$addSign$inputValid$zReady") -> BitPat(s"b$stateO")
        val s0 = "00000001"
        val s1 = "00000010"
        val s2 = "00000100"
        val s3 = "00001000"
        val s4 = "00010000"
        val s5 = "00100000"
        val s6 = "01000000"
        val s7 = "10000000"
        TruthTable(
          Seq(
            to(s0, inputValid = N)(s0),
            to(s0, inputValid = Y)(s1),
            to(s1, mulDone = Y)(s2),
            to(s1, mulDone = N)(s1),
            to(s2, mulDone = Y)(s3),
            to(s2, mulDone = N)(s2),
            to(s3, mulDone = Y)(s4),
            to(s3, mulDone = N)(s3),
            to(s4, addDone = Y, addSign = N)(s5),
            to(s4, addDone = Y, addSign = Y)(s7),
            to(s4, addDone = N)(s4),
            to(s5, addDone = Y, addSign = N)(s6),
            to(s5, addDone = Y, addSign = Y)(s7),
            to(s5, addDone = N)(s5),
            to(s6, addDone = Y)(s7),
            to(s6, addDone = N)(s6)
          ),
          BitPat.dontCare(state.getWidth)
        )
      }
    )
    .asTypeOf(StateType.Type())
  val debounceMul = Mux(mulDone, mul.z, 0.U)
  val debounceAdd = Mux(addDone, add.z, 0.U)

  // Data Path
  when(mulDone)(q := debounceMul)
  when(addDone)(r_stable := debounceAdd)

  r := Mux1H(
    Map(
      // x * y -> z; x * y -> r
      // state 1
      state.asUInt(1) -> q,
      (state.asUInt & "b00001110".U).orR -> r,
      // z - q3 * p; r - p
      // state 4, 5, 6
      isAdd -> r_stable
    )
  )

  add.valid := isAdd
  add.a := r
  // TODO: CSA.
  add.b := Mux(state.asUInt(4), -q, (-p).S((2 * width + 1).W).asUInt)

  mul.valid := isMul & (~mulDoneNext)
  mul.a := Mux1H(
    Map(
      state.asUInt(1) -> input.bits.a,
      state.asUInt(2) -> (q >> (width - 1)), // z >> (k-1)
      state.asUInt(3) -> (q >> (width + 1)) // q2 >> (k+1)
    )
  )
  mul.b := Mux1H(
    Map(
      state.asUInt(1) -> input.bits.b,
      state.asUInt(2) -> m.U,
      state.asUInt(3) -> p.U
    )
  )

  input.ready := state.asUInt(0)
  z.bits := r
  z.valid := state.asUInt(7)
}

// before Wallace Mul implemented, we use DummyMul as mul.
class DummyMul(width: Int, pipe: Int) extends Module {
  val valid = IO(Input(Bool()))
  val a = IO(Input(UInt(width.W)))
  val b = IO(Input(UInt(width.W)))
  val z = IO(Output(UInt((2 * width).W)))
  val rs = Seq.fill(pipe + 1) { Wire(chiselTypeOf(z)) }
  rs.zipWithIndex.foreach {
    case (r, i) =>
      if (i == 0) r := Mux(valid, a * b, 0.U) else r := Mux(valid, RegNext(rs(i - 1)), 0.U)
  }
  z := rs.last
}

class DummyAdd(width: Int, pipe: Int) extends Module {
  val valid = IO(Input(Bool()))
  val a = IO(Input(UInt(width.W)))
  val b = IO(Input(UInt(width.W)))
  val z = IO(Output(UInt(width.W)))
  val rs = Seq.fill(pipe + 1) { Wire(chiselTypeOf(z)) }
  rs.zipWithIndex.foreach {
    case (r, i) =>
      if (i == 0) r := Mux(valid, a + b, 0.U) else r := Mux(valid, RegNext(rs(i - 1)), 0.U)
  }
  z := rs.last
}
