package verdes

import chisel3._
import freechips.rocketchip.amba.AMBACorrupt
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._
import org.chipsalliance.cde.config.Parameters

class LazyAXI4MemBFM(edge: AXI4EdgeParameters, size: BigInt, base: BigInt = 0)(implicit p: Parameters) extends SimpleLazyModule {
  val node = AXI4MasterNode(List(edge.master))
  val srams = AddressSet.misaligned(base, size).map { aSet =>
    LazyModule(new AXI4BFM(
      address = aSet,
      beatBytes = edge.bundle.dataBits/8,
      wcorrupt=edge.slave.requestKeys.contains(AMBACorrupt)))
  }
  val xbar = AXI4Xbar()
  srams.foreach{ s => s.node := AXI4Buffer() := AXI4Fragmenter() := xbar }
  xbar := node
  val io_axi4 = InModuleBody { node.makeIOs() }
}

class AXI4BFM(address: AddressSet,
              cacheable: Boolean = true,
              executable: Boolean = true,
              beatBytes: Int = 4,
              errors: Seq[AddressSet] = Nil,
              wcorrupt: Boolean = true
             )(implicit p: Parameters) extends LazyModule
{ outer =>
  val node = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    Seq(AXI4SlaveParameters(
      address       = List(address) ++ errors,
      resources     = Nil,
      regionType    = if (cacheable) RegionType.UNCACHED else RegionType.IDEMPOTENT,
      executable    = executable,
      supportsRead  = TransferSizes(1, beatBytes),
      supportsWrite = TransferSizes(1, beatBytes),
      interleavedId = Some(0))),
    beatBytes  = beatBytes,
    requestKeys = if (wcorrupt) Seq(AMBACorrupt) else Seq(),
    minLatency = 1)))

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val axi4Bundle: AXI4Bundle = node.in.head._1

    val dpiGen = Module(new DPIModule {
      val bundleTpe = axi4Bundle.cloneType
      override def desiredName = "AXI4BFMDPI"
      override val isImport: Boolean = true
      val clock = dpiTrigger("clock", Input(Bool()))
      val reset = dpiTrigger("reset", Input(Bool()))
      override val trigger: String = s"always@(negedge ${clock.name})"
      override val guard: String = s"!${reset.name}"

      // TODO: these must use dpi
      bundleTpe.aw.bits
      bundleTpe.w.bits
      bundleTpe.b.bits
      bundleTpe.ar.bits
      bundleTpe.r.bits

      bundleTpe.aw.valid
      bundleTpe.w.valid
      bundleTpe.b.valid
      bundleTpe.ar.valid
      bundleTpe.r.valid

      bundleTpe.aw.ready
      bundleTpe.w.ready
      bundleTpe.b.ready
      bundleTpe.ar.ready
      bundleTpe.r.ready

    })
    dpiGen.clock.ref := clock.asBool
    dpiGen.reset.ref := reset
    axi4Bundle := DontCare
    dontTouch(axi4Bundle)
  }
}