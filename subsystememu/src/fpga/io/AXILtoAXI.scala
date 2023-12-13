package verdes.fpga.io

import chisel3._
import chisel3.util._
import freechips.rocketchip.amba.axi4._

class AXILtoAXI(val idWidth: Int = 4) extends Module {
  private val axi4Params = new AXI4BundleParameters(32, 32, idWidth)
  private val axilParams = new AXILiteBundleParameters(32, 32, hasProt = true)
  val io = IO(new Bundle {
    val axi = Flipped(AXI4Bundle(axi4Params))
    val axil = new AXILiteBundle(axilParams)
  })

  // TODO: protect burst length by tracking the inflight transactions and returns error when AxLEN is not zero

  val ridQueue = Module(new Queue(UInt(idWidth.W), 4))
  val bidQueue = Module(new Queue(UInt(idWidth.W), 4))
  // AR
  io.axil.ar.bits.addr := io.axi.ar.bits.addr
  io.axil.ar.bits.prot := io.axi.ar.bits.prot
  io.axil.ar.valid := io.axi.ar.valid && ridQueue.io.enq.ready
  io.axi.ar.ready := io.axil.ar.ready && ridQueue.io.enq.ready
  ridQueue.io.enq.bits := io.axi.ar.bits.id
  ridQueue.io.enq.valid := io.axi.ar.fire
  // R
  io.axi.r.bits.id := ridQueue.io.deq.bits
  io.axi.r.bits.data := io.axil.r.bits.data
  io.axi.r.bits.resp := io.axil.r.bits.resp
  io.axi.r.bits.last := true.B
  io.axi.r.valid := io.axil.r.valid
  io.axil.r.ready := io.axi.r.ready
  ridQueue.io.deq.ready := io.axi.r.fire
  // AW
  io.axil.aw.bits.addr := io.axi.aw.bits.addr
  io.axil.aw.bits.prot := io.axi.aw.bits.prot
  io.axil.aw.valid := io.axi.aw.valid && bidQueue.io.enq.ready
  io.axi.aw.ready := io.axil.aw.ready && bidQueue.io.enq.ready
  bidQueue.io.enq.bits := io.axi.aw.bits.id
  bidQueue.io.enq.valid := io.axi.aw.fire
  // W
  io.axil.w.bits.data := io.axi.w.bits.data
  io.axil.w.bits.strb := io.axi.w.bits.strb
  io.axil.w.valid := io.axi.w.valid
  io.axi.w.ready := io.axil.w.ready
  // B
  io.axi.b.bits.id := bidQueue.io.deq.bits
  io.axi.b.bits.resp := io.axil.b.bits.resp
  io.axi.b.valid := io.axil.b.valid
  io.axil.b.ready := io.axi.b.ready
  bidQueue.io.deq.ready := io.axi.b.fire
}
