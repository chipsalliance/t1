package v
import chisel3._

class MaskedLogicRequest(param: DataPathParam) extends Bundle {
  /** 0, 1： 两个操作数
    * 2：mask, 由v0.mask和vl共同决定
    * 3: 原始数据,从vd里读出来的,用来细粒度地写
    */
  val src: Vec[UInt] = Vec(4, UInt(param.dataWidth.W))

  /** n_op ## op_n ## op */
  val opcode: UInt = UInt(4.W)
}

class MaskedLogic(param: DataPathParam) extends Module {
  val req:  MaskedLogicRequest = IO(Input(new MaskedLogicRequest(param)))
  val resp: UInt = IO(Output(UInt(param.dataWidth.W)))

  resp := VecInit(req.src.map(_.asBools).transpose.map {
    case Seq(sr0, sr1, sr2, sr3) =>
      val bitCalculate = Module(new LaneBitLogic)
      bitCalculate.src := sr0 ## (req.opcode(2) ^ sr1)
      bitCalculate.opcode := req.opcode
      Mux(sr2, bitCalculate.resp ^ req.opcode(3), sr3)
  }).asUInt
}
