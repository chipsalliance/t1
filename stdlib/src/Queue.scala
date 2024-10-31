package org.chipsalliance.dwbb.stdlib.queue

import chisel3._
import chisel3.experimental.hierarchy.Instantiate
import org.chipsalliance.dwbb.wrapper.DW_fifo_s1_sf.{DW_fifo_s1_sf => DwbbFifo}
import org.chipsalliance.dwbb.interface.DW_fifo_s1_sf.{Interface => DwbbFifoInterface, Parameter => DwbbFifoParameter}
import chisel3.ltl.AssertProperty
import chisel3.util.{Decoupled, DecoupledIO, DeqIO, EnqIO, ReadyValidIO}

class QueueIO[T <: Data](private val gen: T, entries: Int) extends Bundle {
  val enq = Flipped(EnqIO(gen))
  val deq = Flipped(DeqIO(gen))

  val empty       = Output(Bool())
  val full        = Output(Bool())
  val almostEmpty = if (entries >= 2) Some(Output(Bool())) else None
  val almostFull  = if (entries >= 2) Some(Output(Bool())) else None
}

object Queue {
  def apply[T <: Data](
    enq:      ReadyValidIO[T],
    entries:  Int,
    pipe:     Boolean = false,
    flow:     Boolean = false,
    resetMem: Boolean = false
  ): DecoupledIO[T] = {
    val io = this.io(chiselTypeOf(enq.bits), entries, pipe, flow, resetMem)
    io.enq <> enq

    io.deq
  }

  def io[T <: Data](
    gen:              T,
    entries:          Int = 2,
    pipe:             Boolean = false,
    flow:             Boolean = false,
    resetMem:         Boolean = false,
    almostEmptyLevel: Int = 1,
    almostFullLevel:  Int = 1
  ): QueueIO[T] = {
    require(
      Range.inclusive(1, 2048).contains(gen.getWidth),
      "Data width must be between 1 and 2048"
    )
    require(
      Range.inclusive(1, 1024).contains(entries),
      "Entries must be between 1 and 1024"
    )

    val io = Wire(new QueueIO(gen, entries))

    if (entries == 1) {
      val data  = if (resetMem) RegInit(0.U.asTypeOf(gen)) else Reg(gen)
      val empty = RegInit(true.B)
      val full  = !empty

      val push = io.enq.fire && (if (flow) !(empty && io.deq.ready) else true.B)
      io.enq.ready := empty || (if (pipe) io.deq.ready else false.B)
      data         := Mux(push, io.enq.bits, data)

      val pop = io.deq.ready && full
      io.deq.valid := full || (if (flow) io.enq.valid else false.B)
      io.deq.bits  := (if (flow) Mux(empty, io.enq.bits, data) else data)

      empty := Mux(push =/= pop, pop, empty)

      io.empty := empty
      io.full  := full
    } else {
      require(
        Range.inclusive(1, entries - 1).contains(almostEmptyLevel),
        "almost empty level must be between 1 and entries-1"
      )
      require(
        Range.inclusive(1, entries - 1).contains(almostFullLevel),
        "almost full level must be between 1 and entries-1"
      )

      val clock = Module.clock
      val reset = Module.reset

      // TODO: use sync reset for now and wait for t1 to migrate to FixedIOModule
      // require(reset.typeName == "Bool" || reset.typeName == "AsyncReset")
      val useAsyncReset = reset.typeName == "AsyncReset"

      val fifo = Instantiate(
        new DwbbFifo(
          new DwbbFifoParameter(
            width = gen.getWidth,
            depth = entries,
            aeLevel = almostEmptyLevel,
            afLevel = almostFullLevel,
            errMode = "unlatched",
            rstMode = (useAsyncReset, resetMem) match {
              case (false, false) => "sync_wo_mem"
              case (false, true)  => "sync_with_mem"
              case (true, false)  => "async_wo_mem"
              case (true, true)   => "async_with_mem"
            }
          )
        )
      )

      val dataIn  = io.enq.bits.asUInt
      val dataOut = fifo.io.data_out.asTypeOf(io.deq.bits)

      fifo.io.clk   := clock
      fifo.io.rst_n := ~(reset.asBool)

      fifo.io.diag_n := ~(false.B)

      io.enq.ready       := !fifo.io.full || (if (pipe) io.deq.ready else false.B)
      fifo.io.push_req_n := ~(io.enq.fire && (if (flow) !(fifo.io.empty && io.deq.ready) else true.B))
      fifo.io.data_in    := dataIn

      io.deq.valid      := !fifo.io.empty || (if (flow) io.enq.valid else false.B)
      fifo.io.pop_req_n := ~(io.deq.ready && !fifo.io.empty)
      io.deq.bits       := (if (flow) Mux(fifo.io.empty, io.enq.bits, dataOut) else dataOut)

      io.empty           := fifo.io.empty
      io.full            := fifo.io.full
      io.almostEmpty.get := fifo.io.almost_empty
      io.almostFull.get  := fifo.io.almost_full

      // There should be no error since we guarantee to push/pop items only when the fifo is neither empty nor full.
      AssertProperty(!fifo.io.error)
    }

    io
  }
}
