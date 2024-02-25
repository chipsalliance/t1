// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package org.chipsalliance.t1.rocketcore

import chisel3._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.prci.{ClockGroup, ClockSinkParameters, NoResetCrossing, ResetCrossingType}
import freechips.rocketchip.subsystem.{Attachable, CBUS, CanAttachTile, HierarchicalElementCrossingParamsLike, HierarchicalElementMasterPortParams, HierarchicalElementPortParamsLike, HierarchicalElementSlavePortParams, RocketCrossingParams, TLBusWrapperLocation}
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import org.chipsalliance.cde.config._
import org.chipsalliance.t1.rockettile.{HasLazyT1, HasLazyT1Module}
// TODO: remove it.
import freechips.rocketchip.rocket.{BTBParams, DCacheParams, ICacheParams, RocketCoreParams}

case class RocketTileBoundaryBufferParams(force: Boolean = false)

case class T1CrossingParams(
                             crossingType: ClockCrossingType,
                             master: HierarchicalElementPortParamsLike,
                             vectorMaster: HierarchicalElementPortParamsLike,
                             slave: HierarchicalElementSlavePortParams,
                             mmioBaseAddressPrefixWhere: TLBusWrapperLocation,
                             resetCrossingType: ResetCrossingType,
                             forceSeparateClockReset: Boolean
                           ) extends HierarchicalElementCrossingParamsLike

case class T1TileAttachParams(
  tileParams:     T1TileParams,
  crossingParams: T1CrossingParams)
    extends CanAttachTile {
  type TileType = T1Tile
  /** Connect power/reset/clock resources. */
  override def connectPRC(domain: TilePRCIDomain[TileType], context: TileContextType): Unit = {
    implicit val p = context.p
    val tlBusToGetClockDriverFrom = context.locateTLBusWrapper(crossingParams.master.where)
    crossingParams.crossingType match {
      case _: SynchronousCrossing | _: CreditedCrossing =>
        if (crossingParams.forceSeparateClockReset) {
          domain.clockNode := tlBusToGetClockDriverFrom.clockNode
        } else {
          domain.clockNode := tlBusToGetClockDriverFrom.fixedClockNode
        }
      case _: RationalCrossing => domain.clockNode := tlBusToGetClockDriverFrom.clockNode
      case _: AsynchronousCrossing =>
        val tileClockGroup = ClockGroup()
        tileClockGroup := context.allClockGroupsNode
        domain.clockNode := tileClockGroup
    }

    val vectorBusToGetClockDriverFrom = context.locateTLBusWrapper(crossingParams.vectorMaster.where)

    domain {
      domain.element_reset_domain.clockNode := crossingParams.resetCrossingType.injectClockNode := domain.clockNode
    }
  }

  /** Connect the port where the tile is the master to a TileLink interconnect. */
  override def connectMasterPorts(domain: TilePRCIDomain[TileType], context: Attachable): Unit = {
    super.connectMasterPorts(domain, context)
    implicit val p = context.p
    val vectorBus = context.locateTLBusWrapper(crossingParams.vectorMaster.where)
    vectorBus.coupleFrom(tileParams.baseName) { bus: TLInwardNode =>
      // TODO: add clock crossing here.
      domain.element.t1.foreach(bus :=* crossingParams.vectorMaster.injectNode(context) :=* _.t1LSUNode)
    }
  }
}

case class T1TileParams(
  core:                RocketCoreParams = RocketCoreParams(),
  icache:              Option[ICacheParams] = Some(ICacheParams()),
  dcache:              Option[DCacheParams] = Some(DCacheParams()),
  btb:                 Option[BTBParams] = Some(BTBParams()),
  dataScratchpadBytes: Int = 0,
  name:                Option[String] = Some("T1"),
  tileId:              Int = 0,
  beuAddr:             Option[BigInt] = None,
  blockerCtrlAddr:     Option[BigInt] = None,
  clockSinkParams:     ClockSinkParameters = ClockSinkParameters(),
  boundaryBuffers:     Option[RocketTileBoundaryBufferParams] = None)
    extends InstantiableTileParams[T1Tile] {
  require(icache.isDefined)
  require(dcache.isDefined)
  val baseName = "t1tile"
  val uniqueName = s"${baseName}_$tileId"
  def instantiate(
    crossing: HierarchicalElementCrossingParamsLike,
    lookup:   LookupByHartIdImpl
  )(
    implicit p: Parameters
  ): T1Tile = {
    new T1Tile(this, crossing, lookup)
  }
}

