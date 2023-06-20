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

import sifive.blocks.inclusivecache._

class Peripheries extends Config((site, here, up) => {
  case SubsystemExternalResetVectorKey => false
  case BootROMLocated(InSubsystem) => Some(BootROMParams(contentFileName = "./dependencies/rocket-chip/bootrom/bootrom.img", hang = 0x10040))
})

class VectorLongConfig extends Config(
  new Peripheries ++
  new WithVectorLong ++
  new WithVector(1024) ++
  new WithNoSlavePort ++
  new WithNoMMIOPort ++
  new WithNBanks(2) ++
  new WithInclusiveCache ++
  new WithoutFPU ++
  new WithBitManip ++
  new WithCryptoNIST ++
  new WithCryptoSM ++
  new DefaultRV32Config
)
