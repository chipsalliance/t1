// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.subsystem

import chisel3._
import chisel3.experimental.{SerializableModuleGenerator, UnlocatableSourceInfo}
import chisel3.util.BitPat
import chisel3.util.experimental.BitSet
import freechips.rocketchip.amba.axi4.{AXI4IdIndexer, AXI4SlaveNode, AXI4SlaveParameters, AXI4SlavePortParameters, AXI4UserYanker, AXI4Xbar}
import freechips.rocketchip.devices.debug.{DebugModuleKey, TLDebugModule}
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci.{ClockBundle, ClockDomain, ClockGroup, ClockGroupAggregator, ClockGroupSourceNode, ClockGroupSourceParameters, ClockSourceNode, ClockSourceParameters, FixedClockBroadcast, FixedClockBroadcastNode, NoResetCrossing, SimpleClockGroupSource}
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem.CoherenceManagerWrapper.CoherenceManagerInstantiationFn
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tile.{MaxHartIdBits, XLen}
import freechips.rocketchip.tilelink.{HasTLBusParams, RegionReplicator, ReplicatedRegion, TLBroadcast, TLBusWrapper, TLBusWrapperConnection, TLBusWrapperInstantiationLike, TLBusWrapperTopology, TLCacheCork, TLClientNode, TLEdge, TLFilter, TLInwardNode, TLManagerNode, TLMasterParameters, TLMasterPortParameters, TLOutwardNode, TLSlaveParameters, TLSlavePortParameters, TLSourceShrinker, TLToAXI4, TLWidthWidget, TLXbar}
import freechips.rocketchip.util.{DontTouch, Location, RecordMap}
import org.chipsalliance.cde.config._
import org.chipsalliance.t1.rocketcore.{T1CrossingParams, T1TileAttachParams, T1TileParams}
import org.chipsalliance.t1.rockettile.BuildT1
import org.chipsalliance.t1.rtl.{T1, T1Parameter}

// The Subsystem that T1 lives in.
case object T1Subsystem extends HierarchicalLocation("T1Subsystem")
case object ScalarMasterBus extends TLBusWrapperLocation("ScalarMaster")
case class ScalarMasterBusParameters(beatBytes: Int, blockBytes: Int)
    extends HasTLBusParams
    with TLBusWrapperInstantiationLike {
  def dtsFrequency: Option[BigInt] = None
  override def instantiate(
    context: HasTileLinkLocations,
    loc:     Location[TLBusWrapper]
  )(
    implicit p: Parameters
  ): TLBusWrapper = {
    val scalarMaster = LazyModule(new TLBusWrapper(this, "ScalarMasterBus") {
      private val xbar = LazyModule(new TLXbar())
      val inwardNode:  TLInwardNode = xbar.node
      val outwardNode: TLOutwardNode = xbar.node
      def busView:     TLEdge = xbar.node.edges.in.head

      override def prefixNode:     Option[BundleBridgeNode[UInt]] = None
      override def builtInDevices: BuiltInDevices = BuiltInDevices.none
    })
    context.tlBusWrapperLocationMap += (loc -> scalarMaster)
    scalarMaster
  }
}
case object ScalarControlBus extends TLBusWrapperLocation("ScalarControl")
case class ScalarControlBusParameters(beatBytes: Int, blockBytes: Int)
    extends HasTLBusParams
    with TLBusWrapperInstantiationLike {
  def dtsFrequency: Option[BigInt] = None
  def instantiate(
    context: HasTileLinkLocations,
    loc:     Location[TLBusWrapper]
  )(
    implicit p: Parameters
  ): TLBusWrapper = {
    val scalarControl = LazyModule(new TLBusWrapper(this, "ScalarControl") {
      private val xbar = LazyModule(new TLXbar())
      val inwardNode:  TLInwardNode = xbar.node
      val outwardNode: TLOutwardNode = xbar.node
      def busView:     TLEdge = xbar.node.edges.in.head

      override def prefixNode:     Option[BundleBridgeNode[UInt]] = None
      override def builtInDevices: BuiltInDevices = BuiltInDevices.none
    })
    context.tlBusWrapperLocationMap += (loc -> scalarControl)
    scalarControl
  }
}
case object VectorMasterBus extends TLBusWrapperLocation(s"Vector")
case class VectorMasterBusParameters(beatBytes: Int, blockBytes: Int)
    extends HasTLBusParams
    with TLBusWrapperInstantiationLike {
  def dtsFrequency: Option[BigInt] = None
  def instantiate(
    context: HasTileLinkLocations,
    loc:     Location[TLBusWrapper]
  )(
    implicit p: Parameters
  ): TLBusWrapper = {
    val vectorMaster = LazyModule(new TLBusWrapper(this, "VectorMaster") {
      private val xbar = LazyModule(new TLXbar())
      val inwardNode:  TLInwardNode = xbar.node
      val outwardNode: TLOutwardNode = xbar.node
      def busView:     TLEdge = xbar.node.edges.in.head

      override def prefixNode:     Option[BundleBridgeNode[UInt]] = None
      override def builtInDevices: BuiltInDevices = BuiltInDevices.none
    })
    context.tlBusWrapperLocationMap += (loc -> vectorMaster)
    vectorMaster
  }
}

