// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.util.Cat

object ImmGen {
  def IMM_S  = 0.U(3.W)
  def IMM_SB = 1.U(3.W)
  def IMM_U  = 2.U(3.W)
  def IMM_UJ = 3.U(3.W)
  def IMM_I  = 4.U(3.W)
  def IMM_Z  = 5.U(3.W)

  def apply(sel: UInt, inst: UInt) = {
    val sign   = Mux(sel === IMM_Z, 0.S, inst(31).asSInt)
    val b30_20 = Mux(sel === IMM_U, inst(30, 20).asSInt, sign)
    val b19_12 = Mux(sel =/= IMM_U && sel =/= IMM_UJ, sign, inst(19, 12).asSInt)
    val b11    = Mux(
      sel === IMM_U || sel === IMM_Z,
      0.S,
      Mux(sel === IMM_UJ, inst(20).asSInt, Mux(sel === IMM_SB, inst(7).asSInt, sign))
    )
    val b10_5  = Mux(sel === IMM_U || sel === IMM_Z, 0.U, inst(30, 25))
    val b4_1   = Mux(
      sel === IMM_U,
      0.U,
      Mux(sel === IMM_S || sel === IMM_SB, inst(11, 8), Mux(sel === IMM_Z, inst(19, 16), inst(24, 21)))
    )
    val b0     = Mux(sel === IMM_S, inst(7), Mux(sel === IMM_I, inst(20), Mux(sel === IMM_Z, inst(15), 0.U)))

    Cat(sign, b30_20, b19_12, b11, b10_5, b4_1, b0).asSInt
  }
}
