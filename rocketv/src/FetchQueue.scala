// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.instantiable
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._

object FetchQueueParameter {
  implicit def rwP: upickle.default.ReadWriter[FetchQueueParameter] = upickle.default.macroRW[FetchQueueParameter]
}

case class FetchQueueParameter(
  useAsyncReset:     Boolean,
  entries:           Int,
  vaddrBits:         Int,
  respEntries:       Int,
  bhtHistoryLength:  Option[Int],
  bhtCounterLength:  Option[Int],
  vaddrBitsExtended: Int,
  coreInstBits:      Int,
  fetchWidth:        Int)
    extends SerializableModuleParameter {
  def gen = new FrontendResp(
    vaddrBits,
    respEntries,
    bhtHistoryLength,
    bhtCounterLength,
    vaddrBitsExtended,
    coreInstBits,
    fetchWidth
  )
}

class FetchQueueInterface(parameter: FetchQueueParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val enq = Flipped(Decoupled(parameter.gen))
  val deq = Decoupled(parameter.gen)
  val mask = Output(UInt(parameter.entries.W))
}

@instantiable
class FetchQueue(val parameter: FetchQueueParameter)
    extends FixedIORawModule(new FetchQueueInterface(parameter))
    with SerializableModule[FetchQueueParameter]
    with Public
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  private val valid = RegInit(VecInit(Seq.fill(parameter.entries) { false.B }))
  private val elts = Reg(Vec(parameter.entries, parameter.gen))

  for (i <- 0 until parameter.entries) {
    def paddedValid(i: Int) = if (i == -1) true.B else if (i == parameter.entries) false.B else valid(i)

    val flow = true
    val wdata = if (i == parameter.entries - 1) io.enq.bits else Mux(valid(i + 1), elts(i + 1), io.enq.bits)
    val wen =
      Mux(
        io.deq.ready,
        paddedValid(i + 1) || io.enq.fire && valid(i),
        io.enq.fire && paddedValid(i - 1) && !valid(i)
      )
    when(wen) { elts(i) := wdata }

    valid(i) :=
      Mux(
        io.deq.ready,
        paddedValid(i + 1) || io.enq.fire && ((i == 0 && !flow).B || valid(i)),
        io.enq.fire && paddedValid(i - 1) || valid(i)
      )
  }

  io.enq.ready := !valid(parameter.entries - 1)
  io.deq.valid := valid(0)
  io.deq.bits := elts.head

  when(io.enq.valid) { io.deq.valid := true.B }
  when(!valid(0)) { io.deq.bits := io.enq.bits }

  io.mask := valid.asUInt
}
