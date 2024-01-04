// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package org.chipsalliance.t1.rocketcore

import chisel3._
import chisel3.util.{Cat, Fill, Reverse}

case class ALUParameter(
  // used to access all uops
  decoder:              InstructionDecoder,
  xLen:                 Int,
  usingConditionalZero: Boolean)

class ALU(parameter: ALUParameter) extends Module {
  private def xLen = parameter.xLen
  private def usingConditionalZero = parameter.usingConditionalZero
  private def uop = parameter.decoder.UOPALU

  val io = IO(new Bundle {
    val dw = Input(UInt(1.W))
    val fn = Input(UInt(uop.width.W))
    val in2 = Input(UInt(xLen.W))
    val in1 = Input(UInt(xLen.W))
    val out = Output(UInt(xLen.W))
    val adder_out = Output(UInt(xLen.W))
    val cmp_out = Output(Bool())
  })
  // ADD, SUB
  val in2_inv = Mux(uop.isSub(io.fn), ~io.in2, io.in2)
  val in1_xor_in2 = io.in1 ^ in2_inv
  io.adder_out := io.in1 + in2_inv + uop.isSub(io.fn)

  // SLT, SLTU
  val slt =
    Mux(
      io.in1(xLen - 1) === io.in2(xLen - 1),
      io.adder_out(xLen - 1),
      Mux(uop.cmpUnsigned(io.fn), io.in2(xLen - 1), io.in1(xLen - 1))
    )
  io.cmp_out := uop.cmpInverted(io.fn) ^ Mux(uop.cmpEq(io.fn), in1_xor_in2 === 0.U, slt)

  // SLL, SRL, SRA
  val (shamt, shin_r) =
    if (xLen == 32) (io.in2(4, 0), io.in1)
    else {
      require(xLen == 64)
      val shin_hi_32 = Fill(32, uop.isSub(io.fn) && io.in1(31))
      val shin_hi = Mux(io.dw === DW_64, io.in1(63, 32), shin_hi_32)
      val shamt = Cat(io.in2(5) & (io.dw === DW_64), io.in2(4, 0))
      (shamt, Cat(shin_hi, io.in1(31, 0)))
    }
  val shin = Mux(io.fn === uop.sr || io.fn === uop.sra, shin_r, Reverse(shin_r))
  val shout_r = (Cat(uop.isSub(io.fn) & shin(xLen - 1), shin).asSInt >> shamt)(xLen - 1, 0)
  val shout_l = Reverse(shout_r)
  val shout = Mux(io.fn === uop.sr || io.fn === uop.sra, shout_r, 0.U) |
    Mux(io.fn === uop.sl, shout_l, 0.U)

  // CZEQZ, CZNEZ
  val in2_not_zero = io.in2.orR
  val cond_out = Option.when(usingConditionalZero)(
    Mux((io.fn === uop.czeqz && in2_not_zero) || (io.fn === uop.cznez && !in2_not_zero), io.in1, 0.U)
  )

  // AND, OR, XOR
  val logic = Mux(io.fn === uop.xor || io.fn === uop.or, in1_xor_in2, 0.U) |
    Mux(io.fn === uop.or || io.fn === uop.and, io.in1 & io.in2, 0.U)

  val shift_logic = (uop.isCmp(io.fn) && slt) | logic | shout
  val shift_logic_cond = cond_out match {
    case Some(co) => shift_logic | co
    case _        => shift_logic
  }
  val out = Mux(io.fn === uop.add || io.fn === uop.sub, io.adder_out, shift_logic_cond)

  io.out := out
  if (xLen > 32) {
    require(xLen == 64)
    when(io.dw === DW_32) { io.out := Cat(Fill(32, out(31)), out(31, 0)) }
  }
}
