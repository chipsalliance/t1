package v

import chisel3._

class LaneLogic(param: VectorParameters) extends Module {
  val src:  Vec[UInt] = IO(Input(Vec(2, UInt(param.ELEN.W))))
  val opcode: UInt = IO(Input(UInt(3.W)))
  val resp: UInt = IO(Output(UInt(param.ELEN.W)))

  resp := VecInit(src.map(_.asBools).transpose.map { case Seq(sr0, sr1) =>
    val bitCalculate = Module(new LaneBitLogic)
    bitCalculate.src := sr0 ## sr1
    bitCalculate.opcode := opcode
    bitCalculate.resp
  }).asUInt
}
