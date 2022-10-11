package v

import chisel3._
import chisel3.util.BitPat
import chisel3.util.experimental.decode._

class LaneBitLogic extends Module {
  val src: UInt = IO(Input(UInt(2.W)))
  val opcode: UInt = IO(Input(UInt(3.W)))
  val resp: Bool = IO(Output(Bool()))
  resp := decoder.qmc(opcode ## src, TruthTable(TableGenerator.LogicTable.table, BitPat.dontCare(1)))
}
