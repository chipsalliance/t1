package verdes

import freechips.rocketchip.devices.debug.HasPeripheryDebug
import freechips.rocketchip.devices.tilelink.{BootROM, BootROMLocated, MaskROM, MaskROMLocated}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util.DontTouch
import org.chipsalliance.cde.config._

class VerdesConfig
  extends Config(
    new Config((site, here, up) => {
      case ExtMem => Some(MemoryPortParams(MasterPortParams(
        base = BigInt("20000000", 16),
        size = BigInt("20000000", 16),
        beatBytes = site(MemoryBusKey).beatBytes,
        idBits = 4), 1))
    })
      .orElse(new WithBootROMFile("./dependencies/rocket-chip/bootrom/bootrom.img"))
      .orElse(new WithClockGateModel("./dependencies/rocket-chip/src/vsrc/EICG_wrapper.v"))
      .orElse(new WithNoSimulationTimeout)
      .orElse(new WithScratchpadsBaseAddress(BigInt("3000000", 16)))
      .orElse(new WithCacheBlockBytes(16))
      .orElse(new WithInclusiveCache())
      .orElse(new WithRV32)
      .orElse(new WithoutFPU)
      .orElse(new WithBitManip)
      .orElse(new WithCryptoNIST)
      .orElse(new WithCryptoSM)
      .orElse(new WithBitManipCrypto)
      .orElse(new With1TinyCore)
      // SoC
      .orElse(new WithoutTLMonitors)
      .orElse(new WithDefaultMemPort)
      .orElse(new WithDebugSBA)
      // 1 MHz
      .orElse(new WithTimebase(BigInt(1000000)))
      .orElse(new WithDTS("sequencer,verdes", Nil))
      .orElse(new WithCoherentBusTopology)
      .orElse(new BaseSubsystemConfig)
  )

class VerdesSystem(implicit p: Parameters) extends BaseSubsystem
  with HasRocketTiles
  with HasPeripheryDebug
  with CanHaveMasterAXI4MemPort {
  p(BootROMLocated(location)).foreach(BootROM.attach(_, this, CBUS))
  p(MaskROMLocated(location)).foreach(MaskROM.attach(_, this, CBUS))
  override lazy val module = new VerdesSystemModuleImp(this)
}

class VerdesSystemModuleImp[+L <: VerdesSystem](_outer: L) extends BaseSubsystemModuleImp(_outer)
  with HasTilesModuleImp
  with HasRTCModuleImp
  with DontTouch