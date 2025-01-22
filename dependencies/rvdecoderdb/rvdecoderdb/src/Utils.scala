// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2023 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.rvdecoderdb

object Utils {
  def isR(instruction: Instruction): Boolean = instruction.args.map(_.name) == Seq("rd", "rs1", "rs2")

  def isI(instruction: Instruction): Boolean = instruction.args.map(_.name) == Seq("rd", "rs1", "imm12")

  def isS(instruction: Instruction): Boolean = instruction.args.map(_.name) == Seq("imm12lo", "rs1", "rs2", "imm12hi")

  def isB(instruction: Instruction): Boolean = instruction.args.map(_.name) == Seq("bimm12lo", "rs1", "rs2", "bimm12hi")

  def isU(instruction: Instruction): Boolean = instruction.args.map(_.name) == Seq("rd", "imm20")

  def isJ(instruction: Instruction): Boolean = instruction.args.map(_.name) == Seq("rd", "jimm20")

  def isR4(instruction: Instruction): Boolean = instruction.args.map(_.name) == Seq("rd", "rs1", "rs2", "rs3")

  // some general helper to sort instruction out
  def isFP(instruction: Instruction): Boolean = Seq(
    "rv_d",
    "rv_f",
    "rv_q",
    "rv64_zfh",
    "rv_d_zfh",
    "rv_q_zfh",
    "rv_zfh",
    // unratified
    "rv_zfh_zfa"
  ).exists(instruction.instructionSets.map(_.name).contains)

  def readRs1(instruction: Instruction): Boolean = instruction.args.map(_.name).contains("rs1")

  def readRs2(instruction: Instruction): Boolean = instruction.args.map(_.name).contains("rs2")

  def writeRd(instruction: Instruction): Boolean = instruction.args.map(_.name).contains("rd")
}
