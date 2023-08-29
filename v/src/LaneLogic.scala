// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v

import chisel3._

class LaneLogicRequest(datapathWidth: Int) extends Bundle {
  val src: Vec[UInt] = Vec(2, UInt(datapathWidth.W))

  /** n_op ## op_n ## op */
  val opcode: UInt = UInt(4.W)
}

class LaneLogic(datapathWidth: Int) extends Module {
  val req:  LaneLogicRequest = IO(Input(new LaneLogicRequest(datapathWidth)))
  val resp: UInt = IO(Output(UInt(datapathWidth.W)))

  resp := VecInit(req.src.map(_.asBools).transpose.map {
    case Seq(sr0, sr1) =>
      val bitCalculate = Module(new LaneBitLogic)
      bitCalculate.src := sr0 ## (req.opcode(2) ^ sr1)
      bitCalculate.opcode := req.opcode
      bitCalculate.resp ^ req.opcode(3)
  }).asUInt
}
