package v


import chisel3._
import chisel3.util._

class LaneFFO(param: DataPathParam) extends Module {
  val src: UInt = IO(Input(UInt(param.dataWidth.W)))
  val resultSelect: UInt = IO(Input(UInt(2.W)))
  val resp: ValidIO[UInt] = IO(Output(Valid(UInt(param.dataWidth.W))))
  // todo: add mask
  val notZero: Bool = src.orR
  val lo: UInt = scanLeftOr(src)
  // set before(right or)
  val ro: UInt = (~lo).asUInt
  // set including
  val inc: UInt = ro ## notZero
  // 1H
  val OH: UInt = lo & inc
  val index: UInt = OH1ToUInt(OH)

  // copy&paste from rocket-chip: src/main/scala/util/package.scala
  // todo: upstream this to chisel3
  private def OH1ToOH(x: UInt): UInt = ((x << 1: UInt) | 1.U) & ~Cat(0.U(1.W), x)
  private def OH1ToUInt(x: UInt): UInt = OHToUInt(OH1ToOH(x))
  resp.valid := notZero
  resp.bits := Mux1H(UIntToOH(resultSelect), Seq(ro, inc, OH, index))
}
