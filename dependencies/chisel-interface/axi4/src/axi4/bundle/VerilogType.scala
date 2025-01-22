// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.amba.axi4.bundle

import chisel3.{Bool, Bundle, Clock, Flipped, UInt, fromIntToWidth}

// type-erased AXI4 interface based on IHI0022H
// view from master to slave

/** See IHI0022H A2.1. TODO: this isn't typically useful until we have
  * asynchronous support.
  */
trait Global extends Bundle {

  /** Global clock signal. Synchronous signals are sampled on the rising edge of
    * the global clock.
    */
  val ACLK: Clock = Clock()

  /** Global reset signal. This signal is active-LOW. */
  val ARESETn: Bool = Bool()
}

trait AXI4VerilogBundle extends AXI4Bundle

/** See IHI0022H A2.2 */
trait AWChannel extends AXI4VerilogBundle {

  /** Identification tag for a write transaction. See IHI0022H A5.2 */
  val AWID: UInt = UInt(idWidth.W)

  /** The address of the first transfer in a write transaction. See IHI0022H
    * A3.4.1
    */
  val AWADDR: UInt = UInt(addrWidth.W)

  /** Length, the exact number of data transfers in a write transaction. This
    * information determines the number of data transfers associated with the
    * address. See IHI0022H A3.4.1
    */
  val AWLEN: UInt = UInt(8.W)

  /** Size, the number of bytes in each data transfer in a write transaction.
    * See IHI0022H A3.4.1
    */
  val AWSIZE: UInt = UInt(3.W)

  /** Burst type, indicates how address changes between each transfer in a write
    * transaction.
    */
  val AWBURST: UInt = UInt(2.W)

  /** Provides information about the atomic characteristics of a write
    * transaction.
    */
  val AWLOCK: Bool = Bool()

  /** Indicates how a write transaction is required to progress through a
    * system.
    */
  val AWCACHE: UInt = UInt(4.W)

  /** Protection attributes of a write transaction: privilege, security level,
    * and access type.
    */
  val AWPROT: UInt = UInt(3.W)

  /** Quality of Service identifier for a write transaction. */
  val AWQOS: UInt = UInt(4.W)

  /** Region indicator for a write transaction. */
  val AWREGION: UInt = UInt(4.W)

  /** User-defined extension for the write address channel. */
  val AWUSER: UInt = UInt(awUserWidth.W)
}

trait AWFlowControl extends AXI4VerilogBundle {

  /** Indicates that the write address channel signals are valid. */
  val AWVALID: Bool = Bool()

  /** Indicates that a transfer on the write address channel can be accepted. */
  val AWREADY: Bool = Flipped(Bool())
}

/** See IHI0022H A2.3 */
trait WChannel extends AXI4VerilogBundle {

  /** Write Data */
  val WDATA: UInt = UInt(dataWidth.W)

  /** Write strobes, indicate which byte lanes hold valid data. */
  val WSTRB: UInt = UInt((dataWidth / 8).W)

  /** Indicates whether this is the last data transfer in a write transaction.
    */
  val WLAST: Bool = Bool()

  /** User-defined extension for the write data channel. */
  val WUSER: UInt = UInt(wUserWidth.W)
}

trait WFlowControl extends AXI4VerilogBundle {

  /** Indicates that the write data channel signals are valid. */
  val WVALID: Bool = Bool()

  /** Indicates that a transfer on the write data channel can be accepted. */
  val WREADY: Bool = Flipped(Bool())
}

/** See IHI0022H A2.4 */
trait BChannel extends AXI4VerilogBundle {

  /** Identification tag for a write response. */
  val BID: UInt = Flipped(UInt(idWidth.W))

  /** Write response, indicates the status of a write transaction. */
  val BRESP: UInt = Flipped(UInt(2.W))

  /** User-defined extension for the write response channel. */
  val BUSER: UInt = Flipped(UInt(bUserWidth.W))
}

trait BFlowControl extends AXI4VerilogBundle {

  /** Indicates that the write response channel signals are valid. */
  val BVALID: Bool = Flipped(Bool())

  /** Indicates that a transfer on the write response channel can be accepted.
    */
  val BREADY: Bool = Bool()
}

/** See IHI0022H A2.5 */
trait ARChannel extends AXI4VerilogBundle {

  /** width of [[ARUSER]] */
  /** Identification tag for a read transaction. */
  val ARID: UInt = UInt(idWidth.W)