class T1Tile private(
  val rocketParams: T1TileParams,
  crossing:         ClockCrossingType,
  lookup:           LookupByHartIdImpl,
  q:                Parameters)
    extends BaseTile(rocketParams, crossing, lookup, q)
    with SinksExternalInterrupts
    with SourcesExternalNotifications
    with HasHellaCache
    with HasLazyT1
    with HasICacheFrontend {
  // Private constructor ensures altered LazyModule.p is used implicitly
  def this(
    params:   T1TileParams,
    crossing: HierarchicalElementCrossingParamsLike,
    lookup:   LookupByHartIdImpl
  )(
    implicit p: Parameters
  ) =
    this(params, crossing.crossingType, lookup, p)

  val intOutwardNode = rocketParams.beuAddr.map { _ => IntIdentityNode() }
  val slaveNode = TLIdentityNode()
  val masterNode = visibilityNode

  val dtim_adapter = tileParams.dcache.flatMap { d =>
    d.scratch.map { s =>
      LazyModule(
        new ScratchpadSlavePort(
          AddressSet.misaligned(s, d.dataScratchpadBytes),
          lazyCoreParamsView.coreDataBytes,
          tileParams.core.useAtomics && !tileParams.core.useAtomicsOnlyForIO
        )
      )
    }
  }
  dtim_adapter.foreach(lm => connectTLSlave(lm.node, lm.node.portParams.head.beatBytes))

  val bus_error_unit = rocketParams.beuAddr.map { a =>
    val beu = LazyModule(new BusErrorUnit(new L1BusErrors, BusErrorUnitParams(a)))
    intOutwardNode.get := beu.intNode
    connectTLSlave(beu.node, xBytes)
    beu
  }

  val tile_master_blocker =
    tileParams.blockerCtrlAddr
      .map(BasicBusBlockerParams(_, xBytes, masterPortBeatBytes, deadlock = true))
      .map(bp => LazyModule(new BasicBusBlocker(bp)))

  tile_master_blocker.foreach(lm => connectTLSlave(lm.controlNode, xBytes))

  tlOtherMastersNode := tile_master_blocker.map { _.node := tlMasterXbar.node }.getOrElse { tlMasterXbar.node }
  masterNode :=* tlOtherMastersNode
  DisableMonitors { implicit p => tlSlaveXbar.node :*= slaveNode }

  nDCachePorts += 1 /*core */ + (dtim_adapter.isDefined).toInt

  val dtimProperty = dtim_adapter.map(d => Map("sifive,dtim" -> d.device.asProperty)).getOrElse(Nil)

  val itimProperty = frontend.icache.itimProperty.toSeq.flatMap(p => Map("sifive,itim" -> p))

  val beuProperty = bus_error_unit.map(d => Map("sifive,buserror" -> d.device.asProperty)).getOrElse(Nil)

  val cpuDevice: SimpleDevice = new SimpleDevice("cpu", Seq("sifive,rocket0", "riscv")) {
    override def parent = Some(ResourceAnchors.cpus)
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(
        name,
        mapping ++ cpuProperties ++ nextLevelCacheProperty
          ++ tileProperties ++ dtimProperty ++ itimProperty ++ beuProperty
      )
    }
  }

  ResourceBinding {
    Resource(cpuDevice, "reg").bind(ResourceAddress(tileId))
  }

  override lazy val module = new RocketTileModuleImp(this)

  override def makeMasterBoundaryBuffers(crossing: ClockCrossingType)(implicit p: Parameters) =
    (rocketParams.boundaryBuffers, crossing) match {
      case (Some(RocketTileBoundaryBufferParams(true)), _) => TLBuffer()
      case (Some(RocketTileBoundaryBufferParams(false)), _: RationalCrossing) =>
        TLBuffer(BufferParams.none, BufferParams.flow, BufferParams.none, BufferParams.flow, BufferParams(1))
      case _ => TLBuffer(BufferParams.none)
    }

  override def makeSlaveBoundaryBuffers(crossing: ClockCrossingType)(implicit p: Parameters) =
    (rocketParams.boundaryBuffers, crossing) match {
      case (Some(RocketTileBoundaryBufferParams(true)), _) => TLBuffer()
      case (Some(RocketTileBoundaryBufferParams(false)), _: RationalCrossing) =>
        TLBuffer(BufferParams.flow, BufferParams.none, BufferParams.none, BufferParams.none, BufferParams.none)
      case _ => TLBuffer(BufferParams.none)
    }
}

