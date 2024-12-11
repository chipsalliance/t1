// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{Instance, instantiable, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.properties.{Property, Path}
import chisel3.util.BitPat
import chisel3.util.experimental.decode.TruthTable
import org.chipsalliance.stdlib.GeneralOM
import org.chipsalliance.t1.rtl.decoder.{BoolField, TableGenerator, Decoder}

object LogicParam {
  implicit def rw: upickle.default.ReadWriter[LogicParam] = upickle.default.macroRW
}
case class LogicParam(datapathWidth: Int, latency: Int) extends VFUParameter with SerializableModuleParameter {
  val decodeField: BoolField = Decoder.logic
  val inputBundle  = new MaskedLogicRequest(datapathWidth)
  val outputBundle = new MaskedLogicResponse(datapathWidth)
}

class MaskedLogicRequest(datapathWidth: Int) extends VFUPipeBundle {

  /** 0, 1: two operands 2: original data, read from vd, used to write with fine granularity 3: mask, determined by
    * v0.mask and vl
    */
  val src: Vec[UInt] = Vec(4, UInt(datapathWidth.W))

  /** see the logic part in [[Decoder.uop]] n_op ## op_n ## op n_op: `op.name.startsWith("vmn")`, e.g. nand, nor op_n:
    * `isXnor || op.name.endsWith("n")`, e.g. andn, orn op: `and`, `or`, `xor`
    */
  val opcode: UInt = UInt(4.W)
}

class MaskedLogicResponse(datapathWidth: Int) extends VFUPipeBundle {
  val data: UInt = UInt(datapathWidth.W)
}

class MaskedLogicOM(parameter: LogicParam) extends GeneralOM[LogicParam, MaskedLogic](parameter) {
  override def hasRetime: Boolean = true
}

@instantiable
class MaskedLogic(val parameter: LogicParam) extends VFUModule with SerializableModule[LogicParam] {
  val omInstance: Instance[MaskedLogicOM] = Instantiate(new MaskedLogicOM(parameter))
  omInstance.retimeIn.foreach(_ := Property(Path(clock)))

  val response: MaskedLogicResponse = Wire(new MaskedLogicResponse(parameter.datapathWidth))
  val request:  MaskedLogicRequest  = connectIO(response).asTypeOf(parameter.inputBundle)

  response.data := VecInit(request.src.map(_.asBools).transpose.map { case Seq(sr0, sr1, sr2, sr3) =>
    Mux(
      sr3,
      chisel3.util.experimental.decode.decoder
        .qmc(
          request.opcode ## ((request.opcode(2) ^ sr0) ## sr1),
          TruthTable(TableGenerator.LogicTable.table, BitPat.dontCare(1))
        ) ^ request.opcode(3),
      sr2
    )
  }).asUInt
}
