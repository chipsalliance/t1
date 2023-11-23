package verdes

import chisel3._
import chisel3.experimental.UnlocatableSourceInfo
import freechips.rocketchip.devices.debug.HasPeripheryDebug
import freechips.rocketchip.diplomacy.{BundleBridgeSource, InModuleBody, LazyModule, SynchronousCrossing, ValName}
import freechips.rocketchip.rocket.{DCacheParams, ICacheParams, MulDivParams, RocketCoreParams}
import freechips.rocketchip.subsystem.{BaseSubsystem, BaseSubsystemConfig, BaseSubsystemModuleImp, CacheBlockBytes, CanHaveMasterAXI4MemPort, ExtMem, HasRTCModuleImp, HasTilesModuleImp, InSubsystem, MasterPortParams, MemoryBusKey, MemoryPortParams, RocketCrossingParams, SystemBusKey, TileMasterPortParams, TilesLocated, WithCacheBlockBytes, WithClockGateModel, WithCoherentBusTopology, WithDTS, WithDebugSBA, WithNoSimulationTimeout, WithScratchpadsBaseAddress, WithTimebase, WithoutTLMonitors}
import org.chipsalliance.t1.rockettile.BuildVector
import freechips.rocketchip.tile.XLen
import freechips.rocketchip.util.DontTouch
import org.chipsalliance.cde.config._
import org.chipsalliance.t1.rocketcore.{RocketTileAttachParams, RocketTileParams}

class VerdesConfig
  extends Config(
    new Config((site, here, up) => {
      case ExtMem => Some(MemoryPortParams(MasterPortParams(
        base = BigInt("20000000", 16),
        size = BigInt("20000000", 16),
        beatBytes = site(MemoryBusKey).beatBytes,
        idBits = 4), 1))
      case BuildVector => Some((p: Parameters) => LazyModule(new LazyT1()(p))(ValName("T1"), UnlocatableSourceInfo))
      case XLen => 32
      case TilesLocated(InSubsystem) =>
        val tiny = RocketTileParams(
          core = new RocketCoreParams(
            haveSimTimeout = false,
            useVM = false,
            fpu = None,
            mulDiv = Some(MulDivParams(mulUnroll = 8))) {
            // hot fix
            override val useVector = true
            override def vLen = 1024
          },
          btb = None,
          dcache = Some(DCacheParams(
            rowBits = site(SystemBusKey).beatBits,
            nSets = 256, // 16Kb scratchpad
            nWays = 1,
            nTLBSets = 1,
            nTLBWays = 4,
            nMSHRs = 0,
            blockBytes = site(CacheBlockBytes),
            scratch = Some(0x80000000L))),
          icache = Some(ICacheParams(
            rowBits = site(SystemBusKey).beatBits,
            nSets = 64,
            nWays = 1,
            nTLBSets = 1,
            nTLBWays = 4,
            blockBytes = site(CacheBlockBytes)))
        )
        List(RocketTileAttachParams(
          tiny,
          RocketCrossingParams(
            crossingType = SynchronousCrossing(),
            master = TileMasterPortParams())
        ))
    })
      .orElse(new WithClockGateModel("./dependencies/rocket-chip/src/vsrc/EICG_wrapper.v"))
      .orElse(new WithNoSimulationTimeout)
      .orElse(new WithScratchpadsBaseAddress(BigInt("3000000", 16)))
      .orElse(new WithCacheBlockBytes(16))
      // SoC
      .orElse(new WithoutTLMonitors)
      .orElse(new WithDebugSBA)
      // 1 MHz
      .orElse(new WithTimebase(BigInt(1000000)))
      .orElse(new WithDTS("sequencer,verdes", Nil))
      .orElse(new WithCoherentBusTopology)
      .orElse(new BaseSubsystemConfig)
  )

class VerdesSystem(implicit p: Parameters) extends BaseSubsystem
  with HasT1Tiles
  with HasPeripheryDebug
  with CanHaveMasterAXI4MemPort {
  // configure
  val resetVectorSourceNode = BundleBridgeSource[UInt]()
  tileResetVectorNexusNode := resetVectorSourceNode
  val resetVector = InModuleBody(resetVectorSourceNode.makeIO())
  override lazy val module = new VerdesSystemModuleImp(this)
}

class VerdesSystemModuleImp[+L <: VerdesSystem](_outer: L) extends BaseSubsystemModuleImp(_outer)
  with HasTilesModuleImp
  with HasRTCModuleImp
  with DontTouch