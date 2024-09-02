// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.vfu

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Instantiate}
import chisel3.util._

@instantiable
class VectorMultiplier32Signed extends Module {
  @public
  val a      = IO(Input(UInt(32.W)))
  @public
  val b      = IO(Input(UInt(32.W)))
  @public
  val z      = IO(Output(UInt(64.W)))
  @public
  val sew    = IO(Input(UInt(3.W)))
  @public
  val unsign = IO(Input(Bool()))

  val aAbs = Abs32(a, sew)
  val bAbs = Abs32(b, sew)

  val zSign = (a(31) ^ b(31)) ## (a(23) ^ b(23)) ## (a(15) ^ b(15)) ## (a(7) ^ b(7))

  val Mul = Instantiate(new VectorMultiplier32Unsigned)
  Mul.a   := Mux(unsign, a, aAbs)
  Mul.b   := Mux(unsign, b, bAbs)
  Mul.sew := sew

  val doComplement = Mux1H(
    Seq(
      sew(0) -> zSign(3) ## zSign(2) ## zSign(1) ## zSign(0),
      sew(1) -> zSign(3) ## zSign(3) ## zSign(1) ## zSign(1),
      sew(2) -> zSign(3) ## zSign(3) ## zSign(3) ## zSign(3)
    )
  )

  val complementResult = Complement64(Mul.z, doComplement, sew)
  z := Mux(unsign, Mul.z, complementResult)
}