  /** The address of the first transfer in a read transaction. */
  val ARADDR: UInt = UInt(addrWidth.W)

  /** Length, the exact number of data transfers in a read transaction. */
  val ARLEN: UInt = UInt(8.W)

  /** Size, the number of bytes in each data transfer in a read transaction. */
  val ARSIZE: UInt = UInt(3.W)

  /** Burst type, indicates how address changes between each transfer in a read
    * transaction.
    */
  val ARBURST: UInt = UInt(2.W)

  /** Provides information about the atomic characteristics of a read
    * transaction.
    */
  val ARLOCK: Bool = Bool()

  /** Indicates how a read transaction is required to progress through a system.
    */
  val ARCACHE: UInt = UInt(4.W)

  /** Quality of Service identifier for a read transaction. */
  val ARPROT: UInt = UInt(3.W)

  /** Quality of Service identifier for a read transaction. */
  val ARQOS: UInt = UInt(4.W)

  /** Region indicator for a read transaction. */
  val ARREGION: UInt = UInt(4.W)

  /** User-defined extension for the read address channel. */
  val ARUSER: UInt = UInt(arUserWidth.W)
}

trait ARFlowControl extends AXI4VerilogBundle {

  /** Indicates that the read address channel signals are valid. */
  val ARVALID: Bool = Bool()

  /** Indicates that a transfer on the read address channel can be accepted. */
  val ARREADY: Bool = Flipped(Bool())
}

/** See IHI0022H A2.6 */
trait RChannel extends AXI4VerilogBundle {

  /** Identification tag for read data and response. */
  val RID: UInt = Flipped(UInt(idWidth.W))

  /** Read data. */
  val RDATA: UInt = Flipped(UInt(dataWidth.W))

  /** Read response, indicates the status of a read transfer. */
  val RRESP: UInt = Flipped(UInt(2.W))

  /** Indicates whether this is the last data transfer in a read transaction. */
  val RLAST: Bool = Flipped(Bool())

  /** User-defined extension for the read data channel. */
  val RUSER: UInt = Flipped(UInt(rUserWidth.W))
}

trait RFlowControl extends AXI4VerilogBundle {

  /** Indicates that the read data channel signals are valid. */
  val RVALID: Bool = Flipped(Bool())

  /** Indicates that a transfer on the read data channel can be accepted. */
  val RREADY: Bool = Bool()
}

// Raw Type definition for AXI4.
/** A9.2.1 Read/write interface */
class AXI4RWIrrevocableVerilog(val parameter: AXI4BundleParameter)
    extends AXI4VerilogBundle
    with AWChannel
    with AWFlowControl
    with WChannel
    with WFlowControl
    with BChannel
    with BFlowControl
    with ARChannel
    with ARFlowControl
    with RChannel
    with RFlowControl

object AXI4RWIrrevocableVerilog {
  def apply(parameter: AXI4BundleParameter) = new AXI4ROIrrevocableVerilog(
    parameter
  )
  implicit val viewChisel: chisel3.experimental.dataview.DataView[
    AXI4RWIrrevocableVerilog,
    AXI4RWIrrevocable
  ] = rwV2C
}

/** A9.2.2 Read-only interface */
class AXI4ROIrrevocableVerilog(val parameter: AXI4BundleParameter)
    extends AXI4VerilogBundle
    with ARChannel
    with ARFlowControl
    with RChannel
    with RFlowControl

object AXI4ROIrrevocableVerilog {
  def apply(parameter: AXI4BundleParameter) = new AXI4ROIrrevocableVerilog(
    parameter
  )
  implicit val viewChisel: chisel3.experimental.dataview.DataView[
    AXI4RWIrrevocableVerilog,
    AXI4RWIrrevocable
  ] = rwV2C
}

/** A9.2.3 Write-only interface */
class AXI4WOIrrevocableVerilog(val parameter: AXI4BundleParameter)
    extends AXI4VerilogBundle
    with AWChannel
    with AWFlowControl
    with WChannel
    with WFlowControl
    with BChannel
    with BFlowControl

object AXI4WOIrrevocableVerilog {
  def apply(parameter: AXI4BundleParameter) = new AXI4RWIrrevocableVerilog(
    parameter
  )
  implicit val viewChisel: chisel3.experimental.dataview.DataView[
    AXI4RWIrrevocableVerilog,
    AXI4RWIrrevocable
  ] = rwV2C
}
