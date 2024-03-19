// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.subsystem

import chisel3._
import chisel3.experimental.{SerializableModuleGenerator, UnlocatableSourceInfo}
import chisel3.util.experimental.BitSet
import chisel3.util.{BitPat, Counter}
import freechips.rocketchip.devices.debug.DebugModuleKey
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci.{ClockSourceNode, ClockSourceParameters, FixedClockBroadcast, NoResetCrossing}
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tile.{MaxHartIdBits, XLen}
import freechips.rocketchip.tilelink.{BroadcastFilter, HasTLBusParams, TLBusWrapper, TLBusWrapperConnection, TLBusWrapperInstantiationLike, TLBusWrapperTopology, TLEdge, TLFIFOFixer, TLFragmenter, TLInwardNode, TLManagerNode, TLOutwardNode, TLSlaveParameters, TLSlavePortParameters, TLWidthWidget, TLXbar}
import freechips.rocketchip.util.Location
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
    context: HasTileLinkLocations with HasPRCILocations with LazyModule,
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
    context: HasTileLinkLocations with HasPRCILocations with LazyModule,
    loc:     Location[TLBusWrapper]
  )(
    implicit p: Parameters
  ): TLBusWrapper = {
    val scalarControl = LazyModule(new TLBusWrapper(this, "ScalarControl") {
      private val xbar = TLXbar()
      private val fixer = LazyModule(new TLFIFOFixer(TLFIFOFixer.all)).node
      val node = xbar :*= fixer
      val inwardNode:  TLInwardNode = node
      val outwardNode: TLOutwardNode = node
      def busView:     TLEdge = fixer.edges.in.head

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
    context: HasTileLinkLocations with HasPRCILocations with LazyModule,
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
        case BroadcastKey                               => BroadcastParams(nTrackers = 4, bufferless = true, controlAddress = None, filterFactory = BroadcastFilter.factory)
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
                    nSets = 64,
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
          // at 12M
        case PLICKey       => Some(PLICParams(baseAddress = 0xc00000, maxPriorities = 3, intStages = 0, maxHarts = 1))
        case PLICAttachKey => PLICAttachParams(slaveWhere = ScalarControlBus)
        case CLINTKey =>
          // at 8M
          Some(
            CLINTParams(
              baseAddress = 0x800000,
              intStages = 0
            )
          )
        case CLINTAttachKey => CLINTAttachParams(slaveWhere = ScalarControlBus)
        case TLNetworkTopologyLocated(T1Subsystem) =>
          List(
            new TLBusWrapperTopology(
              instantiations = List(
                (ScalarMasterBus, ScalarMasterBusParameters(beatBytes = 8, blockBytes = 32)),
                (ScalarControlBus, ScalarControlBusParameters(beatBytes = 8, blockBytes = 32)),
                // beatBytes, blockBytes should be constraint to DLEN/VLEN:
                // beatBytes = DLEN, blockBytes = DLEN
                (
                  VectorMasterBus,
                  VectorMasterBusParameters(
                    beatBytes = t1Generator.parameter.datapathWidth / 8,
                    blockBytes = t1Generator.parameter.dLen / 8
                  )
                ),
                (
                  COH,
                  CoherenceManagerWrapperParams(blockBytes = site(CacheBlockBytes), beatBytes = 8, 1, COH.name)(
                    CoherenceManagerWrapper.broadcastManagerFn("broadcast", T1Subsystem, ScalarControlBus)
                  )
                )
              ),
              connections = Seq(
                (
                  ScalarMasterBus,
                  ScalarControlBus,
                  TLBusWrapperConnection.crossTo(
                    xType = NoCrossing,
                    driveClockFromMaster = Some(true),
                    nodeBinding = BIND_STAR,
                    flipRendering = false
                  )
                ),
                (
                  ScalarMasterBus,
                  COH,
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
        .orElse(new WithCacheBlockBytes(32))
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
  lazy val ibus = LazyModule(new InterruptBusWrapper)
  lazy val module = new T1SubsystemModuleImp(this)
  val t1Parameter: T1Parameter = p(org.chipsalliance.t1.subsystem.T1Generator).parameter

  override lazy val location:       HierarchicalLocation = T1Subsystem
  override lazy val busContextName: String = "t1subsystem"

  // 512M-1G
  val scalarMemoryRanges = BitSet(BitPat("b001?????????????????????????????"))
  require(
    t1Parameter.lsuParameters.banks.filter(_.accessScalar).map(_.region).reduce(_ union _).cover(scalarMemoryRanges),
    "Vector accessing scalar should include scalarMemory"
  )

  // 256M-512M
  val mmioRanges = BitSet(BitPat("b0001????????????????????????????"))

  // ClockDomains:
  val clockSource = ClockSourceNode(Seq(ClockSourceParameters()))

  val clockBroadcaster = FixedClockBroadcast() := clockSource

  // Three clocks need to special take care about: Interrupt, ScalarBus, VectorBus.
  ibus.clockNode :*= clockBroadcaster
  tlBusWrapperLocationMap(ScalarMasterBus).clockNode :*= clockBroadcaster
  tlBusWrapperLocationMap(VectorMasterBus).clockNode :*= clockBroadcaster

  lazy val debugOpt = None

  private def bitsetToAddressSet(bitset: BitSet): Seq[AddressSet] =
    bitset.terms.map((bp: BitPat) => AddressSet(bp.value, bp.mask ^ ((BigInt(1) << bp.width) - 1))).toSeq.sorted
  private def addressSetToBitSet(addresssSet: AddressSet, width: Int): BitSet = BitPat(
    "b" + Seq
      .tabulate(width)(idx =>
        if (addresssSet.mask.testBit(idx)) "?" else if (addresssSet.base.testBit(idx)) "1" else "0"
      )
      .mkString("")
  )

  val vectorMemoryNodes = t1Parameter.lsuParameters.banks.filter(!_.accessScalar).map(bank =>
    TLManagerNode(
      Seq(
        TLSlavePortParameters.v1(
          Seq(
            TLSlaveParameters.v1(
              address = bank.region.terms.map(bitsetToAddressSet).toSeq.flatten,
              resources = Nil,
              regionType = RegionType.UNCACHED,
              executable = true,
              supportsGet = TransferSizes(1, tlBusWrapperLocationMap(VectorMasterBus).blockBytes),
              supportsPutPartial = TransferSizes(1, tlBusWrapperLocationMap(VectorMasterBus).blockBytes),
              supportsPutFull = TransferSizes(1, tlBusWrapperLocationMap(VectorMasterBus).blockBytes),
              supportsArithmetic = TransferSizes.none,
              supportsLogical = TransferSizes.none,
              fifoId = Some(0)
            )
          ), // requests are handled in order
          // align with datapath size
          beatBytes = tlBusWrapperLocationMap(ScalarMasterBus).beatBytes,
          minLatency = 1
        )
      )
    )
  )

  vectorMemoryNodes.foreach(vectorMemoryNode =>
    tlBusWrapperLocationMap(VectorMasterBus).coupleTo("VectorPort")(vectorMemoryNode := _)
  )

  // T1 is an accelerator. it only have one scalar outwards bank
  val scalarMemoryNode = TLManagerNode(
    Seq(
      TLSlavePortParameters.v1(
        Seq(
          TLSlaveParameters.v1(
            // all vector bank that supports MMU should observe the scalar banks
            address = bitsetToAddressSet(scalarMemoryRanges),
            resources = Nil,
            regionType = RegionType.UNCACHED,
            executable = true,
            supportsGet = TransferSizes(1, tlBusWrapperLocationMap(ScalarMasterBus).blockBytes),
            supportsPutPartial = TransferSizes(1, tlBusWrapperLocationMap(ScalarMasterBus).blockBytes),
            supportsPutFull = TransferSizes(1, tlBusWrapperLocationMap(ScalarMasterBus).blockBytes),
            supportsArithmetic = TransferSizes.none,
            supportsLogical = TransferSizes.none,
            fifoId = Some(0)
          )
        ), // requests are handled in order
        // align with datapath size
        beatBytes = tlBusWrapperLocationMap(ScalarMasterBus).beatBytes,
        minLatency = 1
      )
    )
  )

  val scalarMemoryXBar = TLXbar()

  scalarMemoryNode :=* scalarMemoryXBar

  tlBusWrapperLocationMap(VectorMasterBus).coupleTo("VectorToScalarPort")(
    scalarMemoryXBar
      :=* TLWidthWidget(tlBusWrapperLocationMap(COH).beatBytes)
      := _
  )

  tlBusWrapperLocationMap(COH).coupleTo("ScalarPort")(
    scalarMemoryXBar
      :=* TLWidthWidget(tlBusWrapperLocationMap(COH).beatBytes)
      := _
  )

  val mmioNode = TLManagerNode(
    Seq(
      TLSlavePortParameters.v1(
        Seq(
          TLSlaveParameters.v1(bitsetToAddressSet(mmioRanges),
            resources = Nil,
            regionType = RegionType.GET_EFFECTS,
            executable = false,
            supportsGet = TransferSizes(1, viewpointBus.blockBytes),
            supportsPutFull = TransferSizes(1, viewpointBus.blockBytes),
            supportsPutPartial = TransferSizes(1, viewpointBus.blockBytes),
            fifoId = Some(0)
          )
        ),
        beatBytes = tlBusWrapperLocationMap(ScalarMasterBus).beatBytes,
        minLatency = 1
      )
    )
  )

  tlBusWrapperLocationMap(ScalarControlBus).coupleTo(s"ScalarMMIOPort") {
    mmioNode := _
  }

  // MMIOs
  lazy val (plicOpt, plicDomainOpt) = p(PLICKey).map { param =>
    val tlbus = locateTLBusWrapper(p(PLICAttachKey).slaveWhere)
    val plicDomainWrapper = tlbus.generateSynchronousDomain.suggestName("clint_domain")
    val plic = plicDomainWrapper { LazyModule(new TLPLIC(param, tlbus.beatBytes)) }
    plicDomainWrapper { plic.node := tlbus.coupleTo("plic") { TLFragmenter(tlbus) := _ } }
    plicDomainWrapper { plic.intnode :=* ibus.toPLIC }
    (plic, plicDomainWrapper)
  }.unzip

  lazy val (clintOpt, clintDomainOpt) = p(CLINTKey).map { params =>
    val tlbus = locateTLBusWrapper(p(CLINTAttachKey).slaveWhere)
    val clintDomainWrapper = tlbus.generateSynchronousDomain.suggestName("clint_domain")
    val clint = clintDomainWrapper { LazyModule(new CLINT(params, tlbus.beatBytes)) }
    clintDomainWrapper {
      clint.node := tlbus.coupleTo("clint") { TLFragmenter(tlbus) := _ }
      InModuleBody {
        // TODO: make tick configurable
        clint.module.io.rtcTick := Counter(true.B, 10000)._2
      }
    }
    (clint, clintDomainWrapper)
  }.unzip

  // IOs
  val scalarPort = InModuleBody { scalarMemoryNode.makeIOs() }
  val mmioPort = InModuleBody { mmioNode.makeIOs() }
  val vectorPorts = InModuleBody {
    vectorMemoryNodes.zipWithIndex.map { case (n, i) => n.makeIOs()(ValName(s"vectorChannel$i")) }
  }
  val clock = InModuleBody {
    val clockInput = clockSource.out.map(_._1).head
    val clock = IO(Input(Clock()))
    val reset = IO(Input(Bool()))
    clockInput.clock := clock
    clockInput.reset := reset
    clockInput
  }
}

class T1SubsystemModuleImp[+L <: T1Subsystem](_outer: L)
    extends BareSubsystemModuleImp(_outer)
    with HasHierarchicalElementsRootContextModuleImp {
  childClock := outer.clock.clock
  childReset := outer.clock.reset

  lazy val outer = _outer
  // IntSyncCrossingSource requires implcit clock
  override def provideImplicitClockToLazyChildren: Boolean = true
}
