// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v
import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}

object LogicParam {
  implicit def rw: upickle.default.ReadWriter[LogicParam] = upickle.default.macroRW
}
case class LogicParam(datapathWidth: Int, latency: Int) extends VFUParameter with SerializableModuleParameter {
  val decodeField: BoolField = Decoder.logic
  val inputBundle = new MaskedLogicRequest(datapathWidth)
  val outputBundle = new MaskedLogicResponse(datapathWidth)
}

class MaskedLogicRequest(datapathWidth: Int) extends Bundle {

  /** 0, 1: two operands
   * 2: original data, read from vd, used to write with fine granularity
   * 3: mask, determined by v0.mask and vl
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

class MaskedLogicResponse(datapathWidth: Int) extends Bundle {
  val data: UInt = UInt(datapathWidth.W)
}

class MaskedLogic(val parameter: LogicParam) extends VFUModule(parameter) with SerializableModule[LogicParam] {
  val response: UInt = Wire(UInt(parameter.datapathWidth.W))
  val request: MaskedLogicRequest = connectIO(response).asTypeOf(parameter.inputBundle)

  response := VecInit(request.src.map(_.asBools).transpose.map {
    case Seq(sr0, sr1, sr2, sr3) =>
      val bitCalculate = Module(new LaneBitLogic)
      bitCalculate.src := (request.opcode(2) ^ sr0) ## sr1
      bitCalculate.opcode := request.opcode
      Mux(sr3, bitCalculate.resp ^ request.opcode(3), sr2)
  }).asUInt
}
