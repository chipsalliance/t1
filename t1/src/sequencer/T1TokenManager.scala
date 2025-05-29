// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._

class IssueToken(parameter: T1Parameter) extends Bundle {
  val instructionIndex: UInt = UInt(parameter.instructionIndexBits.W)
  val writeV0:          Bool = Bool()
  val useV0AsMask:      Bool = Bool()
  val isLoadStore:      Bool = Bool()
  val toLane:           Bool = Bool()
  val toMask:           Bool = Bool()
}

@instantiable
class T1TokenManager(parameter: T1Parameter) extends Module {
  @public
  val instructionIssue: ValidIO[IssueToken] = IO(Flipped(Valid(new IssueToken(parameter))))

  @public
  val lsuWriteV0: Vec[ValidIO[UInt]] = IO(
    Vec(parameter.laneNumber, Flipped(Valid(UInt(parameter.instructionIndexBits.W))))
  )

  @public
  val issueAllow: Bool = IO(Output(Bool()))

  @public
  val instructionFinish: Vec[UInt] = IO(Vec(parameter.laneNumber, Input(UInt(parameter.chaining1HBits.W))))

  @public
  val v0WriteValid = IO(Output(UInt(parameter.chaining1HBits.W)))

  @public
  val maskUnitFree: Bool = IO(Input(Bool()))

  val issueIndex1H: UInt = indexToOH(instructionIssue.bits.instructionIndex, parameter.chainingSize)

  // Boolean type token clear & set
  def updateBooleanToken(set: UInt, clear: UInt): UInt = {
    VecInit(Seq.tabulate(parameter.chaining1HBits) { chainingIndex =>
      val res = RegInit(false.B)
      when(set(chainingIndex) || clear(chainingIndex)) {
        res := set(chainingIndex)
      }
      res
    }).asUInt
  }

  // v0 write token
  val v0WriteValidVec: Seq[UInt] = Seq.tabulate(parameter.laneNumber) { laneIndex =>
    val lsuWriteSet  = maskAnd(
      lsuWriteV0(laneIndex).valid,
      indexToOH(lsuWriteV0(laneIndex).bits, parameter.chainingSize)
    ).asUInt
    val v0WriteIssue =
      instructionIssue.valid && instructionIssue.bits.writeV0 && (instructionIssue.bits.toLane || instructionIssue.bits.isLoadStore)
    val clear: UInt = instructionFinish(laneIndex)
    val updateOH = maskAnd(v0WriteIssue, issueIndex1H).asUInt
    updateBooleanToken(updateOH | lsuWriteSet, clear)
  }

  val useV0AsMaskToken: UInt = Seq
    .tabulate(parameter.laneNumber) { laneIndex =>
      val useV0Issue = instructionIssue.valid && instructionIssue.bits.useV0AsMask &&
        instructionIssue.bits.toLane
      val clear: UInt = instructionFinish(laneIndex)
      val updateOH = maskAnd(useV0Issue, issueIndex1H).asUInt
      updateBooleanToken(updateOH, clear)
    }
    .reduce(_ | _)

  val maskUnitWriteV0: Bool = {
    val set   = instructionIssue.valid && instructionIssue.bits.writeV0 && instructionIssue.bits.toMask
    val clear = maskUnitFree
    val res   = RegInit(false.B)
    when(set || clear) {
      res := set
    }
    res
  }

  v0WriteValid := v0WriteValidVec.reduce(_ | _)

  // v0 read-write conflict
  val v0Conflict: Bool =
    (instructionIssue.bits.writeV0 && useV0AsMaskToken.orR) ||
      (instructionIssue.bits.useV0AsMask && (v0WriteValid.orR || maskUnitWriteV0))

  issueAllow := !v0Conflict
}
