package v.rocket

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket._
import freechips.rocketchip.util._

import v._
import tilelink.TLBundle

class VectorLong(implicit p: Parameters) extends freechips.rocketchip.tile.Vector {
  override lazy val module = new VectorLongModuleImp(this)

  val vParam = new VParameter(
    xLen = p(XLen),
    vLen = p(VLen),
    datapathWidth = p(XLen), // TODO
    laneNumber = 8,
    physicalAddressWidth = p(XLen), // FIXME
    chainingSize = 4,
    vrfWriteQueueSize = 4
  )

  val tlBankNodes = Seq.tabulate(vParam.memoryBankSize)( i => {
    val tlBankNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
      name = s"Vector_$i",
      sourceId = IdRange(0, 1 << vParam.sourceWidth),
      nodePath = Seq(),
      requestFifo = false,
      // TODO: each bank should have a mask bit
      // transfer size now from diplomacy
      visibility = Seq(AddressSet(0, ~0)),
      supportsProbe = TransferSizes.none,
      supportsArithmetic = TransferSizes.none,
      supportsLogical = TransferSizes.none,
      supportsGet = TransferSizes.none,
      supportsPutFull = TransferSizes.none,
      supportsPutPartial = TransferSizes.none,
      supportsHint = TransferSizes.none,
    )))))
    tlNode := tlBankNode
    tlBankNode
  })
}

class VectorLongModuleImp(outer: VectorLong)(implicit p: Parameters)
    extends freechips.rocketchip.tile.VectorModuleImp(outer) {

  val v = Module(new VectorWrapper(outer.vParam))

  v.request.valid := io.cmd.valid
  io.cmd.ready := v.request.ready
  v.request.bits.instruction := io.cmd.bits.inst
  v.request.bits.src1Data := io.cmd.bits.rs1
  v.request.bits.src2Data := io.cmd.bits.rs2

  io.resp.valid := v.response.valid
  io.resp.bits.rd := Mux(v.response.bits.rd.valid, v.response.bits.rd.bits, 0.U)
  io.resp.bits.data := v.response.bits.data
  io.resp.bits.mem := v.response.bits.mem
  io.resp.bits.vxsat := v.response.bits.vxsat

  v.storeBufferClear := false.B
  // FIXME on UInt width
  v.csrInterface.vl := io.cmd.bits.vconfig.vl 
  v.csrInterface.vStart := io.cmd.bits.vstart // FIXME
  v.csrInterface.vlmul := io.cmd.bits.vconfig.vtype.vlmul_signed.asUInt // FIXME
  v.csrInterface.vSew := io.cmd.bits.vconfig.vtype.vsew // FIXME
  v.csrInterface.vxrm := io.cmd.bits.vxrm
  v.csrInterface.vta := io.cmd.bits.vconfig.vtype.vta
  v.csrInterface.vma := io.cmd.bits.vconfig.vtype.vma
  v.csrInterface.ignoreException := false.B // FIXME

  outer.tlBankNodes.zip(v.memoryPorts).map({ case (tl_node, mem) =>
    val (tl_out, _) = tl_node.out(0)
    // dangerous connection
    //(tl_out: Data).waiveAll :<>= (mem: Data).waiveAll
    (tl_out.a: Data).waiveAll :<>= (mem.a: Data).waiveAll
    // f*ck connectable
    mem.d.valid := tl_out.d.valid
    tl_out.d.ready := mem.d.ready
    mem.d.bits.opcode := tl_out.d.bits.opcode
    mem.d.bits.param := tl_out.d.bits.param
    // note the squeeze
    // diplomacy and vector has different ways of computing sizeWidth
    mem.d.bits.size := tl_out.d.bits.size // .squeeze
    mem.d.bits.source := tl_out.d.bits.source
    mem.d.bits.sink := tl_out.d.bits.sink
    mem.d.bits.denied := tl_out.d.bits.denied
    mem.d.bits.data := tl_out.d.bits.data
    mem.d.bits.corrupt := tl_out.d.bits.corrupt
  })

  when (io.cmd.fire()) {
    printf("Vector (%x)\n", io.cmd.bits.inst.asUInt)
  }

  when(io.resp.valid) {
    printf("Vector Commit, mem %x, rd[%x] %x\n", v.response.bits.mem, Mux(v.response.bits.rd.valid, v.response.bits.rd.bits, 0.U), v.response.bits.data)
  }

  //io.busy := !v.request.ready
  //io.interrupt := false.B
  //io.mem.req.valid := false.B
}

class WithVectorLong extends Config((site, here, up) => {
  case BuildVector => Some(
    (p: Parameters) => {
        val vector = LazyModule(new VectorLong()(p))
        vector
    })
})
