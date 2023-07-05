package v
import chisel3._

case class LogicParam(datapathWidth: Int) extends VFUParameter {
  val decodeField: BoolField = Decoder.logic
  val inputBundle = new MaskedLogicRequest(datapathWidth)
  val outputBundle: UInt = UInt(datapathWidth.W)
}

class MaskedLogicRequest(datapathWidth: Int) extends Bundle {

  /** 0, 1: two operands
    * 2: mask, determined by v0.mask and vl
    * 3: original data, read from vd, used to write with fine granularity
    */
  val src: Vec[UInt] = Vec(4, UInt(datapathWidth.W))

  /** see the logic part in [[Decoder.uop]]
    * n_op ## op_n ## op
    * n_op: `op.name.startsWith("vmn")`, e.g. nand, nor
    * op_n: `isXnor || op.name.endsWith("n")`, e.g. andn, orn
    * op: `and`, `or`, `xor`
    */
  val opcode: UInt = UInt(4.W)
}

class MaskedLogic(val parameter: LogicParam) extends VFUModule(parameter) {
  val response: UInt = Wire(UInt(parameter.datapathWidth.W))
  val request: MaskedLogicRequest = connectIO(response).asTypeOf(parameter.inputBundle)

  response := VecInit(request.src.map(_.asBools).transpose.map {
    case Seq(sr0, sr1, sr2, sr3) =>
      val bitCalculate = Module(new LaneBitLogic)
      bitCalculate.src := sr0 ## (request.opcode(2) ^ sr1)
      bitCalculate.opcode := request.opcode
      Mux(sr2, bitCalculate.resp ^ request.opcode(3), sr3)
  }).asUInt
}
