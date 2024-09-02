// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._

@instantiable
class T1TokenManager(parameter: T1Parameter) extends Module {
  @public
  val writeV0 = IO(Vec(parameter.laneNumber, Flipped(Valid(UInt(parameter.instructionIndexBits.W)))))

  @public
  val instructionFinish: Vec[UInt] = IO(Vec(parameter.laneNumber, Input(UInt(parameter.chainingSize.W))))

  @public
  val v0WriteValid = IO(Output(UInt(parameter.chainingSize.W)))

  // v0 write token
  val v0WriteValidVec: Seq[UInt] = Seq.tabulate(parameter.laneNumber) { laneIndex =>
    val update: ValidIO[UInt] = writeV0(laneIndex)
    val clear:  UInt          = instructionFinish(laneIndex)
    val updateOH = maskAnd(update.valid, indexToOH(update.bits, parameter.chainingSize)).asUInt
    VecInit(Seq.tabulate(parameter.chainingSize) { chainingIndex =>
      val res = RegInit(false.B)
      when(updateOH(chainingIndex) || clear(chainingIndex)) {
        res := updateOH(chainingIndex)
      }
      res
    }).asUInt
  }

  v0WriteValid := v0WriteValidVec.reduce(_ | _)
}
