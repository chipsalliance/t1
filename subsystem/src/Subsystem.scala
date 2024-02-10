// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.subsystem

import chisel3._
import chisel3.experimental.UnlocatableSourceInfo
import freechips.rocketchip.devices.debug.DebugModuleKey
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket.{DCacheParams, ICacheParams, MulDivParams, RocketCoreParams}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink._
import org.chipsalliance.t1.rockettile.BuildVector
import freechips.rocketchip.tile.XLen
import freechips.rocketchip.util.DontTouch
import org.chipsalliance.cde.config._
import org.chipsalliance.t1.rocketcore.{RocketTileAttachParams, RocketTileParams}
import freechips.rocketchip.interrupts.NullIntSyncSource

class T1SubsystemConfig
  extends Config(
    new Config((site, here, up) => {
      case SystemBusKey => SystemBusParams(
        beatBytes = site(XLen)/8,
        blockBytes = 32)
      case ExtMem => Some(MemoryPortParams(MasterPortParams(
        base = BigInt("0", 16),
        size = BigInt("80000000", 16),
        beatBytes = site(MemoryBusKey).beatBytes,
        idBits = 4), 1))
      case ExtBus => Some(MasterPortParams(
        base = x"9000_0000",
        size = x"1000_0000",
        beatBytes = site(MemoryBusKey).beatBytes,
        idBits = 4))
      case BuildVector => Some((p: Parameters) => LazyModule(new LazyT1()(p))(ValName("T1"), UnlocatableSourceInfo))
      case XLen => 32
      case ControlBusKey => PeripheryBusParams(
        beatBytes = site(XLen)/8,
        blockBytes = site(CacheBlockBytes),
        errorDevice = Some(BuiltInErrorDeviceParams(
          errorParams = DevNullParams(List(AddressSet(BigInt("80003000", 16), BigInt("fff", 16))), maxAtomic=site(XLen)/8, maxTransfer=4096))))
      case CLINTKey => Some(CLINTParams(BigInt("82000000", 16)))
      case PLICKey => Some(PLICParams(BigInt("8C000000", 16)))
      case DebugModuleKey => None
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
      .orElse(new WithCacheBlockBytes(16))
      // SoC
      .orElse(new WithoutTLMonitors)
      .orElse(new WithNExtTopInterrupts(1))
      // 1 MHz
      .orElse(new WithTimebase(BigInt(1000000)))
      .orElse(new WithDTS("chipsalliance,t1", Nil))
      .orElse(new WithIncoherentBusTopology)
      .orElse(new BaseSubsystemConfig)
  )

class T1SubsystemSystem(implicit p: Parameters) extends BaseSubsystem
  with HasT1Tiles
  with CanHaveMasterAXI4MemPort
  with CanHaveMasterAXI4MMIOPort
  with HasAsyncExtInterrupts {
  // configure
  val resetVectorSourceNode = BundleBridgeSource[UInt]()
  tileResetVectorNexusNode := resetVectorSourceNode
  val resetVector = InModuleBody(resetVectorSourceNode.makeIO())
  override lazy val debugNode = NullIntSyncSource()
  override lazy val module = new T1SubsystemModuleImp(this)
}

class T1SubsystemModuleImp[+L <: T1SubsystemSystem](_outer: L) extends BaseSubsystemModuleImp(_outer)
  with HasTilesModuleImp
  with HasRTCModuleImp
  with HasExtInterruptsModuleImp
  with DontTouch