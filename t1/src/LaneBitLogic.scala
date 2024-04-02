// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util.BitPat
import chisel3.util.experimental.decode._
import org.chipsalliance.t1.rtl.decoder.TableGenerator

@instantiable
class LaneBitLogic extends Module {
  @public
  val src:    UInt = IO(Input(UInt(2.W)))
  @public
  val opcode: UInt = IO(Input(UInt(2.W)))
  @public
  val resp:   Bool = IO(Output(Bool()))
  resp := decoder.qmc(opcode ## src, TruthTable(TableGenerator.LogicTable.table, BitPat.dontCare(1)))
}