class RocketTileModuleImp(outer: T1Tile)
    extends BaseTileModuleImp(outer)
    with HasFpuOpt
    with HasLazyT1Module
    with HasICacheFrontendModule {
  Annotated.params(this, outer.rocketParams)

  lazy val core = Module(new Rocket(outer.dcache.flushOnFenceI, outer.bus_error_unit.isDefined)(outer.p))

  // Report unrecoverable error conditions; for now the only cause is cache ECC errors
  outer.reportHalt(List(outer.dcache.module.io.errors))

  // Report when the tile has ceased to retire instructions; for now the only cause is clock gating
  outer.reportCease(
    outer.rocketParams.core.clockGate.option(
      !outer.dcache.module.io.cpu.clock_enabled &&
        !outer.frontend.module.io.cpu.clock_enabled &&
        !ptw.io.dpath.clock_enabled &&
        core.cease
    )
  )

  outer.reportWFI(Some(core.wfi))

  outer.decodeCoreInterrupts(core.interrupts) // Decode the interrupt vector

  outer.bus_error_unit.foreach { beu =>
    core.interrupts.buserror.get := beu.module.io.interrupt
    beu.module.io.errors.dcache := outer.dcache.module.io.errors
    beu.module.io.errors.icache := outer.frontend.module.io.errors
  }

  core.interrupts.nmi.foreach { nmi => nmi := outer.nmiSinkNode.get.bundle }

  // Pass through various external constants and reports that were bundle-bridged into the tile
  core.traceStall := outer.traceAuxSinkNode.bundle.stall
  outer.bpwatchSourceNode.bundle <> core.bpwatch
  core.hartid := outer.hartIdSinkNode.bundle
  require(
    core.hartid.getWidth >= outer.hartIdSinkNode.bundle.getWidth,
    s"core hartid wire (${core.hartid.getWidth}b) truncates external hartid wire (${outer.hartIdSinkNode.bundle.getWidth}b)"
  )

  // Connect the core pipeline to other intra-tile modules
  outer.frontend.module.io.cpu <> core.imem
  dcachePorts += core.dmem // TODO outer.dcachePorts += () => module.core.dmem ??
  fpuOpt.zip(core.fpu).foreach {
    case (fpu, core) =>
      core :<>= fpu.io.waiveAs[FPUCoreIO](_.cp_req, _.cp_resp)
      fpu.io.cp_req := DontCare
      fpu.io.cp_resp := DontCare
  }
  core.ptw <> ptw.io.dpath

  // Rocket has higher priority to DTIM than other TileLink clients
  outer.dtim_adapter.foreach { lm => dcachePorts += lm.module.io.dmem }

  // TODO eliminate this redundancy
  val h = dcachePorts.size
  val c = core.dcacheArbPorts
  val o = outer.nDCachePorts
  require(h == c, s"port list size was $h, core expected $c")
  require(h == o, s"port list size was $h, outer counted $o")
  // TODO figure out how to move the below into their respective mix-ins
  dcacheArb.io.requestor <> dcachePorts.toSeq
  ptw.io.requestor <> ptwPorts.toSeq
}

trait HasFpuOpt { this: RocketTileModuleImp =>
  val fpuOpt = outer.tileParams.core.fpu.map(params => Module(new FPU(params)(outer.p)))
}
