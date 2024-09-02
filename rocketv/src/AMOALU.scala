// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.instantiable
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util.{log2Ceil, FillInterleaved, PriorityMux}

object AMOALUParameter {
  implicit def rwP: upickle.default.ReadWriter[AMOALUParameter] = upickle.default.macroRW[AMOALUParameter]
}

case class AMOALUParameter(operandBits: Int) extends SerializableModuleParameter {
  val uopSize: Int = 4
  def M_XA_ADD  = "b01000".U
  def M_XA_XOR  = "b01001".U
  def M_XA_OR   = "b01010".U
  def M_XA_AND  = "b01011".U
  def M_XA_MIN  = "b01100".U
  def M_XA_MAX  = "b01101".U
  def M_XA_MINU = "b01110".U
  def M_XA_MAXU = "b01111".U
}

class AMOALUInterface(parameter: AMOALUParameter) extends Bundle {
  val mask         = Input(UInt((parameter.operandBits / 8).W))
  val cmd          = Input(UInt(parameter.uopSize.W))
  val lhs          = Input(UInt(parameter.operandBits.W))
  val rhs          = Input(UInt(parameter.operandBits.W))
  val out          = Output(UInt(parameter.operandBits.W))
  val out_unmasked = Output(UInt(parameter.operandBits.W))
}

@instantiable
class AMOALU(val parameter: AMOALUParameter)
    extends FixedIORawModule(new AMOALUInterface(parameter))
    with SerializableModule[AMOALUParameter]
    with Public {
  val M_XA_MAX    = parameter.M_XA_MAX
  val M_XA_MAXU   = parameter.M_XA_MAXU
  val M_XA_MIN    = parameter.M_XA_MIN
  val M_XA_MINU   = parameter.M_XA_MINU
  val M_XA_ADD    = parameter.M_XA_ADD
  val M_XA_OR     = parameter.M_XA_OR
  val M_XA_AND    = parameter.M_XA_AND
  val M_XA_XOR    = parameter.M_XA_XOR
  val operandBits = parameter.operandBits
  val minXLen     = 32
  val widths      = (0 to log2Ceil(operandBits / minXLen)).map(minXLen << _)

  // Original implementation

  val max       = io.cmd === M_XA_MAX || io.cmd === M_XA_MAXU
  val min       = io.cmd === M_XA_MIN || io.cmd === M_XA_MINU
  val add       = io.cmd === M_XA_ADD
  val logic_and = io.cmd === M_XA_OR || io.cmd === M_XA_AND
  val logic_xor = io.cmd === M_XA_XOR || io.cmd === M_XA_OR

  val adder_out = {
    // partition the carry chain to support sub-xLen addition
    val mask = ~(0.U(operandBits.W) +: widths.init.map(w => !io.mask(w / 8 - 1) << (w - 1))).reduce(_ | _)
    (io.lhs & mask) + (io.rhs & mask)
  }

  val less = {
    // break up the comparator so the lower parts will be CSE'd
    def isLessUnsigned(x: UInt, y: UInt, n: Int): Bool = {
      if (n == minXLen) x(n - 1, 0) < y(n - 1, 0)
      else x(n - 1, n / 2) < y(n - 1, n / 2) || x(n - 1, n / 2) === y(n - 1, n / 2) && isLessUnsigned(x, y, n / 2)
    }

    def isLess(x: UInt, y: UInt, n: Int): Bool = {
      val signed = {
        val mask = M_XA_MIN ^ M_XA_MINU
        (io.cmd & mask) === (M_XA_MIN & mask)
      }
      Mux(x(n - 1) === y(n - 1), isLessUnsigned(x, y, n), Mux(signed, x(n - 1), y(n - 1)))
    }

    PriorityMux(widths.reverse.map(w => (io.mask(w / 8 / 2), isLess(io.lhs, io.rhs, w))))
  }

  val minmax = Mux(Mux(less, min, max), io.lhs, io.rhs)
  val logic  =
    Mux(logic_and, io.lhs & io.rhs, 0.U) |
      Mux(logic_xor, io.lhs ^ io.rhs, 0.U)
  val out    =
    Mux(add, adder_out, Mux(logic_and || logic_xor, logic, minmax))

  val wmask = FillInterleaved(8, io.mask)
  io.out          := wmask & out | ~wmask & io.lhs
  io.out_unmasked := out
}