// This Configuration is forced to be a hardcoded CDE config which read all configurable parameters from [[t1Generator]],
// Or RocketParameter in the future.
class T1SubsystemConfig(t1Generator: SerializableModuleGenerator[T1, T1Parameter])
    extends Config(
      new Config((site, here, up) => {
        case org.chipsalliance.t1.subsystem.T1Generator => t1Generator
        case BuildT1                                    => Some((p: Parameters) => LazyModule(new LazyT1()(p))(ValName("T1"), UnlocatableSourceInfo))
        case XLen                                       => 32
        case PgLevels                                   => 2
        case MaxHartIdBits                              => 1
        case DebugModuleKey                             => None
        case HasTilesExternalResetVectorKey             => true
        case TLManagerViewpointLocated(T1Subsystem)     => ScalarMasterBus
        // Don't Drive clock implicitly from IO, we create clock sources and attach our own clock node:
        // This will pave the road to Chisel Clock Domain API
        case SubsystemDriveClockGroupsFromIO => false
        case TilesLocated(T1Subsystem)       =>
          // Attach one core and
          List(
            T1TileAttachParams(
              T1TileParams(
                core = new RocketCoreParams(
                  haveSimTimeout = false,
                  useVM = false,
                  fpu = None,
                  mulDiv = Some(MulDivParams(mulUnroll = 8))
                ) {
                  // hot fix
                  override val useVector = true

                  override def vLen = t1Generator.parameter.vLen
                },
                btb = None,
                dcache = Some(
                  DCacheParams(
                    // TODO: align with ScalarMasterBus.beatBits
                    rowBits = 64,
                    nSets = 256,
                    nWays = 1,
                    nTLBSets = 1,
                    nTLBWays = 4,
                    nMSHRs = 0,
                    blockBytes = site(CacheBlockBytes),
                    scratch = None
                  )
                ),
                icache = Some(
                  ICacheParams(
                    rowBits = 64,
                    nSets = 64,
                    nWays = 1,
                    nTLBSets = 1,
                    nTLBWays = 4,
                    blockBytes = site(CacheBlockBytes)
                  )
                )
              ),
              T1CrossingParams(
                crossingType = SynchronousCrossing(params = BufferParams.default),
                master = HierarchicalElementMasterPortParams(
                  buffers = 0,
                  cork = None,
                  where = ScalarMasterBus
                ),
                vectorMaster = HierarchicalElementMasterPortParams(
                  buffers = 0,
                  cork = None,
                  where = VectorMasterBus
                ),
                slave = HierarchicalElementSlavePortParams(
                  buffers = 0,
                  blockerCtrlAddr = None,
                  blockerCtrlWhere = ScalarControlBus,
                  where = ScalarControlBus
                ),
                mmioBaseAddressPrefixWhere = ScalarControlBus,
                resetCrossingType = NoResetCrossing(),
                forceSeparateClockReset = false
              )
            )
          )
        case TLNetworkTopologyLocated(T1Subsystem) =>
          List(
            new TLBusWrapperTopology(
              instantiations = List(
                (ScalarMasterBus, ScalarMasterBusParameters(beatBytes = 8, blockBytes = 64)),
                (ScalarControlBus, ScalarControlBusParameters(beatBytes = 8, blockBytes = 64)),
                // beatBytes, blockBytes should be constraint to DLEN/VLEN:
                // beatBytes = DLEN, blockBytes = DLEN
                (VectorMasterBus, VectorMasterBusParameters(beatBytes = t1Generator.parameter.datapathWidth / 8, blockBytes = t1Generator.parameter.dLen / 8)),
                (COH, CoherenceManagerWrapperParams(blockBytes = 64, beatBytes = 8, 1, COH.name)(CoherenceManagerWrapper.broadcastManagerFn("broadcast", T1Subsystem, ScalarControlBus)))
              ),
              connections = Seq(
                (
                  ScalarMasterBus, ScalarControlBus,
                  TLBusWrapperConnection.crossTo(
                    xType = NoCrossing,
                    driveClockFromMaster = Some(true),
                    nodeBinding = BIND_STAR,
                    flipRendering = false
                  ),
                ),
                (
                  ScalarMasterBus, COH,
                  TLBusWrapperConnection.crossTo(
                    xType = NoCrossing,
                    driveClockFromMaster = Some(true),
                    nodeBinding = BIND_STAR,
                    flipRendering = false
                  )
                )
              )
            )
          )
      })
        .orElse(new WithClockGateModel("./dependencies/rocket-chip/src/vsrc/EICG_wrapper.v"))
        .orElse(new WithCacheBlockBytes(16))
        // SoC
        .orElse(new WithoutTLMonitors)
        // 1 MHz
        .orElse(new WithTimebase(BigInt(1000000)))
        .orElse(new WithDTS("chipsalliance,t1", Nil))
    )

