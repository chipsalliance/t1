// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.subsystem

import chisel3._
import chisel3.util.experimental.BoringUtils.bore
import chisel3.experimental.SerializableModuleGenerator
import chisel3.properties.{ClassType, Path, Property}
import freechips.rocketchip.diplomacy.AddressSet
import freechips.rocketchip.subsystem.{BaseSubsystem, InstantiatesHierarchicalElements}
import org.chipsalliance.cde.config._
import org.chipsalliance.t1.rockettile.{AbstractLazyT1, AbstractLazyT1ModuleImp, T1LSUParameter}
import org.chipsalliance.t1.rtl.{T1, T1Parameter}
import org.chipsalliance.t1.rtl.lsu.LSUProbe

case object T1Generator extends Field[SerializableModuleGenerator[T1, T1Parameter]]

class LazyT1()(implicit p: Parameters) extends AbstractLazyT1 {
  lazy val module = new LazyT1Imp(this)
  lazy val generator: SerializableModuleGenerator[T1, T1Parameter] = p(T1Generator)
  def uarchName: String = "t1"
  def xLen: Int = generator.parameter.xLen
  def vlMax: Int = generator.parameter.vLen
  override def t1LSUParameters: T1LSUParameter =
    T1LSUParameter(
      name = generator.parameter.lsuParameters.name,
      banks = generator.parameter.lsuParameters.banks.map(bank => bitsetToAddressSet(bank.region)),
      sourceIdSize = generator.parameter.sourceWidth
    )
}

class LazyT1Imp(outer: LazyT1)(implicit p: Parameters) extends AbstractLazyT1ModuleImp(outer) {
  // We insist using the json config for Vector for uArch tuning.
  val t1: T1 = Module(outer.generator.module())

  // Since T1 doesn't split the interface and implementations into two packages.
  // we should consider doing this in the future. But now we just instantiate Xizhimen here.
  // this remind me that, for all Modules, being a FixedIOModule is really important.
  // that makes us being able to split def/impl easily.
  // In the future plan, we will pull Xizhimen up to RenMinGuangChang which will also include Monitor modules from Scalar.

  // Monitor
  val lsuProbe = probe.read(t1.lsuProbe).suggestName("lsuProbe")
  val laneProbes = t1.laneProbes.zipWithIndex.map{case (p, idx) =>
    val wire = Wire(p.cloneType)
    wire := probe.read(p)
    wire
  }
  val laneVrfProbes = t1.laneVrfProbes.zipWithIndex.map{case (p, idx) =>
    val wire = Wire(p.cloneType).suggestName(s"lane${idx}VrfProbe")
    wire := probe.read(p)
    wire
  }

  // TODO: gather XiZhiMen into a module, making XiZhiMen into an interface package.
  withClockAndReset(clock, reset)(Module(new Module {
    // h/t: GrandCentral
    override def desiredName: String = "XiZhiMen"
    val lsuProbeMonitor: LSUProbe = bore(lsuProbe)
    dontTouch(lsuProbeMonitor)
    val laneProbesMonitor = laneProbes.map(bore(_))
    laneProbesMonitor.foreach(dontTouch(_))
    val laneVrfProbesMonitor = laneVrfProbes.map(bore(_))
    laneVrfProbesMonitor.foreach(dontTouch(_))
  }))

  t1.request.valid := request.valid
  t1.request.bits.instruction := request.bits.instruction
  t1.request.bits.src1Data := request.bits.rs1Data
  t1.request.bits.src2Data := request.bits.rs2Data
  request.ready := t1.request.ready

  response.valid := t1.response.valid
  response.bits.data := t1.response.bits.data
  response.bits.rd := t1.response.bits.rd
  response.bits.float := t1.response.bits.float
  response.bits.vxsat := t1.response.bits.vxsat
  response.bits.mem := t1.response.bits.mem

  // hazardControl.loadTokenRelease := dut.response.bits.mem
  // hazardControl.storeTokenRelease := dut.response.bits.mem
  // TODO: decode LSU out
  // hazardControl.loadTokenGrant := !dut.response.bits.mem
  // hazardControl.storeTokenGrant := !dut.response.bits.mem

  t1.csrInterface.elements.foreach{ case (s, d) => d := csr.elements.getOrElse(s, 0.U)}
  // TODO: fixme
  t1.storeBufferClear := true.B

  // TODO: multiple LSU support
  outer.t1LSUNode.out.zipWithIndex.foreach {
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

  om := t1.om
}
