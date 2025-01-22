package crypto.modmul

import chisel3._
import chisel3.util.experimental.decode.TruthTable
import chisel3.util.{Counter, Mux1H}

class Montgomery(pWidth: Int = 4096, addPipe: Int) extends Module {
  val p = IO(Input(UInt(pWidth.W)))
  val pPrime = IO(Input(Bool()))
  val a = IO(Input(UInt(pWidth.W)))
  val b = IO(Input(UInt(pWidth.W)))
  val b_add_p = IO(Input(UInt((pWidth + 1).W))) // b + p
  val valid = IO(Input(Bool())) // input valid
  val out = IO(Output(UInt(pWidth.W)))
  val out_valid = IO(Output(Bool())) // output valid

  val u = Reg(Bool())
  val i = Reg(UInt((pWidth).W))
  val nextT = Reg(UInt((pWidth + 2).W))

  // multicycle prefixadder
  val adder = Module(new DummyAdd(pWidth + 2, addPipe))
  val add_stable = RegInit(0.U((pWidth + 2).W))
  // Control Path
  object StateType extends ChiselEnum {
    val s0 = Value("b0000001".U) // nextT = 0, u = a(0)b(0)pPrime
    // loop
    val s1 = Value("b0000010".U) // nextT + b
    val s2 = Value("b0000100".U) // nextT + p
    val s3 = Value("b0001000".U) // nextT + b_add_p
    // loop done
    val s4 = Value("b0010000".U) // i << 1, u = (nextT(0) + a(i)b(0))pPrime, nextT / 2
    val s5 = Value("b0100000".U) // if-then
    val s6 = Value("b1000000".U) // done
    val s7 = Value("b10000000".U) // nextT + 0
  }

  val state = RegInit(StateType.s0)
  val isAdd = (state.asUInt & "b10101110".U).orR
  adder.valid := isAdd
  val addDoneNext = RegInit(false.B)
  addDoneNext := addDone
  lazy val addDone = if (addPipe != 0) Counter(isAdd && (~addDoneNext), addPipe + 1)._2 else true.B
  val addSign = ((add_stable >> 1) < p.asUInt)
  val a_i = Reg(Bool())
  state := chisel3.util.experimental.decode
    .decoder(
      state.asUInt ## addDoneNext ## valid ## i.head(1) ## addSign ## u ## a_i, {
        val Y = "1"
        val N = "0"
        val DC = "?"
        def to(
          stateI:  String,
          addDone: String = DC,
          valid:   String = DC,
          iHead:   String = DC,
          addSign: String = DC,
          u:       String = DC,
          a_i:     String = DC
        )(stateO:  String
        ) = s"$stateI$addDone$valid$iHead$addSign$u$a_i->$stateO"
        val s0 = "00000001"
        val s1 = "00000010"
        val s2 = "00000100"
        val s3 = "00001000"
        val s4 = "00010000"
        val s5 = "00100000"
        val s6 = "01000000"
        val s7 = "10000000"
        TruthTable.fromString(
          Seq(
            to(s0, valid = N)(s0),
            to(s0, valid = Y, a_i = Y, u = N)(s1),
            to(s0, valid = Y, a_i = N, u = Y)(s2),
            to(s0, valid = Y, a_i = Y, u = Y)(s3),
            to(s0, valid = Y, a_i = N, u = N)(s7),
            to(s1, addDone = Y)(s4),
            to(s1, addDone = N)(s1),
            to(s2, addDone = Y)(s4),
            to(s2, addDone = N)(s2),
            to(s3, addDone = Y)(s4),
            to(s3, addDone = N)(s3),
            to(s7, addDone = Y)(s4),
            to(s7, addDone = N)(s7),
            to(s4, iHead = Y, addSign = N)(s5),
            to(s4, iHead = Y, addSign = Y)(s6),
            to(s4, iHead = N, a_i = Y, u = N)(s1),
            to(s4, iHead = N, a_i = N, u = Y)(s2),
            to(s4, iHead = N, a_i = Y, u = Y)(s3),
            to(s4, iHead = N, a_i = N, u = N)(s7),
            to(s5, addDone = Y)(s6),
            to(s5, addDone = N)(s5),
            "????????"
          ).mkString("\n")
        )
      }
    )
    .asTypeOf(StateType.Type())

  i := Mux1H(
    Map(
      state.asUInt(0) -> 1.U,
      state.asUInt(4) -> i.rotateLeft(1),
      (state.asUInt & "b11101110".U).orR -> i
    )
  )

  u := Mux1H(
    Map(
      state.asUInt(0) -> (a(0).asUInt & b(0).asUInt & pPrime.asUInt),
      (state.asUInt & "b10001110".U).orR -> ((add_stable(1) + (((a & (i.rotateLeft(1))).orR) & b(0))) & pPrime.asUInt),
      (state.asUInt & "b01110000".U).orR -> u
    )
  )

  a_i := Mux1H(
    Map(
      state.asUInt(0) -> a(0),
      (state.asUInt & "b10001110".U).orR -> (a & (i.rotateLeft(1))).orR,
      (state.asUInt & "b01110000".U).orR -> a_i
    )
  )

  nextT := Mux1H(
    Map(
      state.asUInt(0) -> 0.U,
      state.asUInt(4) -> (add_stable >> 1),
      state.asUInt(5) -> add_stable,
      (state.asUInt & "b11001110".U).orR -> nextT
    )
  )

  adder.a := nextT
  adder.b := Mux1H(
    Map(
      state.asUInt(1) -> b,
      state.asUInt(2) -> p,
      state.asUInt(3) -> b_add_p,
      state.asUInt(7) -> 0.U,
      state.asUInt(5) -> -p
    )
  )
  val debounceAdd = Mux(addDone, adder.z, 0.U)
  when(addDone)(add_stable := debounceAdd)

  // output
  out := nextT
  out_valid := state.asUInt(6)
}