class T1Subsystem(implicit p: Parameters)
    extends BareSubsystem
    // TODO: Remove [[HasDTS]] in the following PRs
    with HasDTS
    // implement ibus, clock domains
    with HasConfigurablePRCILocations
    // Provides [[locateTLBusWrapper]] and [[HasPRCILocations]]
    with Attachable
    // use [[TLNetworkTopologyLocated]] for configuration bus
    with HasConfigurableTLNetworkTopology
    // instantiate tiles
    with InstantiatesHierarchicalElements
    // give halt, wfi, cease
    with HasTileNotificationSinks
    // hartid, resetVector
    with HasTileInputConstants
    // Must to have?
    with HasHierarchicalElementsRootContext
    // Attach Tile to clockdomains
    with HasHierarchicalElements {
  lazy val module = new T1SubsystemModuleImp(this)
  val t1Parameter: T1Parameter = p(org.chipsalliance.t1.subsystem.T1Generator).parameter

  override lazy val location:       HierarchicalLocation = T1Subsystem
  override lazy val busContextName: String = "t1subsystem"

  // ClockDomains:
  val clockSource = ClockGroupSourceNode(Seq(ClockGroupSourceParameters()))
  // Expose clock to IO.
  allClockGroupsNode := ClockGroupAggregator() := clockSource

  // First clock is ScalarMaster Clock, it will drive ScalarControlClock.
  viewpointBus.clockGroupNode := allClockGroupsNode
  // TODO: should it located at control bus?
  ibus.clockNode := viewpointBus.fixedClockNode
  // Second is VectorMasterBus clock, it will drive T1.
  tlBusWrapperLocationMap(VectorMasterBus).clockGroupNode := viewpointBus.clockGroupNode

  lazy val clintOpt = None
  lazy val clintDomainOpt = None
  lazy val plicOpt = None
  lazy val plicDomainOpt = None
  lazy val debugOpt = None

  private def bitsetToAddressSet(bitset: BitSet): Seq[AddressSet] = bitset.terms.map((bp: BitPat) => AddressSet(bp.value, bp.mask ^ ((1 << bp.width) - 1))).toSeq.sorted
  val vectorMemoryNodes = t1Parameter.lsuParameters.banks.map(bank => TLManagerNode(Seq(TLSlavePortParameters.v1(
    Seq(TLSlaveParameters.v1(
      address            = bank.region.terms.map(bitsetToAddressSet).toSeq.flatten,
      resources          = Nil,
      regionType         = RegionType.UNCACHED,
      executable         = true,
      supportsGet        = TransferSizes(1, tlBusWrapperLocationMap(VectorMasterBus).blockBytes),
      supportsPutPartial = TransferSizes(1, tlBusWrapperLocationMap(VectorMasterBus).blockBytes),
      supportsPutFull    = TransferSizes(1, tlBusWrapperLocationMap(VectorMasterBus).blockBytes),
      supportsArithmetic = TransferSizes.none,
      supportsLogical    = TransferSizes.none,
      fifoId             = Some(0))), // requests are handled in order
    // align with datapath size
    beatBytes  = tlBusWrapperLocationMap(ScalarMasterBus).beatBytes,
    minLatency = 1
  ))))

  vectorMemoryNodes.foreach(vectorMemoryNode => tlBusWrapperLocationMap(VectorMasterBus).coupleTo("hbmVectorPort")(vectorMemoryNode := _))

  val scalarMemoryNode = TLManagerNode(Seq(TLSlavePortParameters.v1(
    Seq(TLSlaveParameters.v1(
      address            = bitsetToAddressSet(t1Parameter.lsuParameters.banks.map(_.region).reduce {(l: BitSet, r: BitSet) => l.union(r)}),
      resources          = Nil,
      regionType         = RegionType.UNCACHED,
      executable         = true,
      supportsGet        = TransferSizes(1, tlBusWrapperLocationMap(ScalarMasterBus).blockBytes),
      supportsPutPartial = TransferSizes(1, tlBusWrapperLocationMap(ScalarMasterBus).blockBytes),
      supportsPutFull    = TransferSizes(1, tlBusWrapperLocationMap(ScalarMasterBus).blockBytes),
      supportsArithmetic = TransferSizes.none,
      supportsLogical    = TransferSizes.none,
      fifoId             = Some(0))), // requests are handled in order
    // align with datapath size
    beatBytes  = tlBusWrapperLocationMap(ScalarMasterBus).beatBytes,
    minLatency = 1
  )))

  tlBusWrapperLocationMap(COH).coupleTo("ScalarTileLinkPort")(
    scalarMemoryNode
      := TLWidthWidget(tlBusWrapperLocationMap(COH).beatBytes)
      := _
  )

  val scalarPort = InModuleBody { scalarMemoryNode.makeIOs() }
  val vectorPorts = InModuleBody { vectorMemoryNodes.zipWithIndex.map{case (n, i ) => n.makeIOs()(ValName(s"vectorChannel$i")) } }
  val clocks = InModuleBody {
    // Only take the first clock for now, I'll purge the clock group.
    val elements: Seq[(String, ClockBundle)] = clockSource.out.flatMap(_._1.member.elements).take(1)
    val io = IO(Flipped(RecordMap(elements.map { case (name, data) => name -> data.cloneType }: _*)))
    elements.foreach { case (name, data) => io(name).foreach { data := _ } }
    io
  }
}

class T1SubsystemModuleImp[+L <: T1Subsystem](_outer: L)
    extends BareSubsystemModuleImp(_outer)
    with HasHierarchicalElementsRootContextModuleImp {
  lazy val outer = _outer
  // IntSyncCrossingSource requires implcit clock
  override def provideImplicitClockToLazyChildren: Boolean = true
}
