package v


import chisel3._
import chisel3.util._
import freechips.rocketchip.util.{OH1ToOH, OH1ToUInt, leftOR}

class LaneFFO(param: DataPathParam) extends Module {
  val src: UInt = IO(Input(UInt(param.dataWidth.W)))
  val resultSelect: UInt = IO(Input(UInt(2.W)))
  val resp: ValidIO[UInt] = IO(Output(Valid(UInt(param.dataWidth.W))))
  // todo: add mask
  val notZero: Bool = src.orR
  val lo: UInt = leftOR(src)
  // set before(right or)
  val ro: UInt = (~lo).asUInt
  // set including
  val inc: UInt = ro ## notZero
  // 1H
  val OH: UInt = lo & inc
  val index: UInt = OH1ToUInt(OH)

  resp.valid := notZero
  // "vfirst" -> first set, "vmsbf" -> before, "vmsof" -> oh, "vmsif" -> include
  resp.bits := Mux1H(UIntToOH(resultSelect), Seq(index, ro, OH, inc))
}
