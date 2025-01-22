// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.amba.axi4.bundle

import chisel3.util.{Irrevocable, IrrevocableIO}
import chisel3.{Bool, Flipped, UInt, fromIntToWidth}

trait AXI4ChiselBundle extends AXI4Bundle

class AW(val parameter: AXI4BundleParameter) extends AXI4ChiselBundle {
  val id: UInt = UInt(idWidth.W)
  val addr: UInt = UInt(addrWidth.W)
  val len: UInt = UInt(8.W)
  val size: UInt = UInt(3.W)
  val burst: UInt = UInt(2.W)
  val lock: Bool = Bool()
  val cache: UInt = UInt(4.W)
  // @todo: should prot expose as three different field?
  val prot: UInt = UInt(3.W)
  val qos: UInt = UInt(4.W)
  val region: UInt = UInt(4.W)
  val user: UInt = UInt(awUserWidth.W)
}

class W(val parameter: AXI4BundleParameter) extends AXI4ChiselBundle {
  val data: UInt = UInt(dataWidth.W)
  val strb: UInt = UInt((dataWidth / 8).W)
  val last: Bool = Bool()
  val user: UInt = UInt(wUserWidth.W)
}

class B(val parameter: AXI4BundleParameter) extends AXI4ChiselBundle {
  val id: UInt = UInt(idWidth.W)
  val resp: UInt = UInt(2.W)
  val user: UInt = UInt(bUserWidth.W)
}

class AR(val parameter: AXI4BundleParameter) extends AXI4ChiselBundle {
  val id: UInt = UInt(idWidth.W)
  val addr: UInt = UInt(addrWidth.W)
  val len: UInt = UInt(8.W)
  val size: UInt = UInt(3.W)
  val burst: UInt = UInt(2.W)
  val lock: Bool = Bool()
  val cache: UInt = UInt(4.W)
  // @todo: should prot expose as three different field?
  val prot: UInt = UInt(3.W)
  val qos: UInt = UInt(4.W)
  val region: UInt = UInt(4.W)
  val user: UInt = UInt(arUserWidth.W)
}

class R(val parameter: AXI4BundleParameter) extends AXI4ChiselBundle {
  val id: UInt = UInt(idWidth.W)
  val data: UInt = UInt(dataWidth.W)
  val resp: UInt = UInt(2.W)
  val last: Bool = Bool()
  val user: UInt = UInt(rUserWidth.W)
}

trait HasAW extends AXI4ChiselBundle {
  val aw: IrrevocableIO[AW] = Irrevocable(new AW(parameter))
}

trait HasW extends AXI4ChiselBundle {
  val w: IrrevocableIO[W] = Irrevocable(new W(parameter))
}

trait HasB extends AXI4ChiselBundle {
  val b: IrrevocableIO[B] = Flipped(Irrevocable(new B(parameter)))
}

trait HasAR extends AXI4ChiselBundle {
  val ar: IrrevocableIO[AR] = Irrevocable(new AR(parameter))
}

trait HasR extends AXI4ChiselBundle {
  val r: IrrevocableIO[R] = Flipped(Irrevocable(new R(parameter)))
}

// The standard AXI Bundle expressed in Chisel type system.
/** A9.2.1 Read/write interface */
class AXI4RWIrrevocable(val parameter: AXI4BundleParameter)
    extends AXI4ChiselBundle
    with HasAW
    with HasW
    with HasB
    with HasAR
    with HasR

object AXI4RWIrrevocable {
  def apply(parameter: AXI4BundleParameter) = new AXI4RWIrrevocable(parameter)
  implicit val viewVerilog: chisel3.experimental.dataview.DataView[
    AXI4RWIrrevocable,
    AXI4RWIrrevocableVerilog
  ] = rwC2V
}

/** A9.2.2 Read-only interface */
class AXI4ROIrrevocable(val parameter: AXI4BundleParameter)
    extends AXI4ChiselBundle
    with HasAR
    with HasR

object AXI4ROIrrevocable {
  def apply(parameter: AXI4BundleParameter) = new AXI4ROIrrevocable(parameter)
  implicit val viewVerilog: chisel3.experimental.dataview.DataView[
    AXI4ROIrrevocable,
    AXI4ROIrrevocableVerilog
  ] = roC2V
}

/** A9.2.3 Write-only interface */
class AXI4WOIrrevocable(val parameter: AXI4BundleParameter)
    extends AXI4ChiselBundle
    with HasAW
    with HasW
    with HasB

object AXI4WOIrrevocable {
  def apply(parameter: AXI4BundleParameter) = new AXI4WOIrrevocable(parameter)
  implicit val viewVerilog: chisel3.experimental.dataview.DataView[
    AXI4WOIrrevocable,
    AXI4WOIrrevocableVerilog
  ] = woC2V
}
