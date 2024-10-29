package org.chipsalliance.dwbb.stdlib.queue

import chisel3._
import chisel3.experimental.hierarchy.Instantiate
import org.chipsalliance.dwbb.wrapper.DW_fifo_s1_sf.{DW_fifo_s1_sf => DwbbFifo}
import org.chipsalliance.dwbb.interface.DW_fifo_s1_sf.{Interface => DwbbFifoInterface, Parameter => DwbbFifoParameter}
import chisel3.ltl.AssertProperty
import chisel3.util.{Decoupled, DecoupledIO, DeqIO, EnqIO, ReadyValidIO}

class QueueIO[T <: Data](private val gen: T) extends Bundle {
  val enq = Flipped(EnqIO(gen))
  val deq = Flipped(DeqIO(gen))

  val empty = Output(Bool())
  val full  = Output(Bool())
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
    gen:      T,
    entries:  Int = 2,
    pipe:     Boolean = false,
    flow:     Boolean = false,
    resetMem: Boolean = false
  ): QueueIO[T] = {
    require(gen.getWidth <= 2048, "Data width must be less than 2048")
    require(entries <= 1024, "Entries must be less than 1024")

    val clock = Module.clock
    val reset = Module.reset

    val io = Wire(new QueueIO(gen))

    if (entries < 2) {
      val queue = Module(new chisel3.util.Queue(gen, entries, pipe, flow))
      queue.io.enq <> io.enq
      queue.io.deq <> io.deq
      io.empty := queue.io.count === 0.U
      io.full  := queue.io.count === entries.U

      return io
    }

    // TODO: use sync reset for now and wait for t1 to migrate to FixedIOModule
    // require(reset.typeName == "Bool" || reset.typeName == "AsyncReset")
    val useAsyncReset = reset.typeName == "AsyncReset"

    val fifo = Instantiate(
      new DwbbFifo(
        new DwbbFifoParameter(
          width = gen.getWidth,
          depth = entries,
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

    io.empty := fifo.io.empty
    io.full  := fifo.io.full

    // There should be no error since we guarantee to push/pop items only when the fifo is neither empty nor full.
    AssertProperty(!fifo.io.error)

    io
  }
}
