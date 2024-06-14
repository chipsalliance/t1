// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util.{BitPat, Cat, Fill, Reverse}

case class ALUParameter(xLen: Int) extends SerializableModuleParameter {
  val uopSize: Int = 4
  // static to false for now
  val usingConditionalZero = false

  // TODO:move these to decoder.
  def FN_X = BitPat("b????")
  def FN_ADD = 0.U
  def FN_SL = 1.U
  def FN_SEQ = 2.U
  def FN_SNE = 3.U
  def FN_XOR = 4.U
  def FN_SR = 5.U
  def FN_OR = 6.U
  def FN_AND = 7.U
  def FN_CZEQZ = 8.U
  def FN_CZNEZ = 9.U
  def FN_SUB = 10.U
  def FN_SRA = 11.U
  def FN_SLT = 12.U
  def FN_SGE = 13.U
  def FN_SLTU = 14.U
  def FN_SGEU = 15.U

  // Mul/div reuse some integer FNs
  def FN_DIV = FN_XOR
  def FN_DIVU = FN_SR
  def FN_REM = FN_OR
  def FN_REMU = FN_AND

  def FN_MUL = FN_ADD
  def FN_MULH = FN_SL
  def FN_MULHSU = FN_SEQ
  def FN_MULHU = FN_SNE

  def isMulFN(fn:      UInt, cmp: UInt) = fn(1, 0) === cmp(1, 0)
  def isSub(cmd:       UInt) = cmd(3)
  def isCmp(cmd:       UInt) = cmd >= FN_SLT
  def cmpUnsigned(cmd: UInt) = cmd(1)
  def cmpInverted(cmd: UInt) = cmd(0)
  def cmpEq(cmd:       UInt) = !cmd(3)

  val SZ_DW = 1
  def DW_X = BitPat.dontCare(1)
  def DW_32 = false.B
  def DW_64 = true.B
  def DW_XPR = DW_64
}

class ALUInterface(parameter: ALUParameter) extends Bundle {
  val dw = Input(UInt(1.W))
  val fn = Input(UInt(parameter.uopSize.W))
  val in2 = Input(UInt(parameter.xLen.W))
  val in1 = Input(UInt(parameter.xLen.W))
  val out = Output(UInt(parameter.xLen.W))
  val adder_out = Output(UInt(parameter.xLen.W))
  val cmp_out = Output(Bool())
}

class ALU(val parameter: ALUParameter)
  extends FixedIORawModule(new ALUInterface(parameter))
    with SerializableModule[ALUParameter] {
  // compatibility layer
  val aluFn = parameter
  val xLen = parameter.xLen
  val DW_64 = parameter.DW_64
  val usingConditionalZero = parameter.usingConditionalZero
  val DW_32 = parameter.DW_32


  // Original implementation

  // ADD, SUB
  val in2_inv = Mux(aluFn.isSub(io.fn), ~io.in2, io.in2)
  val in1_xor_in2 = io.in1 ^ in2_inv
  io.adder_out := io.in1 + in2_inv + aluFn.isSub(io.fn)

  // SLT, SLTU
  val slt =
    Mux(
      io.in1(xLen - 1) === io.in2(xLen - 1),
      io.adder_out(xLen - 1),
      Mux(aluFn.cmpUnsigned(io.fn), io.in2(xLen - 1), io.in1(xLen - 1))
    )
  io.cmp_out := aluFn.cmpInverted(io.fn) ^ Mux(aluFn.cmpEq(io.fn), in1_xor_in2 === 0.U, slt)

  // SLL, SRL, SRA
  val (shamt, shin_r) =
    if (xLen == 32) (io.in2(4, 0), io.in1)
    else {
      require(xLen == 64)
      val shin_hi_32 = Fill(32, aluFn.isSub(io.fn) && io.in1(31))
      val shin_hi = Mux(io.dw === DW_64, io.in1(63, 32), shin_hi_32)
      val shamt = Cat(io.in2(5) & (io.dw === DW_64), io.in2(4, 0))
      (shamt, Cat(shin_hi, io.in1(31, 0)))
    }
  val shin = Mux(io.fn === aluFn.FN_SR || io.fn === aluFn.FN_SRA, shin_r, Reverse(shin_r))
  val shout_r = (Cat(aluFn.isSub(io.fn) & shin(xLen - 1), shin).asSInt >> shamt)(xLen - 1, 0)
  val shout_l = Reverse(shout_r)
  val shout = Mux(io.fn === aluFn.FN_SR || io.fn === aluFn.FN_SRA, shout_r, 0.U) |
    Mux(io.fn === aluFn.FN_SL, shout_l, 0.U)

  // CZEQZ, CZNEZ
  val in2_not_zero = io.in2.orR
  val cond_out = Option.when(usingConditionalZero)(
    Mux((io.fn === aluFn.FN_CZEQZ && in2_not_zero) || (io.fn === aluFn.FN_CZNEZ && !in2_not_zero), io.in1, 0.U)
  )

  // AND, OR, XOR
  val logic = Mux(io.fn === aluFn.FN_XOR || io.fn === aluFn.FN_OR, in1_xor_in2, 0.U) |
    Mux(io.fn === aluFn.FN_OR || io.fn === aluFn.FN_AND, io.in1 & io.in2, 0.U)

  val shift_logic = (aluFn.isCmp(io.fn) && slt) | logic | shout
  val shift_logic_cond = cond_out match {
    case Some(co) => shift_logic | co
    case _        => shift_logic
  }
  val out = Mux(io.fn === aluFn.FN_ADD || io.fn === aluFn.FN_SUB, io.adder_out, shift_logic_cond)

  io.out := out
  if (xLen > 32) {
    require(xLen == 64)
    when(io.dw === DW_32) { io.out := Cat(Fill(32, out(31)), out(31, 0)) }
  }
}
