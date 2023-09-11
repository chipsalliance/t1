package VFU

import chisel3._
import chisel3.util._

class csa42(width: Int) extends Module{
  val in = IO(Input(Vec(4, UInt(width.W))))
  val out = IO(Output(Vec(2, UInt((width+1).W))))

  class CSACompressor4_2 extends Module {
    val in = IO(Input(Vec(4, UInt(1.W))))
    val cin = IO(Input(UInt(1.W)))
    val out = IO(Output(Vec(2, UInt(1.W))))
    val cout = IO(Output(UInt(1.W)))

    val ab = in(0) ^ in(1)
    val cd = in(2) ^ in(3)
    val abcd = ab ^ cd
    // sum
    out(1) := abcd ^ cin
    // carry
    out(0) := Mux(abcd.asBool, cin, in(3))
    cout := Mux(ab.asBool, in(2), in(0))
  }

  val compressor: Seq[CSACompressor4_2] = Seq.fill(width)(Module(new CSACompressor4_2))

  /** cout in order
    *
    * coutUInt(i) represents cout for compressor(i)
    */
  val coutUInt = VecInit(compressor.map(_.cout)).asUInt
  val cinVec: Seq[Bool] = Cat(coutUInt(width-2, 0), 0.U(1.W)).asBools
  val compressorAssign = compressor
    .zip(cinVec)
    .zipWithIndex
    .map{
      case ((c, cin), i) => {
        c.in(0) := in(0)(i)
        c.in(1) := in(1)(i)
        c.in(2) := in(2)(i)
        c.in(3) := in(3)(i)
        c.cin   := cin
      }
    }
  /** sum */
  out(1) := VecInit(compressor.map(_.out(1)) :+ coutUInt(width-1)).asUInt
  /** carry */
  out(0) := VecInit(compressor.map(_.out(0))).asUInt

}

object csa42 {
  def apply(
             width:     Int,
           )(in:         Vec[UInt]
           ): (UInt, UInt) = {
    val csa42 = Module(new csa42(width))
    csa42.in := in
    (csa42.out(0), csa42.out(1))
  }
}