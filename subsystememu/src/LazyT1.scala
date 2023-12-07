package verdes

import chisel3._
import chisel3.experimental.SerializableModuleGenerator
import freechips.rocketchip.diplomacy.AddressSet
import freechips.rocketchip.subsystem.{BaseSubsystem, HasTiles}
import freechips.rocketchip.util.CoreMonitorBundle
import org.chipsalliance.t1.rockettile.{AbstractLazyT1, AbstractLazyT1ModuleImp}
import org.chipsalliance.cde.config._
import v.{V, VParameter}

case object T1ConfigPath extends Field[os.Path]
trait HasT1Tiles extends HasTiles { this: BaseSubsystem =>
  val t1Tiles = tiles.collect { case r: org.chipsalliance.t1.rocketcore.RocketTile => r }
  def coreMonitorBundles = List.empty[CoreMonitorBundle]
}

class LazyT1()(implicit p: Parameters) extends AbstractLazyT1 {
  lazy val module = new LazyT1Imp(this)
  lazy val generator: SerializableModuleGenerator[V, VParameter] = upickle.default.read[SerializableModuleGenerator[V, VParameter]](ujson.read(os.read(p(T1ConfigPath))))
  def banks: Int = generator.parameter.memoryBankSize
  def uarchName: String = "t1"
  def sourceIdSize: Int = generator.parameter.sourceWidth
  // FIXME: the giant XBar here.
  def banksMapping(bank: Int): AddressSet = AddressSet.everything
  def xLen: Int = generator.parameter.xLen
  def vLen: Int = generator.parameter.vLen
}

class LazyT1Imp(outer: LazyT1)(implicit p: Parameters) extends AbstractLazyT1ModuleImp(outer) {
  // We insist using the json config for Vector for uArch tuning.
  val t1: V = Module(outer.generator.module())

  t1.request.valid := request.valid
  t1.request.bits.instruction := request.bits.instruction
  t1.request.bits.src1Data := request.bits.rs1Data
  t1.request.bits.src2Data := request.bits.rs2Data
  request.ready := t1.request.ready

  response.valid := t1.response.valid
  response.bits.data := t1.response.bits.data
  response.bits.rd := t1.response.bits.rd
  response.bits.vxsat := t1.response.bits.vxsat

  // hazardControl.loadTokenRelease := dut.response.bits.mem
  // hazardControl.storeTokenRelease := dut.response.bits.mem
  // TODO: decode LSU out
  // hazardControl.loadTokenGrant := !dut.response.bits.mem
  // hazardControl.storeTokenGrant := !dut.response.bits.mem

  t1.csrInterface.elements.foreach{ case (s, d) => d := csr.elements.getOrElse(s, 0.U)}
  // TODO: fixme
  t1.storeBufferClear := true.B

  outer.vectorMasterNode.out.zipWithIndex.foreach {
    case ((bundle, _), i) =>
      bundle.a.bits.opcode := t1.memoryPorts(i).a.bits.opcode
      bundle.a.bits.param := t1.memoryPorts(i).a.bits.param
      bundle.a.bits.size := t1.memoryPorts(i).a.bits.size
      bundle.a.bits.source := t1.memoryPorts(i).a.bits.source
      bundle.a.bits.address := t1.memoryPorts(i).a.bits.address
      bundle.a.bits.mask := t1.memoryPorts(i).a.bits.mask
      bundle.a.bits.data := t1.memoryPorts(i).a.bits.data
      bundle.a.bits.corrupt := t1.memoryPorts(i).a.bits.corrupt
      bundle.a.valid := t1.memoryPorts(i).a.valid
      t1.memoryPorts(i).a.ready := bundle.a.ready
      t1.memoryPorts(i).d.bits.opcode := bundle.d.bits.opcode
      t1.memoryPorts(i).d.bits.param := bundle.d.bits.param
      t1.memoryPorts(i).d.bits.size := bundle.d.bits.size
      t1.memoryPorts(i).d.bits.source := bundle.d.bits.source
      t1.memoryPorts(i).d.bits.sink := bundle.d.bits.sink
      t1.memoryPorts(i).d.bits.denied := bundle.d.bits.denied
      t1.memoryPorts(i).d.bits.data := bundle.d.bits.data
      t1.memoryPorts(i).d.bits.corrupt := bundle.d.bits.corrupt
      t1.memoryPorts(i).d.valid := bundle.d.valid
      bundle.d.ready := t1.memoryPorts(i).d.ready

  }
}