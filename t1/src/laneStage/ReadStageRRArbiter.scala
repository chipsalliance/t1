// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.util.{Decoupled, Mux1H}

class ArbiterIO[T <: Data](gen: T, n: Int) extends Bundle {
  val in  = Flipped(Vec(n, Decoupled(gen)))
  val out = Decoupled(gen)
}

class ReadStageRRArbiter[T <: Data](val gen: T, val n: Int) extends Module {
  require(n <= 2, "Only 2 were needed, so only 2 were made.")
  val io = IO(new ArbiterIO(gen, n))
  if (n == 2) {
    // Choose the first one first
    val choseMask = RegInit(true.B)
    val select: Vec[Bool] = VecInit(
      Seq(
        io.in.head.valid && (choseMask || !io.in.last.valid),
        io.in.last.valid && (!choseMask || !io.in.head.valid)
      )
    )
    io.out.valid := io.in.map(_.valid).reduce(_ || _)
    io.out.bits  := Mux1H(select, io.in.map(_.bits))
    io.in.zip(select).foreach { case (in, s) =>
      in.ready := s && io.out.ready
    }
    when(io.out.fire) {
      choseMask := io.in.last.fire
    }
  } else {
    io.out <> io.in.head
  }
}
