// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{Instance, instantiable, Instantiate}
import chisel3.util.BitPat
import chisel3.util.experimental.decode.TruthTable
import org.chipsalliance.t1.rtl.decoder.TableGenerator
import chisel3.properties.{AnyClassType, ClassType, Property}
import org.chipsalliance.stdlib.GeneralOM

class LaneLogicRequest(datapathWidth: Int) extends Bundle {
  val src: Vec[UInt] = Vec(2, UInt(datapathWidth.W))

  /** n_op ## op_n ## op */
  val opcode: UInt = UInt(4.W)
}

object LaneLogicParameter {
  implicit def rwP: upickle.default.ReadWriter[LaneLogicParameter] = upickle.default.macroRW
}

case class LaneLogicParameter(datapathWidth: Int) extends SerializableModuleParameter

class LaneLogicInterface(parameter: LaneLogicParameter) extends Bundle {
  val req:  LaneLogicRequest    = Input(new LaneLogicRequest(parameter.datapathWidth))
  val resp: UInt                = Output(UInt(parameter.datapathWidth.W))
  val om:   Property[ClassType] = Output(Property[AnyClassType]())
}

class LaneLogicOM(parameter: LaneLogicParameter) extends GeneralOM[LaneLogicParameter, LaneLogic](parameter)

@instantiable
class LaneLogic(val parameter: LaneLogicParameter)
    extends FixedIORawModule(new LaneLogicInterface(parameter))
    with SerializableModule[LaneLogicParameter] {
  val omInstance: Instance[LaneLogicOM] = Instantiate(new LaneLogicOM(parameter))
  io.om := omInstance.getPropertyReference

  val req  = io.req
  val resp = io.resp

  resp := VecInit(req.src.map(_.asBools).transpose.map { case Seq(sr0, sr1) =>
    chisel3.util.experimental.decode.decoder
      .qmc(
        req.opcode ## (sr0 ## (req.opcode(2) ^ sr1)),
        TruthTable(TableGenerator.LogicTable.table, BitPat.dontCare(1))
      ) ^
      req.opcode(3)
  }).asUInt
}
