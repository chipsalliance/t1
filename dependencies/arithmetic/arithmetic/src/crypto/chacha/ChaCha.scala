package crypto.chacha

import chisel3._
import chisel3.util.{Decoupled, Mux1H}
import chisel3.experimental.VecLiterals.AddVecLiteralConstructor

case class ChaChaParameter(nonceWidth: Int)
class ChaCha(parameter: ChaChaParameter) extends Module {
  val chachaHead = Vec(4, UInt(32.W)).Lit(
    0 -> 0x61707865.U,
    1 -> 0x3320646e.U,
    2 -> 0x79622d32.U,
    3 -> 0x6b206574.U
  )

  // allocate key for this ChaCha Module
  // outer nonce should be updated as well.
  // for each hypervisor, there should be a ChaCha Pool.
  // updated by MMIO Store
  val key = IO(Input(Vec(2, Vec(4, UInt(32.W)))))
  // Should be ASID tag captured from page info
  // should be an additional userbits in TileLink protocol
  val nonce = IO(Input(UInt(parameter.nonceWidth.W)))
  // key and counter is valid can start to calculate.
  val valid = IO(Input(Bool()))

  // used by nonce-pool
  val output = IO(Decoupled(new Bundle {
    val nonce = UInt(parameter.nonceWidth.W)
    val counter = UInt((128 - parameter.nonceWidth).W)
    val x = Vec(4, Vec(4, UInt(32.W))) // output state matrix
  }))

  // Internal generated nonce which will be used for encrypt and decrypt.
  val counter = RegInit(0.U((128 - parameter.nonceWidth).W))
  val update = RegInit(false.B)
  // counter update logic
  counter := Mux(update, counter + 1.U, counter)
  val lastRow = counter ## nonce

  // matrix
  val matrix: Seq[Seq[UInt]] = Seq.fill(4)(Seq.fill(4)(RegInit(0.U(32.W))))

  val state = RegInit(1.U(5.W))
  state := Mux(valid, state.rotateLeft(1), state) // rot left circularly

  val rounds = RegInit(0.U(5.W))
  rounds := Mux(valid && (state(4).asBool), Mux(rounds === 19.U, 0.U, rounds + 1.U), rounds)
  val control = chisel3.util.experimental.decode.decoder(
    state,
    chisel3.util.experimental.decode.TruthTable.fromString(
      s"""00001->100000000000
         |00010->001100100011
         |00100->010010010100
         |01000->001101000011
         |10000->010010011000
         |      ????????????""".stripMargin
    )
  )
// readData
// keepD ## keepC ## keepB ## keepA
// updateDShift8 ## updateDShift16
// updateC
// updateBShift7 ## updateBShift12
// updateA
// flag
// r dcba  d c  b a f
// 1 0000 00 0 00 0 0
// 0 0110 01 0 00 1 1
// 0 1001 00 1 01 0 0
// 0 0110 10 0 00 1 1
// 0 1001 00 1 10 0 0
  assert(rounds < 20.U, "rounds should less then 20")
  val flag:           Bool = control(0)
  val updateA:        Bool = control(1)
  val updateBShift12: Bool = control(2)
  val updateBShift7:  Bool = control(3)
  val updateC:        Bool = control(4)
  val updateDShift16: Bool = control(5)
  val updateDShift8:  Bool = control(6)
  val keepA:          Bool = control(7)
  val keepB:          Bool = control(8)
  val keepC:          Bool = control(9)
  val keepD:          Bool = control(10)
  val readData:       Bool = control(11)

  val permuteOdd:  Bool = rounds(0)
  val permuteEven: Bool = !rounds(0)

  // Data path
  matrix.zipWithIndex.foreach {
    case (row, i) =>
      val a = row(0)
      val b = row(1)
      val c = row(2)
      val d = row(3)
      val adder = Module(new Add)
      val xor = Module(new Xor)
      adder.a := Mux(flag, a, c)
      adder.b := Mux(flag, b, d)
      xor.a := Mux(flag, d, b)
      xor.b := adder.c
      a := Mux1H(
        Map(
          readData -> Mux(rounds === 0.U, chachaHead(i), matrix(i)(0)),
          updateA -> adder.c,
          keepA -> a
        )
      )
      b := Mux1H(
        Map(
          readData -> Mux(
            rounds === 0.U,
            key(0)(i),
            Mux1H(
              Map(
                permuteOdd -> matrix((i + 1) % 4)(1),
                permuteEven -> matrix((i + 3) % 4)(1)
              )
            )
          ),
          updateBShift12 -> xor.c.rotateLeft(12),
          updateBShift7 -> xor.c.rotateLeft(7),
          keepB -> b
        )
      )
      c := Mux1H(
        Map(
          readData -> Mux(
            rounds === 0.U,
            key(1)(i),
            Mux1H(
              Map(
                permuteOdd -> matrix((i + 2) % 4)(2),
                permuteEven -> matrix((i + 2) % 4)(2)
              )
            )
          ),
          updateC -> adder.c,
          keepC -> c
        )
      )
      d := Mux1H(
        Map(
          readData -> Mux(
            rounds === 0.U,
            lastRow(127 - i * 32, 127 - (i + 1) * 32 + 1),
            Mux1H(
              Map(
                permuteOdd -> matrix((i + 3) % 4)(3),
                permuteEven -> matrix((i + 1) % 4)(3)
              )
            )
          ),
          updateDShift16 -> xor.c.rotateLeft(16),
          updateDShift8 -> xor.c.rotateLeft(8),
          keepD -> d
        )
      )
  }

  // output
  output.bits.nonce := nonce
  output.bits.counter := counter
  output.valid := (rounds === 19.U) && state(4).asBool
  matrix.zipWithIndex.foreach { // the output should be reorganized
    case (row, i) =>
      output.bits.x(i)(0) := matrix((4 - i) % 4)(i)
      output.bits.x(i)(1) := matrix((5 - i) % 4)(i)
      output.bits.x(i)(2) := matrix((6 - i) % 4)(i)
      output.bits.x(i)(3) := matrix((7 - i) % 4)(i)
  }
}

class Add extends Module {
  val a = IO(Input(UInt(32.W)))
  val b = IO(Input(UInt(32.W)))
  val c = IO(Output(UInt(32.W)))
  c := a +% b
}

class Xor extends Module {
  val a = IO(Input(UInt(32.W)))
  val b = IO(Input(UInt(32.W)))
  val c = IO(Output(UInt(32.W)))
  c := a ^ b
}
